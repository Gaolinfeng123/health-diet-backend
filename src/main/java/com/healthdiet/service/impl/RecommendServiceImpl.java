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
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3);
        double tdee = bmr * activityFactor;

        // --- Step 2: 7天历史回溯 ---
        double historyAvgCal = analyzeHistoryIntake(user.getId(), date.minusDays(7), date.minusDays(1));

        int adjustment = 0;
        boolean isPunishmentMode = false;
        boolean isRecoveryMode = false;

        if (historyAvgCal > 0) {
            if (historyAvgCal > tdee + 500) {
                adjustment = -300;
                isPunishmentMode = true;
            } else if (historyAvgCal < tdee - 500) {
                adjustment = +200;
                isRecoveryMode = true;
            }
        }

        // --- Step 3: 确定今日目标热量 ---
        int targetCal = (int) tdee + adjustment;
        Integer goalType = user.getTarget() != null ? user.getTarget() : 0;

        // 目标调整
        if      (goalType == -1) targetCal -= 300; // 减脂
        else if (goalType ==  1) targetCal += 300; // 增肌
        else if (goalType ==  2) targetCal -= 200; // 糖尿病：轻度控卡
        else if (goalType ==  3) targetCal = (int) tdee + adjustment; // 高血压：维持热量
        else if (goalType ==  4) targetCal -= 150; // 高血脂：轻度控卡

        // 安全底线
        if (targetCal < bmr) targetCal = (int) bmr;

        // --- Step 4: 填充 Summary ---
        RecommendVO.Summary summary = new RecommendVO.Summary();
        summary.setBmi(calculateBMI(user));
        summary.setStatus(getBMIStatus(summary.getBmi()));
        summary.setCaloriesTarget(targetCal);
        summary.setGoal(switch (goalType) {
            case -1 -> "lose_fat";
            case  1 -> "gain_muscle";
            case  2 -> "diabetes_control";
            case  3 -> "hypertension_control";
            case  4 -> "hyperlipidemia_control";
            default -> "maintain";
        });

        // keyMessage：历史摄入提示 + 目标建议，同时显示互不覆盖
        StringBuilder keyMsg = new StringBuilder();

        // 第一部分：历史摄入异常提示（有异常才显示）
        if (isPunishmentMode) {
            keyMsg.append("⚠️ 监测到近期热量严重超标，今日已启动【热量管控模式】。");
        } else if (isRecoveryMode) {
            keyMsg.append("❤️ 监测到近期摄入不足，今日已适当增加能量配给。");
        }

        // 第二部分：目标建议（始终显示）
        String goalMsg = switch (goalType) {
            case -1 -> "今日目标：制造热量缺口，保持低脂饮食。";
            case  1 -> "今日目标：保证碳水和蛋白摄入，为肌肉供能。";
            case  2 -> "今日目标：控制精制碳水，选择低升糖指数食物，稳定血糖。";
            case  3 -> "今日目标：严格控制钠摄入(<2000mg)，多吃钾元素丰富的蔬果。";
            case  4 -> "今日目标：减少饱和脂肪，增加膳食纤维，改善血脂水平。";
            default -> "今日目标：营养均衡，维持健康体重。";
        };
        if (keyMsg.length() > 0) keyMsg.append(" ");
        keyMsg.append(goalMsg);

        summary.setKeyMessage(keyMsg.toString());
        vo.setSummary(summary);

        // --- Step 5: 基于真实食物库生成三餐 ---
        List<RecommendVO.Meal> meals = new ArrayList<>();
        meals.add(composeRealMeal("breakfast", "早餐", (int)(targetCal * 0.3), goalType));
        meals.add(composeRealMeal("lunch",     "午餐", (int)(targetCal * 0.4), goalType));
        meals.add(composeRealMeal("dinner",    "晚餐", (int)(targetCal * 0.3), goalType));
        vo.setMeals(meals);

        // --- Step 6: 每日汇总 ---
        RecommendVO.DailySummary ds = new RecommendVO.DailySummary();
        ds.setTotalCalories(targetCal);

        double pRatio = goalType == -1 ? 0.35 : 0.25;
        ds.setTotalMacros(new RecommendVO.Macros(
                targetCal * pRatio / 4, targetCal * 0.25 / 9, targetCal * (1 - pRatio - 0.25) / 4
        ));
        ds.setPfcRatio(new RecommendVO.Macros(pRatio, 0.25, 1 - pRatio - 0.25));

        // summaryText：历史摄入情况 + 慢性病目标重点，同时显示互不覆盖
        StringBuilder summaryText = new StringBuilder();

        // 第一部分：历史摄入情况（有异常才附加）
        if (isPunishmentMode) {
            summaryText.append("【热量预警】近期摄入严重超标，今日菜单已控卡。");
        } else if (isRecoveryMode) {
            summaryText.append("【代谢恢复】近期摄入不足，今日已增加能量配给。");
        }

        // 第二部分：目标重点（始终显示）
        String focus = switch (goalType) {
            case -1 -> "高效燃脂";
            case  1 -> "肌肉合成";
            case  2 -> "血糖稳控";
            case  3 -> "血压管理";
            case  4 -> "血脂优化";
            default -> "营养均衡";
        };
        String focusDetail = switch (goalType) {
            case  2 -> "严格控制精制碳水摄入，优先选择低升糖食物。";
            case  3 -> "全天钠摄入控制在2000mg以内，多摄入富钾蔬果。";
            case  4 -> "减少动物脂肪和油炸食品，增加膳食纤维摄入。";
            default -> "请严格按照推荐食谱执行。";
        };
        if (summaryText.length() > 0) summaryText.append(" ");
        summaryText.append("今日重点：【").append(focus).append("】。").append(focusDetail);

        ds.setSummaryText(summaryText.toString());
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
        List<Food> staples  = new ArrayList<>();
        List<Food> proteins = new ArrayList<>();
        List<Food> veggies  = new ArrayList<>();

        for (Food f : allFoodsCache) {
            if (f.getName().contains("油") || f.getName().contains("糖") || f.getName().contains("片")) continue;

            if (f.getCalories() < 60 && f.getCarb() < 15) {
                veggies.add(f);
            } else if (f.getProtein() > 10 || f.getName().contains("肉") || f.getName().contains("蛋") || f.getName().contains("鱼")) {
                proteins.add(f);
            } else if (f.getCarb() > 15 || f.getName().contains("饭") || f.getName().contains("面") || f.getName().contains("粥")) {
                staples.add(f);
            }
        }

        if (staples.isEmpty() || proteins.isEmpty()) {
            return new RecommendVO.Meal(type, title, "数据库食物不足，请联系管理员添加", mealCal, new RecommendVO.Macros(0.0, 0.0, 0.0), "暂无建议");
        }

        // 配餐比例：根据目标调整三大类食物占比
        double stapleRatio = 0.4, proteinRatio = 0.4, veggieRatio = 0.2;

        if ("dinner".equals(type) && goal == -1) {
            stapleRatio = 0.15; proteinRatio = 0.55; veggieRatio = 0.3;  // 减脂晚餐
        } else if (goal == 2) {
            stapleRatio = 0.2;  proteinRatio = 0.45; veggieRatio = 0.35; // 糖尿病：降碳水
        } else if (goal == 3) {
            stapleRatio = 0.3;  proteinRatio = 0.35; veggieRatio = 0.35; // 高血压：增蔬果
        } else if (goal == 4) {
            stapleRatio = 0.35; proteinRatio = 0.3;  veggieRatio = 0.35; // 高血脂：降脂肪
        }

        Food stapleFood  = staples.get(random.nextInt(staples.size()));
        Food proteinFood = proteins.get(random.nextInt(proteins.size()));
        Food veggieFood  = veggies.isEmpty() ? null : veggies.get(random.nextInt(veggies.size()));

        int stapleWeight  = (int) ((mealCal * stapleRatio)  / stapleFood.getCalories()  * 100);
        int proteinWeight = (int) ((mealCal * proteinRatio) / proteinFood.getCalories() * 100);
        int veggieWeight  = veggieFood == null ? 0 : (int) ((mealCal * veggieRatio) / veggieFood.getCalories() * 100);

        stapleWeight  = (stapleWeight  / 50 + 1) * 50;
        proteinWeight = (proteinWeight / 10 + 1) * 10;

        StringBuilder menu = new StringBuilder();
        menu.append(stapleFood.getName()).append(stapleWeight).append("g");
        menu.append(" + ").append(proteinFood.getName()).append(proteinWeight).append("g");
        if (veggieFood != null && veggieWeight > 0) {
            menu.append(" + ").append(veggieFood.getName()).append("适量");
        }

        // 单餐建议：慢性病用户加专属提示
        String advice;
        if ("breakfast".equals(type)) {
            advice = "早餐是一天代谢的开关，蛋白质不能少。";
        } else if ("lunch".equals(type)) {
            advice = switch (goal) {
                case 2 -> "午餐避免精制米面，优先选择粗粮和豆类。";
                case 3 -> "午餐少放盐和酱油，用香草或柠檬汁提味。";
                case 4 -> "午餐选择清蒸或水煮，避免油炸烹饪方式。";
                default -> "午餐搭配要丰富，细嚼慢咽。";
            };
        } else {
            advice = switch (goal) {
                case -1 -> "晚餐控制碳水，减轻身体负担。";
                case  2 -> "晚餐以蔬菜和优质蛋白为主，严格限制主食量。";
                case  3 -> "晚餐清淡为主，避免腌制食品和高钠调料。";
                case  4 -> "晚餐选择低脂蛋白，搭配大量蔬菜。";
                default -> "晚餐清淡饮食，避免积食。";
            };
        }

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
        if (bmi < 24)   return "normal";
        if (bmi < 28)   return "overweight";
        return "obese";
    }
}