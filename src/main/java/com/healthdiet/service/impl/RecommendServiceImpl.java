package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.Recommendation;
import com.healthdiet.entity.User;
import com.healthdiet.entity.vo.RecommendVO;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.mapper.RecommendMapper;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.service.ConfigService;
import com.healthdiet.service.IRecommendService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl extends ServiceImpl<RecommendMapper, Recommendation> implements IRecommendService {

    @Autowired private UserMapper userMapper;
    @Autowired private DietRecordMapper dietRecordMapper;
    @Autowired private FoodMapper foodMapper;
    @Autowired private ConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    // 缓存所有食物数据，避免频繁查库 (实际生产中应配合 Redis)
    private List<Food> allFoodsCache = new ArrayList<>();

    // 静态文案库
    private static final List<String> HEALTH_TIPS = Arrays.asList(
            "吃饭细嚼慢咽，尝试每口咀嚼20次以上。",
            "避免边看手机边吃饭，专注饮食能增加饱腹感。",
            "每天保证 7-8 小时睡眠，睡眠不足会增加食欲。",
            "下午 3 点是吃水果的最佳时间。",
            "睡前 3 小时尽量不要进食，减轻肠胃负担。",
            "减少加工食品摄入，多吃天然原型的食物。",
            "饭后靠墙站立15分钟，有助于消化。"
    );

    @Override
    public Recommendation getTodayRecommend(Long userId) {
        // 0. 刷新食物缓存 (简单实现：每次调用前刷一下，或者用 @PostConstruct 初始化)
        refreshFoodCache();

        LocalDate today = LocalDate.now();

        // 1. 缓存检查
        QueryWrapper<Recommendation> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", today);
        Recommendation exist = this.getOne(query);
        if (exist != null) return exist;

        // 2. 准备数据
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if (user.getHeight() == null || user.getWeight() == null) {
            throw new RuntimeException("请先在个人中心完善身高和体重！");
        }

        // 3. 核心生成逻辑
        RecommendVO vo = generateSmartPlan(user, today);

        // 4. 保存
        Recommendation rec = new Recommendation();
        rec.setUserId(userId);
        rec.setDate(today);
        try {
            rec.setResultJson(objectMapper.writeValueAsString(vo));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成推荐失败：JSON转换错误");
        }

        this.save(rec);
        return rec;
    }

    private void refreshFoodCache() {
        if (allFoodsCache.isEmpty()) {
            allFoodsCache = foodMapper.selectList(null);
        }
    }

    /**
     * 智能生成逻辑
     */
    private RecommendVO generateSmartPlan(User user, LocalDate date) {
        RecommendVO vo = new RecommendVO();
        vo.setDate(date.toString());

        // --- Step 1: 计算基础 TDEE ---
        double bmr = calculateBMR(user);
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3); // 默认系数提高到1.3更符合现代人
        double tdee = bmr * activityFactor;

        // --- Step 2: 7天历史回溯 ---
        double historyAvgCal = analyzeHistoryIntake(user.getId(), date.minusDays(7), date.minusDays(1));

        int adjustment = 0;
        boolean isPunishmentMode = false; // 是否处于"惩罚/回调"模式
        boolean isRecoveryMode = false;   // 是否处于"恢复"模式

        if (historyAvgCal > 0) {
            if (historyAvgCal > tdee + 500) {
                adjustment = -300; // 昨天吃太多，今天扣300
                isPunishmentMode = true;
            } else if (historyAvgCal < tdee - 500) {
                adjustment = +200; // 昨天饿到了，今天补200
                isRecoveryMode = true;
            }
        }

        // --- Step 3: 确定今日目标 ---
        int targetCal = (int) tdee + adjustment;
        Integer goalType = user.getTarget() != null ? user.getTarget() : 0;

        // 目标调整
        if (goalType == -1) targetCal -= 300; // 减脂
        else if (goalType == 1) targetCal += 300; // 增肌

        // 安全底线
        if (targetCal < bmr) targetCal = (int) bmr;

        // --- Step 4: 填充 Summary ---
        RecommendVO.Summary summary = new RecommendVO.Summary();
        summary.setBmi(calculateBMI(user));
        summary.setStatus(getBMIStatus(summary.getBmi()));
        summary.setCaloriesTarget(targetCal);
        summary.setGoal(goalType == -1 ? "lose_fat" : (goalType == 1 ? "gain_muscle" : "maintain"));

        // 这里的文案逻辑修复矛盾问题
        if (isPunishmentMode) {
            summary.setKeyMessage("⚠️ 监测到近期热量严重超标，今日方案已启动【热量管控模式】，请务必管住嘴。");
        } else if (isRecoveryMode) {
            summary.setKeyMessage("❤️ 监测到近期摄入不足，今日方案增加了能量配给，请按时吃饭恢复代谢。");
        } else {
            // 没有特殊情况，按目标显示
            if (goalType == -1) summary.setKeyMessage("今日目标：制造热量缺口，保持低脂饮食。");
            else if (goalType == 1) summary.setKeyMessage("今日目标：保证碳水和蛋白摄入，为肌肉供能。");
            else summary.setKeyMessage("今日目标：营养均衡，维持健康体重。");
        }
        vo.setSummary(summary);

        // --- Step 5: 基于真实食物库生成三餐 ---
        List<RecommendVO.Meal> meals = new ArrayList<>();
        // 早餐 30%, 午餐 40%, 晚餐 30%
        meals.add(composeRealMeal("breakfast", "早餐", (int)(targetCal * 0.3), goalType));
        meals.add(composeRealMeal("lunch", "午餐", (int)(targetCal * 0.4), goalType));
        meals.add(composeRealMeal("dinner", "晚餐", (int)(targetCal * 0.3), goalType));
        vo.setMeals(meals);

        // --- Step 6: 每日汇总 ---
        RecommendVO.DailySummary ds = new RecommendVO.DailySummary();
        ds.setTotalCalories(targetCal);

        // 根据餐单反推宏量 (简单模拟)
        double pRatio = goalType == -1 ? 0.35 : 0.25;
        ds.setTotalMacros(new RecommendVO.Macros(
                targetCal * pRatio / 4, targetCal * 0.25 / 9, targetCal * (1-pRatio-0.25) / 4
        ));
        ds.setPfcRatio(new RecommendVO.Macros(pRatio, 0.25, 1-pRatio-0.25));

        // 修复矛盾的总结文案
        if (isPunishmentMode) {
            ds.setSummaryText("今日重点：【控卡减负】。因近期摄入过多，今日菜单以低热量、高纤维食物为主。");
        } else if (isRecoveryMode) {
            ds.setSummaryText("今日重点：【代谢恢复】。增加了优质碳水和蛋白质，帮助身体走出饥荒模式。");
        } else {
            String focus = goalType == -1 ? "高效燃脂" : (goalType == 1 ? "肌肉合成" : "营养均衡");
            ds.setSummaryText("今日重点：【" + focus + "】。请严格按照推荐食谱执行。");
        }
        vo.setDailySummary(ds);

        // --- Step 7: 额外建议 ---
        List<String> tips = new ArrayList<>(HEALTH_TIPS);
        Collections.shuffle(tips);
        vo.setExtraAdvice(tips.subList(0, 2));

        return vo;
    }

    // ========================================================================
    // 🥗 核心算法：基于真实食物库的配餐引擎
    // ========================================================================
    private RecommendVO.Meal composeRealMeal(String type, String title, int mealCal, int goal) {
        // 1. 食物分类 (启发式算法)
        List<Food> staples = new ArrayList<>(); // 主食 (碳水高)
        List<Food> proteins = new ArrayList<>(); // 肉蛋奶 (蛋白高)
        List<Food> veggies = new ArrayList<>(); // 蔬果 (热量低)

        for (Food f : allFoodsCache) {
            if (f.getName().contains("油") || f.getName().contains("糖") || f.getName().contains("片")) continue; // 简单过滤零食

            // 简单分类逻辑
            if (f.getCalories() < 60 && f.getCarb() < 15) {
                veggies.add(f);
            } else if (f.getProtein() > 10 || f.getName().contains("肉") || f.getName().contains("蛋") || f.getName().contains("鱼")) {
                proteins.add(f);
            } else if (f.getCarb() > 15 || f.getName().contains("饭") || f.getName().contains("面") || f.getName().contains("粥")) {
                staples.add(f);
            }
        }

        // 防止空库报错 (兜底)
        if (staples.isEmpty() || proteins.isEmpty()) {
            return new RecommendVO.Meal(type, title, "数据库食物不足，请联系管理员添加", mealCal, new RecommendVO.Macros(0.0,0.0,0.0), "暂无建议");
        }

        // 2. 分配热量模型
        // 早餐/午餐：主食40% + 蛋白40% + 蔬菜20%
        // 晚餐：主食20% + 蛋白50% + 蔬菜30% (减脂期晚餐少碳水)
        double stapleRatio = 0.4, proteinRatio = 0.4, veggieRatio = 0.2;

        if ("dinner".equals(type) && goal == -1) {
            stapleRatio = 0.15; proteinRatio = 0.55; veggieRatio = 0.3; // 减脂晚餐
        }

        // 3. 随机抽取食物并计算克数
        Food stapleFood = staples.get(random.nextInt(staples.size()));
        Food proteinFood = proteins.get(random.nextInt(proteins.size()));
        Food veggieFood = veggies.isEmpty() ? null : veggies.get(random.nextInt(veggies.size()));

        // 计算重量 (克) = (目标热量 / 食物每100g热量) * 100
        int stapleWeight = (int) ((mealCal * stapleRatio) / stapleFood.getCalories() * 100);
        int proteinWeight = (int) ((mealCal * proteinRatio) / proteinFood.getCalories() * 100);
        int veggieWeight = veggieFood == null ? 0 : (int) ((mealCal * veggieRatio) / veggieFood.getCalories() * 100);

        // 取整到 10g 或 50g (更像人话)
        stapleWeight = (stapleWeight / 50 + 1) * 50; // 50g, 100g...
        proteinWeight = (proteinWeight / 10 + 1) * 10; // 120g...

        // 4. 拼接菜单字符串
        StringBuilder menu = new StringBuilder();
        menu.append(stapleFood.getName()).append(stapleWeight).append("g");
        menu.append(" + ").append(proteinFood.getName()).append(proteinWeight).append("g");
        if (veggieFood != null && veggieWeight > 0) {
            menu.append(" + ").append(veggieFood.getName()).append("适量");
        }

        // 5. 生成建议
        String advice = "";
        if ("breakfast".equals(type)) advice = "早餐是一天代谢的开关，蛋白质不能少。";
        else if ("lunch".equals(type)) advice = "午餐搭配要丰富，细嚼慢咽。";
        else advice = (goal == -1) ? "晚餐控制碳水，减轻身体负担。" : "晚餐清淡饮食，避免积食。";

        // 简算宏量 (这里为了性能未做精确累加，仅供前端展示趋势)
        RecommendVO.Macros macros = new RecommendVO.Macros(
                mealCal * 0.25 / 4, mealCal * 0.25 / 9, mealCal * 0.5 / 4
        );

        return new RecommendVO.Meal(type, title, menu.toString(), mealCal, macros, advice);
    }

    // --- 基础计算方法 ---
    private double analyzeHistoryIntake(Long userId, LocalDate start, LocalDate end) {
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).between("date", start, end);
        List<DietRecord> records = dietRecordMapper.selectList(query);
        if (records.isEmpty()) return 0;
        double total = 0;
        for (DietRecord r : records) {
            Food f = foodMapper.selectById(r.getFoodId());
            if (f != null) total += f.getCalories() * r.getQuantity();
        }
        return total / 7.0;
    }

    private double calculateBMR(User u) {
        double bmr = 10 * u.getWeight() + 6.25 * u.getHeight() - 5 * u.getAge();
        return u.getGender() == 1 ? bmr + 5 : bmr - 161;
    }

    private double calculateBMI(User u) {
        double h = u.getHeight() / 100.0;
        return Math.round(u.getWeight() / (h * h) * 10.0) / 10.0;
    }

    private String getBMIStatus(double bmi) {
        if (bmi < 18.5) return "underweight";
        if (bmi < 24) return "normal";
        if (bmi < 28) return "overweight";
        return "obese";
    }
}