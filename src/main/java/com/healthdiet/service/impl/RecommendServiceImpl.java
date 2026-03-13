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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl extends ServiceImpl<RecommendMapper, Recommendation> implements IRecommendService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private FoodMapper foodMapper;

    @Autowired
    private ConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<Food> allFoodsCache = new ArrayList<>();

    private static final List<String> HEALTH_TIPS = Arrays.asList(
            "吃饭细嚼慢咽，尝试每口咀嚼20次以上。",
            "避免边看手机边吃饭，专注饮食能增加饱腹感。",
            "每天保证 7-8 小时睡眠，睡眠不足会增加食欲。",
            "下午 3 点是吃水果的较佳时间。",
            "睡前 3 小时尽量不要进食，减轻肠胃负担。",
            "减少加工食品摄入，多吃天然原型的食物。",
            "饭后散步 10-15 分钟，有助于消化和血糖管理。"
    );

    @Override
    public Recommendation getTodayRecommend(Long userId) {
        refreshFoodCache();

        LocalDate today = LocalDate.now();
        QueryWrapper<Recommendation> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", today);
        Recommendation exist = this.getOne(query);
        if (exist != null) {
            return exist;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getHeight() == null || user.getWeight() == null || user.getAge() == null || user.getGender() == null) {
            throw new RuntimeException("请先在个人中心完善身高、体重、年龄和性别！");
        }

        RecommendVO vo = generateSmartPlan(user, today);

        Recommendation rec = new Recommendation();
        rec.setUserId(userId);
        rec.setDate(today);
        try {
            rec.setResultJson(objectMapper.writeValueAsString(vo));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成推荐失败：JSON 转换错误");
        }

        this.save(rec);
        return rec;
    }

    private void refreshFoodCache() {
        allFoodsCache = foodMapper.selectList(null);
    }

    private RecommendVO generateSmartPlan(User user, LocalDate date) {
        RecommendVO vo = new RecommendVO();
        vo.setDate(date.toString());

        double bmr = calculateBMR(user);
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3);
        double tdee = bmr * activityFactor;

        double historyAvgCal = analyzeHistoryIntake(user.getId(), date.minusDays(7), date.minusDays(1));

        int adjustment = 0;
        boolean punishmentMode = false;
        boolean recoveryMode = false;

        if (historyAvgCal > 0) {
            if (historyAvgCal > tdee + 500) {
                adjustment = -300;
                punishmentMode = true;
            } else if (historyAvgCal < tdee - 500) {
                adjustment = 200;
                recoveryMode = true;
            }
        }

        int goalType = user.getTarget() != null ? user.getTarget() : 0;

        int targetCal = (int) Math.round(tdee) + adjustment;
        switch (goalType) {
            case -1 -> targetCal -= 300; // 减脂
            case 1 -> targetCal += 300;  // 增肌
            case 2 -> targetCal -= 200;  // 糖尿病控糖
            case 3 -> targetCal -= 100;  // 高血压略保守
            case 4 -> targetCal -= 150;  // 高血脂
            default -> {
            }
        }

        if (targetCal < bmr) {
            targetCal = (int) Math.round(bmr);
        }

        RecommendVO.Summary summary = new RecommendVO.Summary();
        double bmi = calculateBMI(user);
        summary.setBmi(bmi);
        summary.setStatus(getBMIStatus(bmi));
        summary.setCaloriesTarget(targetCal);
        summary.setGoal(mapGoal(goalType));
        summary.setKeyMessage(buildKeyMessage(goalType, punishmentMode, recoveryMode));
        vo.setSummary(summary);

        Set<Long> usedFoodIds = new HashSet<>();
        List<RecommendVO.Meal> meals = new ArrayList<>();

        MealBuildResult breakfast = composeSmartMeal("breakfast", "早餐", (int) Math.round(targetCal * 0.30), goalType, usedFoodIds);
        meals.add(breakfast.meal);
        addUsedFoodIds(breakfast, usedFoodIds);

        MealBuildResult lunch = composeSmartMeal("lunch", "午餐", (int) Math.round(targetCal * 0.40), goalType, usedFoodIds);
        meals.add(lunch.meal);
        addUsedFoodIds(lunch, usedFoodIds);

        MealBuildResult dinner = composeSmartMeal("dinner", "晚餐", (int) Math.round(targetCal * 0.30), goalType, usedFoodIds);
        meals.add(dinner.meal);
        addUsedFoodIds(dinner, usedFoodIds);

        vo.setMeals(meals);

        double totalProtein = meals.stream().mapToDouble(m -> safe(m.getMacros().getProtein())).sum();
        double totalFat = meals.stream().mapToDouble(m -> safe(m.getMacros().getFat())).sum();
        double totalCarb = meals.stream().mapToDouble(m -> safe(m.getMacros().getCarbs())).sum();

        int actualTotalCalories = (int) Math.round(totalProtein * 4 + totalFat * 9 + totalCarb * 4);

        RecommendVO.DailySummary ds = new RecommendVO.DailySummary();
        ds.setTotalCalories(actualTotalCalories);
        ds.setTotalMacros(new RecommendVO.Macros(round1(totalProtein), round1(totalFat), round1(totalCarb)));

        double totalMacroCalories = totalProtein * 4 + totalFat * 9 + totalCarb * 4;
        if (totalMacroCalories > 0) {
            ds.setPfcRatio(new RecommendVO.Macros(
                    round3(totalProtein * 4 / totalMacroCalories),
                    round3(totalFat * 9 / totalMacroCalories),
                    round3(totalCarb * 4 / totalMacroCalories)
            ));
        } else {
            ds.setPfcRatio(new RecommendVO.Macros(0.0, 0.0, 0.0));
        }

        ds.setSummaryText(buildDailySummary(goalType, punishmentMode, recoveryMode));
        vo.setDailySummary(ds);

        List<String> tips = new ArrayList<>(HEALTH_TIPS);
        Collections.shuffle(tips);
        vo.setExtraAdvice(tips.subList(0, 2));

        return vo;
    }

    private MealBuildResult composeSmartMeal(String type, String title, int mealCal, int goalType, Set<Long> usedFoodIds) {
        MealTarget target = buildMealTarget(type, mealCal, goalType);
        CandidateBuckets buckets = buildFoodCandidates(type, goalType);

        MealBuildResult result = new MealBuildResult();

        if (buckets.staples.isEmpty() || buckets.proteins.isEmpty() || buckets.veggies.isEmpty()) {
            result.meal = new RecommendVO.Meal(
                    type,
                    title,
                    "候选食物不足，请补充主食/蛋白/蔬菜数据",
                    0,
                    new RecommendVO.Macros(0.0, 0.0, 0.0),
                    "请先完善 food_category 和慢病标签"
            );
            return result;
        }

        MealSolution best = solveBestPortion(type, goalType, target, buckets, usedFoodIds);
        if (best == null) {
            result.meal = new RecommendVO.Meal(
                    type,
                    title,
                    "未找到合适配餐，请调整食物库",
                    0,
                    new RecommendVO.Macros(0.0, 0.0, 0.0),
                    "建议增加更多主食、蛋白和蔬菜候选"
            );
            return result;
        }

        String menu = best.staple.getName() + best.stapleWeight + "g"
                + " + " + best.protein.getName() + best.proteinWeight + "g"
                + " + " + best.veggie.getName() + best.veggieWeight + "g";

        RecommendVO.Macros macros = new RecommendVO.Macros(
                round1(best.proteinG),
                round1(best.fatG),
                round1(best.carbG)
        );

        result.meal = new RecommendVO.Meal(
                type,
                title,
                menu,
                best.actualCalories,
                macros,
                buildMealAdvice(type, goalType)
        );

        result.stapleId = best.staple.getId();
        result.proteinId = best.protein.getId();
        result.veggieId = best.veggie.getId();

        return result;
    }

    private void addUsedFoodIds(MealBuildResult result, Set<Long> usedFoodIds) {
        if (result == null) {
            return;
        }
        if (result.stapleId != null) {
            usedFoodIds.add(result.stapleId);
        }
        if (result.proteinId != null) {
            usedFoodIds.add(result.proteinId);
        }
        if (result.veggieId != null) {
            usedFoodIds.add(result.veggieId);
        }
    }

    private MealTarget buildMealTarget(String mealType, int mealCal, int goalType) {
        double pRatio;
        double fRatio;
        double cRatio;

        switch (goalType) {
            case -1 -> { // 减脂
                pRatio = "dinner".equals(mealType) ? 0.38 : 0.35;
                fRatio = 0.27;
                cRatio = 1.0 - pRatio - fRatio;
            }
            case 1 -> { // 增肌
                pRatio = 0.28;
                fRatio = 0.22;
                cRatio = 0.50;
            }
            case 2 -> { // 糖尿病控糖
                pRatio = 0.25;
                fRatio = 0.30;
                cRatio = 0.45;
            }
            case 3 -> { // 高血压
                pRatio = 0.25;
                fRatio = 0.25;
                cRatio = 0.50;
            }
            case 4 -> { // 高血脂
                pRatio = 0.24;
                fRatio = 0.23;
                cRatio = 0.53;
            }
            default -> { // 维持
                pRatio = 0.25;
                fRatio = 0.25;
                cRatio = 0.50;
            }
        }

        MealTarget target = new MealTarget();
        target.mealCalories = mealCal;
        target.proteinG = mealCal * pRatio / 4.0;
        target.fatG = mealCal * fRatio / 9.0;
        target.carbG = mealCal * cRatio / 4.0;
        return target;
    }

    private CandidateBuckets buildFoodCandidates(String mealType, int goalType) {
        List<Food> staples = new ArrayList<>();
        List<Food> proteins = new ArrayList<>();
        List<Food> veggies = new ArrayList<>();

        for (Food food : allFoodsCache) {
            if (!isMealFriendly(food, mealType)) {
                continue;
            }
            if (shouldExclude(food, goalType)) {
                continue;
            }

            String category = normalizeCategory(food);

            if ("staple".equals(category)) {
                staples.add(food);
            } else if ("protein".equals(category) || "dairy".equals(category)) {
                proteins.add(food);
            } else if ("vegetable".equals(category)) {
                veggies.add(food);
            }
        }

        staples.sort(Comparator
                .comparing((Food f) -> staplePenalty(f, goalType, mealType))
                .thenComparing(f -> fiberValue(f), Comparator.reverseOrder()));

        proteins.sort(Comparator
                .comparing((Food f) -> proteinPenalty(f, goalType, mealType))
                .thenComparing(f -> proteinDensity(f), Comparator.reverseOrder()));

        veggies.sort(Comparator
                .comparing((Food f) -> vegetablePenalty(f, goalType, mealType))
                .thenComparing(f -> fiberValue(f), Comparator.reverseOrder()));

        CandidateBuckets buckets = new CandidateBuckets();
        buckets.staples = limitList(staples, 10);
        buckets.proteins = limitList(proteins, 10);
        buckets.veggies = limitList(veggies, 10);
        return buckets;
    }

    private MealSolution solveBestPortion(String mealType, int goalType, MealTarget target, CandidateBuckets buckets, Set<Long> usedFoodIds) {
        MealSolution best = null;

        for (Food staple : buckets.staples) {
            int stapleMax = getStapleMaxWeight(staple, mealType, goalType);

            for (Food protein : buckets.proteins) {
                int proteinMax = getProteinMaxWeight(protein, mealType, goalType);

                for (Food veggie : buckets.veggies) {
                    int veggieMax = getVeggieMaxWeight(veggie, mealType, goalType);

                    for (int stapleWeight = 50; stapleWeight <= stapleMax; stapleWeight += 25) {
                        for (int proteinWeight = 50; proteinWeight <= proteinMax; proteinWeight += 10) {
                            for (int veggieWeight = 100; veggieWeight <= veggieMax; veggieWeight += 50) {
                                MealSolution candidate = evaluateSolution(
                                        staple, stapleWeight,
                                        protein, proteinWeight,
                                        veggie, veggieWeight,
                                        target, goalType, mealType, usedFoodIds
                                );

                                if (best == null || candidate.score < best.score) {
                                    best = candidate;
                                }
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private MealSolution evaluateSolution(
            Food staple, int stapleWeight,
            Food protein, int proteinWeight,
            Food veggie, int veggieWeight,
            MealTarget target, int goalType,
            String mealType,
            Set<Long> usedFoodIds
    ) {
        double calories = nutrient(staple.getCalories(), stapleWeight)
                + nutrient(protein.getCalories(), proteinWeight)
                + nutrient(veggie.getCalories(), veggieWeight);

        double proteinG = nutrient(staple.getProtein(), stapleWeight)
                + nutrient(protein.getProtein(), proteinWeight)
                + nutrient(veggie.getProtein(), veggieWeight);

        double fatG = nutrient(staple.getFat(), stapleWeight)
                + nutrient(protein.getFat(), proteinWeight)
                + nutrient(veggie.getFat(), veggieWeight);

        double carbG = nutrient(staple.getCarb(), stapleWeight)
                + nutrient(protein.getCarb(), proteinWeight)
                + nutrient(veggie.getCarb(), veggieWeight);

        double sodium = decimalNutrient(staple.getSodiumMg(), stapleWeight)
                + decimalNutrient(protein.getSodiumMg(), proteinWeight)
                + decimalNutrient(veggie.getSodiumMg(), veggieWeight);

        double satFat = decimalNutrient(staple.getSaturatedFat(), stapleWeight)
                + decimalNutrient(protein.getSaturatedFat(), proteinWeight)
                + decimalNutrient(veggie.getSaturatedFat(), veggieWeight);

        double fiber = decimalNutrient(staple.getFiber(), stapleWeight)
                + decimalNutrient(protein.getFiber(), proteinWeight)
                + decimalNutrient(veggie.getFiber(), veggieWeight);

        double sugar = decimalNutrient(staple.getSugar(), stapleWeight)
                + decimalNutrient(protein.getSugar(), proteinWeight)
                + decimalNutrient(veggie.getSugar(), veggieWeight);

        double score =
                Math.abs(calories - target.mealCalories) * 1.5
                        + Math.abs(proteinG - target.proteinG) * 3.5
                        + Math.abs(fatG - target.fatG) * 2.3
                        + Math.abs(carbG - target.carbG) * 3.2;

        if ("dinner".equals(mealType) && calories > target.mealCalories + 80) {
            score += 40;
        }
        if (proteinWeight > 160) {
            score += (proteinWeight - 160) * 0.8;
        }
        if (veggieWeight > 220) {
            score += (veggieWeight - 220) * 0.3;
        }
        if (stapleWeight > 180) {
            score += (stapleWeight - 180) * 0.5;
        }

        if (usedFoodIds.contains(staple.getId())) {
            score += 90;
        }
        if (usedFoodIds.contains(protein.getId())) {
            score += 140;
        }
        if (usedFoodIds.contains(veggie.getId())) {
            score += 60;
        }

        switch (goalType) {
            case -1 -> { // 减脂
                if (calories > target.mealCalories) {
                    score += (calories - target.mealCalories) * 1.2;
                }
                if ("dinner".equals(mealType) && stapleWeight > 100) {
                    score += (stapleWeight - 100) * 1.2;
                }
                score -= proteinG * 0.4;
                score -= fiber * 1.2;
            }
            case 1 -> { // 增肌
                if (calories < target.mealCalories - 80) {
                    score += (target.mealCalories - calories) * 0.8;
                }
                if (proteinG < target.proteinG) {
                    score += (target.proteinG - proteinG) * 4.0;
                }
                if (stapleWeight < 100) {
                    score += 20;
                }
            }
            case 2 -> { // 糖尿病控糖
                String gi = nullToDefault(staple.getGiLevel(), "medium");
                if ("high".equalsIgnoreCase(gi)) {
                    score += 120;
                } else if ("medium".equalsIgnoreCase(gi)) {
                    score += 20;
                }
                score += sugar * 1.6;
                score -= fiber * 2.2;
                if (carbG > target.carbG + 12) {
                    score += (carbG - target.carbG) * 2.5;
                }
            }
            case 3 -> { // 高血压
                score += sodium * 0.06;
                if (sodium > 600) {
                    score += (sodium - 600) * 0.08;
                }
                score -= fiber * 1.0;
            }
            case 4 -> { // 高血脂
                score += satFat * 12.0;
                if (fatG > target.fatG + 8) {
                    score += (fatG - target.fatG) * 3.0;
                }
                score -= fiber * 1.5;
            }
            default -> score -= fiber * 0.6;
        }

        MealSolution result = new MealSolution();
        result.staple = staple;
        result.protein = protein;
        result.veggie = veggie;
        result.stapleWeight = stapleWeight;
        result.proteinWeight = proteinWeight;
        result.veggieWeight = veggieWeight;
        result.actualCalories = (int) Math.round(calories);
        result.proteinG = proteinG;
        result.fatG = fatG;
        result.carbG = carbG;
        result.score = score;
        return result;
    }

    private int getStapleMaxWeight(Food food, String mealType, int goalType) {
        String name = nullToDefault(food.getName(), "");

        if (name.contains("燕麦")) return 80;
        if (name.contains("面包")) return 120;
        if (name.contains("馒头")) return 120;
        if (name.contains("玉米")) return 180;
        if (name.contains("红薯")) return 180;
        if ("dinner".equals(mealType) && goalType == -1) return 100;
        if (goalType == 2) return 160;
        return 180;
    }

    private int getProteinMaxWeight(Food food, String mealType, int goalType) {
        String name = nullToDefault(food.getName(), "");

        if (name.contains("鸡蛋")) return 120;
        if (name.contains("牛奶")) return 300;
        if (name.contains("豆腐")) return 220;
        if (name.contains("三文鱼")) return 160;
        if (name.contains("沙丁鱼")) return 150;
        if (goalType == 1) return 200;
        return 180;
    }

    private int getVeggieMaxWeight(Food food, String mealType, int goalType) {
        String name = nullToDefault(food.getName(), "");

        if (name.contains("木耳")) return 200;
        if (name.contains("番茄") || name.contains("西红柿")) return 200;
        if (name.contains("黄瓜")) return 200;
        return 250;
    }

    private boolean isMealFriendly(Food food, String mealType) {
        if ("breakfast".equals(mealType)) {
            return food.getBreakfastFriendly() == null || food.getBreakfastFriendly() == 1;
        }
        if ("dinner".equals(mealType)) {
            return food.getDinnerFriendly() == null || food.getDinnerFriendly() == 1;
        }
        return true;
    }

    private boolean shouldExclude(Food food, int goalType) {
        String name = nullToDefault(food.getName(), "");
        String category = normalizeCategory(food);

        if ("mixed".equals(category) || "fruit".equals(category) || "nut".equals(category)) {
            return true;
        }

        if (name.contains("可乐")
                || name.contains("奶茶")
                || name.contains("巧克力")
                || name.contains("汉堡")
                || name.contains("薯片")
                || name.contains("炸薯条")) {
            return true;
        }

        if (goalType == 2 && "high".equalsIgnoreCase(nullToDefault(food.getGiLevel(), "medium")) && "staple".equals(category)) {
            return true;
        }

        return false;
    }

    private String normalizeCategory(Food food) {
        String category = nullToDefault(food.getFoodCategory(), "").trim().toLowerCase();
        if (!category.isEmpty()) {
            return category;
        }

        String name = nullToDefault(food.getName(), "");
        if (safe(food.getProtein()) > 10 || name.contains("肉") || name.contains("蛋") || name.contains("鱼") || name.contains("豆腐")) {
            return "protein";
        }
        if (safe(food.getCalories()) < 60 && safe(food.getCarb()) < 15) {
            return "vegetable";
        }
        if (safe(food.getCarb()) > 15 || name.contains("饭") || name.contains("面") || name.contains("粥")) {
            return "staple";
        }
        return "mixed";
    }

    private double staplePenalty(Food food, int goalType, String mealType) {
        double penalty = 0;
        String gi = nullToDefault(food.getGiLevel(), "medium");
        double fiber = fiberValue(food);
        String name = nullToDefault(food.getName(), "");

        switch (goalType) {
            case -1 -> {
                penalty += safe(food.getCalories()) * 0.05;
                penalty -= fiber * 0.8;
            }
            case 1 -> {
                penalty -= safe(food.getCarb()) * 0.1;
                penalty -= fiber * 0.3;
            }
            case 2 -> {
                if ("high".equalsIgnoreCase(gi)) {
                    penalty += 80;
                } else if ("medium".equalsIgnoreCase(gi)) {
                    penalty += 15;
                }
                penalty -= fiber * 1.5;
            }
            case 3 -> {
                penalty += decimalValue(food.getSodiumMg()) * 0.02;
                penalty -= fiber * 0.6;
            }
            case 4 -> {
                penalty += decimalValue(food.getSaturatedFat()) * 2.0;
                penalty -= fiber * 1.0;
            }
            default -> penalty -= fiber * 0.4;
        }

        if ("breakfast".equals(mealType)) {
            if (name.contains("燕麦") || name.contains("全麦") || name.contains("玉米") || name.contains("红薯")) {
                penalty -= 12;
            }
            if (name.contains("意面") || name.contains("藜麦")) {
                penalty += 6;
            }
        }

        if ("dinner".equals(mealType)) {
            penalty += safe(food.getCalories()) * 0.015;
        }

        return penalty;
    }

    private double proteinPenalty(Food food, int goalType, String mealType) {
        double penalty = 0;
        String name = nullToDefault(food.getName(), "");

        penalty += decimalValue(food.getSaturatedFat()) * (goalType == 4 ? 10.0 : 2.0);
        penalty += decimalValue(food.getSodiumMg()) * (goalType == 3 ? 0.03 : 0.005);
        penalty -= proteinDensity(food) * 2.0;

        if (goalType == 1) {
            penalty -= proteinDensity(food) * 1.2;
        }
        if (goalType == 2) {
            penalty += decimalValue(food.getSugar()) * 0.8;
        }

        if ("breakfast".equals(mealType)) {
            if (name.contains("鸡蛋") || name.contains("牛奶") || name.contains("豆腐")) {
                penalty -= 15;
            }
            if (name.contains("毛豆")) {
                penalty += 6;
            }
            if (name.contains("鱼") || name.contains("三文鱼") || name.contains("沙丁鱼")
                    || name.contains("猪肉") || name.contains("牛肉")) {
                penalty += 10;
            }
        }

        if ("dinner".equals(mealType)) {
            penalty += decimalValue(food.getSaturatedFat()) * 1.0;
            if (name.contains("鸡蛋")) {
                penalty += 3;
            }
        }

        return penalty;
    }

    private double vegetablePenalty(Food food, int goalType, String mealType) {
        double penalty = 0;
        String name = nullToDefault(food.getName(), "");

        penalty -= fiberValue(food);

        if (goalType == 3) {
            penalty += decimalValue(food.getSodiumMg()) * 0.01;
        }
        if (goalType == 2) {
            penalty -= fiberValue(food) * 0.8;
            penalty += decimalValue(food.getSugar()) * 0.5;
        }

        if ("breakfast".equals(mealType)) {
            if (name.contains("西蓝花") || name.contains("生菜") || name.contains("黄瓜") || name.contains("西红柿")) {
                penalty -= 4;
            }
            if (name.contains("木耳") || name.contains("莲藕")) {
                penalty += 5;
            }
        }

        return penalty;
    }

    private double proteinDensity(Food food) {
        return safe(food.getProtein()) - safe(food.getFat()) * 0.35;
    }

    private double fiberValue(Food food) {
        return decimalValue(food.getFiber());
    }

    private double analyzeHistoryIntake(Long userId, LocalDate start, LocalDate end) {
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).between("date", start, end);
        List<DietRecord> records = dietRecordMapper.selectList(query);

        if (records.isEmpty()) {
            return 0;
        }

        Set<Long> foodIds = records.stream()
                .map(DietRecord::getFoodId)
                .collect(Collectors.toSet());

        Map<Long, Food> foodMap = foodMapper.selectBatchIds(foodIds)
                .stream()
                .collect(Collectors.toMap(Food::getId, f -> f));

        Map<LocalDate, Double> dayCalories = new HashMap<>();

        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId());
            if (food == null) {
                continue;
            }
            // 当前先兼容旧 quantity 语义：1 = 一个 100g 单位
            double cal = safe(food.getCalories()) * safeInt(record.getQuantity());
            dayCalories.merge(record.getDate(), cal, Double::sum);
        }

        if (dayCalories.isEmpty()) {
            return 0;
        }

        return dayCalories.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double calculateBMR(User user) {
        double bmr = 10 * user.getWeight() + 6.25 * user.getHeight() - 5 * user.getAge();
        return user.getGender() == 1 ? bmr + 5 : bmr - 161;
    }

    private double calculateBMI(User user) {
        double h = user.getHeight() / 100.0;
        return round1(user.getWeight() / (h * h));
    }

    private String getBMIStatus(double bmi) {
        if (bmi < 18.5) return "underweight";
        if (bmi < 24) return "normal";
        if (bmi < 28) return "overweight";
        return "obese";
    }

    private String mapGoal(int goalType) {
        return switch (goalType) {
            case -1 -> "lose_fat";
            case 1 -> "gain_muscle";
            case 2 -> "diabetes_control";
            case 3 -> "hypertension_control";
            case 4 -> "hyperlipidemia_control";
            default -> "maintain";
        };
    }

    private String buildKeyMessage(int goalType, boolean punishmentMode, boolean recoveryMode) {
        StringBuilder sb = new StringBuilder();

        if (punishmentMode) {
            sb.append("⚠️ 监测到近期热量明显超标，今日已启动热量管控。");
        } else if (recoveryMode) {
            sb.append("❤️ 监测到近期摄入偏低，今日已适当提高能量供给。");
        }

        String goalMsg = switch (goalType) {
            case -1 -> "今日重点：提高蛋白占比，控制总热量和晚餐主食。";
            case 1 -> "今日重点：保证碳水与优质蛋白，支持增肌恢复。";
            case 2 -> "今日重点：优先低GI主食，减少高糖与精制碳水。";
            case 3 -> "今日重点：控制钠摄入，避免高盐加工食品。";
            case 4 -> "今日重点：减少饱和脂肪，提高膳食纤维摄入。";
            default -> "今日重点：保持营养均衡，三餐结构稳定。";
        };

        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(goalMsg);
        return sb.toString();
    }

    private String buildDailySummary(int goalType, boolean punishmentMode, boolean recoveryMode) {
        StringBuilder sb = new StringBuilder();

        if (punishmentMode) {
            sb.append("【历史预警】近期总热量偏高，今日菜单已进行控卡。");
        } else if (recoveryMode) {
            sb.append("【代谢恢复】近期总摄入偏低，今日菜单已增加能量。");
        }

        String focus = switch (goalType) {
            case -1 -> "今日执行重点：优先高蛋白、高纤维、避免晚餐过量主食。";
            case 1 -> "今日执行重点：保证训练日供能与优质蛋白输入。";
            case 2 -> "今日执行重点：控制高GI和高糖食物，稳定碳水结构。";
            case 3 -> "今日执行重点：严格控制钠摄入并保持饮食清淡。";
            case 4 -> "今日执行重点：减少饱和脂肪并提高高纤维食物比例。";
            default -> "今日执行重点：平衡三餐结构，避免过度偏科。";
        };

        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(focus);
        return sb.toString();
    }

    private String buildMealAdvice(String type, int goalType) {
        if ("breakfast".equals(type)) {
            return switch (goalType) {
                case -1 -> "早餐保持高蛋白但避免过量主食，帮助控制全天食欲。";
                case 1 -> "早餐建议包含主食和蛋白，为训练和恢复做准备。";
                case 2 -> "早餐优先低GI主食搭配蛋白，避免高糖饮料。";
                case 3 -> "早餐尽量清淡，减少咸味加工食品。";
                case 4 -> "早餐避免高脂加工肉，优先低脂蛋白。";
                default -> "早餐尽量包含主食 + 蛋白，帮助启动全天代谢。";
            };
        }

        if ("lunch".equals(type)) {
            return switch (goalType) {
                case -1 -> "午餐保证蛋白和蔬菜充足，主食不过量。";
                case 1 -> "午餐是增肌期核心供能餐，注意碳水和蛋白都要到位。";
                case 2 -> "午餐优先低GI主食和高纤维蔬菜，稳定餐后血糖。";
                case 3 -> "午餐尽量减少酱汁和加工肉，优先清淡烹调。";
                case 4 -> "午餐避免油炸和高脂肉类，优先瘦肉或鱼类。";
                default -> "午餐是全天最核心的一餐，注意结构均衡。";
            };
        }

        return switch (goalType) {
            case -1 -> "晚餐控制主食不过量，保持蛋白和蔬菜充足。";
            case 1 -> "晚餐可保留适量主食和蛋白，支持恢复但避免暴食。";
            case 2 -> "晚餐继续控制高GI主食和甜食摄入。";
            case 3 -> "晚餐避免高盐菜品和腌制食品。";
            case 4 -> "晚餐优先低脂蛋白和高纤维蔬菜。";
            default -> "晚餐宜清淡，避免过量进食。";
        };
    }

    private double nutrient(Double per100g, int weightG) {
        return safe(per100g) * weightG / 100.0;
    }

    private double decimalNutrient(BigDecimal per100g, int weightG) {
        return decimalValue(per100g) * weightG / 100.0;
    }

    private List<Food> limitList(List<Food> list, int maxSize) {
        if (list.size() <= maxSize) {
            return list;
        }
        return new ArrayList<>(list.subList(0, maxSize));
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double decimalValue(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static class MealTarget {
        private int mealCalories;
        private double proteinG;
        private double fatG;
        private double carbG;
    }

    private static class CandidateBuckets {
        private List<Food> staples = new ArrayList<>();
        private List<Food> proteins = new ArrayList<>();
        private List<Food> veggies = new ArrayList<>();
    }

    private static class MealSolution {
        private Food staple;
        private Food protein;
        private Food veggie;
        private int stapleWeight;
        private int proteinWeight;
        private int veggieWeight;
        private int actualCalories;
        private double proteinG;
        private double fatG;
        private double carbG;
        private double score;
    }

    private static class MealBuildResult {
        private RecommendVO.Meal meal;
        private Long stapleId;
        private Long proteinId;
        private Long veggieId;
    }
}