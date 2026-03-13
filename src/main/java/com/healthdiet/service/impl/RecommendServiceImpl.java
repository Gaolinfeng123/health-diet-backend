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
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.model.CandidateBuckets;
import com.healthdiet.recommend.model.MealBuildResult;
import com.healthdiet.recommend.model.MealSolution;
import com.healthdiet.recommend.model.MealTarget;
import com.healthdiet.recommend.rule.FoodRuleHelper;
import com.healthdiet.recommend.rule.GoalRuleProfile;
import com.healthdiet.recommend.rule.RecommendationScorer;
import com.healthdiet.service.ConfigService;
import com.healthdiet.service.IRecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private final RecommendationScorer scorer = new RecommendationScorer();

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
        GoalType goalType = GoalType.fromCode(user.getTarget());
        GoalRuleProfile profile = GoalRuleProfile.of(goalType);

        int targetCal = calculateDailyTargetCalories(user, date, goalType);

        RecommendVO vo = new RecommendVO();
        vo.setDate(date.toString());
        vo.setSummary(buildSummary(user, goalType, targetCal, date));

        Set<Long> usedFoodIds = new HashSet<>();
        List<RecommendVO.Meal> meals = new ArrayList<>();

        MealBuildResult breakfast = composeSmartMeal(MealType.BREAKFAST, (int) Math.round(targetCal * 0.30), profile, usedFoodIds);
        meals.add(breakfast.getMeal());
        addUsedFoodIds(breakfast, usedFoodIds);

        MealBuildResult lunch = composeSmartMeal(MealType.LUNCH, (int) Math.round(targetCal * 0.40), profile, usedFoodIds);
        meals.add(lunch.getMeal());
        addUsedFoodIds(lunch, usedFoodIds);

        MealBuildResult dinner = composeSmartMeal(MealType.DINNER, (int) Math.round(targetCal * 0.30), profile, usedFoodIds);
        meals.add(dinner.getMeal());
        addUsedFoodIds(dinner, usedFoodIds);

        vo.setMeals(meals);
        vo.setDailySummary(buildDailySummary(profile, meals));
        vo.setExtraAdvice(buildExtraAdvice());

        return vo;
    }

    private int calculateDailyTargetCalories(User user, LocalDate date, GoalType goalType) {
        double bmr = calculateBMR(user);
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3);
        double tdee = bmr * activityFactor;

        double historyAvgCal = analyzeHistoryIntake(user.getId(), date.minusDays(7), date.minusDays(1));

        int adjustment = 0;
        if (historyAvgCal > 0) {
            if (historyAvgCal > tdee + 500) {
                adjustment = -300;
            } else if (historyAvgCal < tdee - 500) {
                adjustment = 200;
            }
        }

        int targetCal = (int) Math.round(tdee) + adjustment;
        switch (goalType) {
            case LOSE_FAT -> targetCal -= 300;
            case GAIN_MUSCLE -> targetCal += 300;
            case DIABETES_CONTROL -> targetCal -= 200;
            case HYPERTENSION_CONTROL -> targetCal -= 100;
            case HYPERLIPIDEMIA_CONTROL -> targetCal -= 150;
            case MAINTAIN -> {
            }
        }

        if (targetCal < bmr) {
            targetCal = (int) Math.round(bmr);
        }
        return targetCal;
    }

    private RecommendVO.Summary buildSummary(User user, GoalType goalType, int targetCal, LocalDate date) {
        double bmi = calculateBMI(user);

        boolean punishmentMode = false;
        boolean recoveryMode = false;

        double bmr = calculateBMR(user);
        double activityFactor = configService.getDouble("BMR_ACTIVITY_FACTOR", 1.3);
        double tdee = bmr * activityFactor;
        double historyAvgCal = analyzeHistoryIntake(user.getId(), date.minusDays(7), date.minusDays(1));

        if (historyAvgCal > 0) {
            if (historyAvgCal > tdee + 500) {
                punishmentMode = true;
            } else if (historyAvgCal < tdee - 500) {
                recoveryMode = true;
            }
        }

        RecommendVO.Summary summary = new RecommendVO.Summary();
        summary.setBmi(bmi);
        summary.setStatus(getBMIStatus(bmi));
        summary.setCaloriesTarget(targetCal);
        summary.setGoal(mapGoal(goalType));
        summary.setKeyMessage(buildKeyMessage(goalType, punishmentMode, recoveryMode));
        return summary;
    }

    private MealBuildResult composeSmartMeal(MealType mealType, int mealCal, GoalRuleProfile profile, Set<Long> usedFoodIds) {
        MealTarget target = profile.buildMealTarget(mealType, mealCal);
        CandidateBuckets buckets = buildFoodCandidates(mealType, profile);

        MealBuildResult result = new MealBuildResult();

        if (buckets.getStaples().isEmpty() || buckets.getProteins().isEmpty() || buckets.getVeggies().isEmpty()) {
            result.setMeal(new RecommendVO.Meal(
                    mealType.getCode(),
                    mealType.getTitle(),
                    "候选食物不足，请补充主食/蛋白/蔬菜数据",
                    0,
                    new RecommendVO.Macros(0.0, 0.0, 0.0),
                    "请先完善 food_category 和慢病标签"
            ));
            return result;
        }

        MealSolution best = solveBestPortion(mealType, profile, target, buckets, usedFoodIds);
        if (best == null) {
            result.setMeal(new RecommendVO.Meal(
                    mealType.getCode(),
                    mealType.getTitle(),
                    "未找到合适配餐，请调整食物库",
                    0,
                    new RecommendVO.Macros(0.0, 0.0, 0.0),
                    "建议增加更多主食、蛋白和蔬菜候选"
            ));
            return result;
        }

        String menu = best.getStaple().getName() + best.getStapleWeight() + "g"
                + " + " + best.getProtein().getName() + best.getProteinWeight() + "g"
                + " + " + best.getVeggie().getName() + best.getVeggieWeight() + "g";

        result.setMeal(new RecommendVO.Meal(
                mealType.getCode(),
                mealType.getTitle(),
                menu,
                best.getActualCalories(),
                new RecommendVO.Macros(
                        round1(best.getProteinG()),
                        round1(best.getFatG()),
                        round1(best.getCarbG())
                ),
                buildMealAdvice(mealType, profile.getGoalType())
        ));

        result.setStapleId(best.getStaple().getId());
        result.setProteinId(best.getProtein().getId());
        result.setVeggieId(best.getVeggie().getId());

        return result;
    }

    private CandidateBuckets buildFoodCandidates(MealType mealType, GoalRuleProfile profile) {
        List<Food> staples = new ArrayList<>();
        List<Food> proteins = new ArrayList<>();
        List<Food> veggies = new ArrayList<>();

        for (Food food : allFoodsCache) {
            if (!FoodRuleHelper.isMealFriendly(food, mealType)) {
                continue;
            }
            if (FoodRuleHelper.shouldExclude(food, profile.getGoalType())) {
                continue;
            }

            String category = FoodRuleHelper.normalizeCategory(food);
            switch (category) {
                case "staple" -> staples.add(food);
                case "protein", "dairy" -> proteins.add(food);
                case "vegetable" -> veggies.add(food);
                default -> {
                }
            }
        }

        staples.sort(Comparator
                .comparing((Food f) -> scoreStapleCandidate(f, profile, mealType))
                .thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()));

        proteins.sort(Comparator
                .comparing((Food f) -> scoreProteinCandidate(f, profile, mealType))
                .thenComparing(FoodRuleHelper::proteinDensity, Comparator.reverseOrder()));

        veggies.sort(Comparator
                .comparing((Food f) -> scoreVeggieCandidate(f, profile, mealType))
                .thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()));

        CandidateBuckets buckets = new CandidateBuckets();
        buckets.setStaples(limitList(staples, 10));
        buckets.setProteins(limitList(proteins, 10));
        buckets.setVeggies(limitList(veggies, 12));
        return buckets;
    }

    private double scoreStapleCandidate(Food food, GoalRuleProfile profile, MealType mealType) {
        double penalty = 0.0;
        String gi = FoodRuleHelper.nullToDefault(food.getGiLevel(), "medium");
        double fiber = FoodRuleHelper.fiberValue(food);
        String name = FoodRuleHelper.safeName(food);

        switch (profile.getGoalType()) {
            case LOSE_FAT -> {
                penalty += FoodRuleHelper.safe(food.getCalories()) * 0.05;
                penalty -= fiber * 0.8;
            }
            case GAIN_MUSCLE -> {
                penalty -= FoodRuleHelper.safe(food.getCarb()) * 0.1;
                penalty -= fiber * 0.3;
            }
            case DIABETES_CONTROL -> {
                if ("high".equalsIgnoreCase(gi)) penalty += 80;
                else if ("medium".equalsIgnoreCase(gi)) penalty += 15;
                penalty -= fiber * 1.5;
            }
            case HYPERTENSION_CONTROL -> {
                penalty += FoodRuleHelper.decimalValue(food.getSodiumMg()) * 0.02;
                penalty -= fiber * 0.6;
            }
            case HYPERLIPIDEMIA_CONTROL -> {
                penalty += FoodRuleHelper.decimalValue(food.getSaturatedFat()) * 2.0;
                penalty -= fiber * 1.0;
            }
            case MAINTAIN -> penalty -= fiber * 0.4;
        }

        if (mealType == MealType.BREAKFAST) {
            if (name.contains("燕麦") || name.contains("全麦") || name.contains("玉米") || name.contains("红薯") || name.contains("小米粥")) {
                penalty -= 18;
            }
            if (name.contains("意面") || name.contains("藜麦")) {
                penalty += 10;
            }
        }

        if (mealType == MealType.LUNCH && name.contains("燕麦")) {
            penalty += 12;
        }

        if (mealType == MealType.DINNER) {
            penalty += FoodRuleHelper.safe(food.getCalories()) * 0.015;
            if (name.contains("燕麦")) {
                penalty += 18;
            }
        }

        return penalty;
    }

    private double scoreProteinCandidate(Food food, GoalRuleProfile profile, MealType mealType) {
        double penalty = 0.0;
        String name = FoodRuleHelper.safeName(food);

        penalty += FoodRuleHelper.decimalValue(food.getSaturatedFat())
                * (profile.getGoalType() == GoalType.HYPERLIPIDEMIA_CONTROL ? 10.0 : 2.0);
        penalty += FoodRuleHelper.decimalValue(food.getSodiumMg())
                * (profile.getGoalType() == GoalType.HYPERTENSION_CONTROL ? 0.03 : 0.005);
        penalty -= FoodRuleHelper.proteinDensity(food) * 2.0;

        if (profile.getGoalType() == GoalType.GAIN_MUSCLE) {
            penalty -= FoodRuleHelper.proteinDensity(food) * 1.2;
        }
        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            penalty += FoodRuleHelper.decimalValue(food.getSugar()) * 0.8;
        }

        if (mealType == MealType.BREAKFAST) {
            if (FoodRuleHelper.isGoodBreakfastProtein(food)) {
                penalty -= 26;
            }
            if (FoodRuleHelper.isHeavyBreakfastProtein(food)) {
                penalty += 35;
            }
            if (name.contains("毛豆")) {
                penalty += 18;
            }
        }

        if (mealType == MealType.DINNER) {
            penalty += FoodRuleHelper.decimalValue(food.getSaturatedFat()) * 1.0;
            if (name.contains("鸡蛋")) {
                penalty += 3;
            }
        }

        if (profile.getGoalType() == GoalType.HYPERTENSION_CONTROL) {
            if (name.contains("鳕鱼") || name.contains("虾仁") || name.contains("豆腐")
                    || name.contains("鸡胸") || name.contains("火鸡胸")) {
                penalty -= 8;
            }
            if (FoodRuleHelper.isNotIdealForHypertension(food)) {
                penalty += 16;
            }
        }

        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            if (name.contains("豆腐") || name.contains("鳕鱼") || name.contains("鸡胸")
                    || name.contains("虾仁") || name.contains("三文鱼")) {
                penalty -= 6;
            }
        }

        return penalty;
    }

    private double scoreVeggieCandidate(Food food, GoalRuleProfile profile, MealType mealType) {
        double penalty = 0.0;
        penalty -= FoodRuleHelper.fiberValue(food);

        if (profile.getGoalType() == GoalType.HYPERTENSION_CONTROL) {
            penalty += FoodRuleHelper.decimalValue(food.getSodiumMg()) * 0.01;
        }
        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            penalty -= FoodRuleHelper.fiberValue(food) * 0.8;
            penalty += FoodRuleHelper.decimalValue(food.getSugar()) * 0.5;
        }

        if (FoodRuleHelper.getVeggieKind(food) == com.healthdiet.recommend.enums.VeggieKind.STARCHY) {
            penalty += 16;
            if (mealType == MealType.DINNER) {
                penalty += 12;
            }
            if (profile.getGoalType() == GoalType.LOSE_FAT || profile.getGoalType() == GoalType.DIABETES_CONTROL) {
                penalty += 18;
            }
        }

        if (mealType == MealType.BREAKFAST) {
            if (FoodRuleHelper.isLightLeafyVeg(food)) {
                penalty -= 6;
            }
        }

        return penalty;
    }

    private MealSolution solveBestPortion(
            MealType mealType,
            GoalRuleProfile profile,
            MealTarget target,
            CandidateBuckets buckets,
            Set<Long> usedFoodIds
    ) {
        MealSolution best = null;

        for (Food staple : buckets.getStaples()) {
            int stapleMax = FoodRuleHelper.getStapleMaxWeight(staple, mealType, profile);

            for (Food protein : buckets.getProteins()) {
                int proteinMax = FoodRuleHelper.getProteinMaxWeight(protein, mealType, profile);

                for (Food veggie : buckets.getVeggies()) {
                    int veggieMax = FoodRuleHelper.getVeggieMaxWeight(veggie, mealType, profile);

                    for (int stapleWeight = 50; stapleWeight <= stapleMax; stapleWeight += 25) {
                        for (int proteinWeight = 50; proteinWeight <= proteinMax; proteinWeight += 10) {
                            for (int veggieWeight = 100; veggieWeight <= veggieMax; veggieWeight += 50) {

                                if (!FoodRuleHelper.isReasonableCombination(profile, mealType, staple, stapleWeight, veggie, veggieWeight)) {
                                    continue;
                                }

                                MealSolution candidate = scorer.evaluate(
                                        profile, mealType,
                                        staple, stapleWeight,
                                        protein, proteinWeight,
                                        veggie, veggieWeight,
                                        target,
                                        usedFoodIds
                                );

                                if (best == null || candidate.getScore() < best.getScore()) {
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

    private RecommendVO.DailySummary buildDailySummary(GoalRuleProfile profile, List<RecommendVO.Meal> meals) {
        double totalProtein = meals.stream().mapToDouble(m -> safe(m.getMacros().getProtein())).sum();
        double totalFat = meals.stream().mapToDouble(m -> safe(m.getMacros().getFat())).sum();
        double totalCarb = meals.stream().mapToDouble(m -> safe(m.getMacros().getCarbs())).sum();

        int actualTotalCalories = meals.stream()
                .mapToInt(m -> m.getCalories() == null ? 0 : m.getCalories())
                .sum();

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

        ds.setSummaryText(buildDailySummaryText(profile.getGoalType()));
        return ds;
    }

    private List<String> buildExtraAdvice() {
        List<String> tips = new ArrayList<>(HEALTH_TIPS);
        Collections.shuffle(tips);
        return tips.subList(0, 2);
    }

    private void addUsedFoodIds(MealBuildResult result, Set<Long> usedFoodIds) {
        if (result == null) return;
        if (result.getStapleId() != null) usedFoodIds.add(result.getStapleId());
        if (result.getProteinId() != null) usedFoodIds.add(result.getProteinId());
        if (result.getVeggieId() != null) usedFoodIds.add(result.getVeggieId());
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
            if (food == null) continue;
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

    private String mapGoal(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "lose_fat";
            case GAIN_MUSCLE -> "gain_muscle";
            case DIABETES_CONTROL -> "diabetes_control";
            case HYPERTENSION_CONTROL -> "hypertension_control";
            case HYPERLIPIDEMIA_CONTROL -> "hyperlipidemia_control";
            case MAINTAIN -> "maintain";
        };
    }

    private String buildKeyMessage(GoalType goalType, boolean punishmentMode, boolean recoveryMode) {
        StringBuilder sb = new StringBuilder();

        if (punishmentMode) {
            sb.append("⚠️ 监测到近期热量明显超标，今日已启动热量管控。");
        } else if (recoveryMode) {
            sb.append("❤️ 监测到近期摄入偏低，今日已适当提高能量供给。");
        }

        String goalMsg = switch (goalType) {
            case LOSE_FAT -> "今日重点：提高蛋白占比，控制总热量和晚餐主食。";
            case GAIN_MUSCLE -> "今日重点：保证碳水与优质蛋白，支持增肌恢复。";
            case DIABETES_CONTROL -> "今日重点：优先低GI主食，减少高糖与精制碳水。";
            case HYPERTENSION_CONTROL -> "今日重点：控制钠摄入，避免高盐加工食品。";
            case HYPERLIPIDEMIA_CONTROL -> "今日重点：减少饱和脂肪，提高膳食纤维摄入。";
            case MAINTAIN -> "今日重点：保持营养均衡，三餐结构稳定。";
        };

        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append(goalMsg);
        return sb.toString();
    }

    private String buildDailySummaryText(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "今日执行重点：优先高蛋白、高纤维、避免晚餐过量主食。";
            case GAIN_MUSCLE -> "今日执行重点：保证训练日供能与优质蛋白输入。";
            case DIABETES_CONTROL -> "今日执行重点：控制高GI和高糖食物，稳定碳水结构。";
            case HYPERTENSION_CONTROL -> "今日执行重点：严格控制钠摄入并保持饮食清淡。";
            case HYPERLIPIDEMIA_CONTROL -> "今日执行重点：减少饱和脂肪并提高高纤维食物比例。";
            case MAINTAIN -> "今日执行重点：平衡三餐结构，避免过度偏科。";
        };
    }

    private String buildMealAdvice(MealType mealType, GoalType goalType) {
        if (mealType == MealType.BREAKFAST) {
            return switch (goalType) {
                case LOSE_FAT -> "早餐保持高蛋白但避免过量主食，帮助控制全天食欲。";
                case GAIN_MUSCLE -> "早餐建议包含主食和蛋白，为训练和恢复做准备。";
                case DIABETES_CONTROL -> "早餐优先低GI主食搭配蛋白，避免高糖饮料。";
                case HYPERTENSION_CONTROL -> "早餐尽量清淡，减少咸味加工食品。";
                case HYPERLIPIDEMIA_CONTROL -> "早餐避免高脂加工肉，优先低脂蛋白。";
                case MAINTAIN -> "早餐尽量包含主食 + 蛋白，帮助启动全天代谢。";
            };
        }

        if (mealType == MealType.LUNCH) {
            return switch (goalType) {
                case LOSE_FAT -> "午餐保证蛋白和蔬菜充足，主食不过量。";
                case GAIN_MUSCLE -> "午餐是增肌期核心供能餐，注意碳水和蛋白都要到位。";
                case DIABETES_CONTROL -> "午餐优先低GI主食和高纤维蔬菜，稳定餐后血糖。";
                case HYPERTENSION_CONTROL -> "午餐尽量减少酱汁和加工肉，优先清淡烹调。";
                case HYPERLIPIDEMIA_CONTROL -> "午餐避免油炸和高脂肉类，优先瘦肉或鱼类。";
                case MAINTAIN -> "午餐是全天最核心的一餐，注意结构均衡。";
            };
        }

        return switch (goalType) {
            case LOSE_FAT -> "晚餐控制主食不过量，保持蛋白和蔬菜充足。";
            case GAIN_MUSCLE -> "晚餐可保留适量主食和蛋白，支持恢复但避免暴食。";
            case DIABETES_CONTROL -> "晚餐继续控制高GI主食和甜食摄入。";
            case HYPERTENSION_CONTROL -> "晚餐避免高盐菜品和腌制食品。";
            case HYPERLIPIDEMIA_CONTROL -> "晚餐优先低脂蛋白和高纤维蔬菜。";
            case MAINTAIN -> "晚餐宜清淡，避免过量进食。";
        };
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private <T> List<T> limitList(List<T> list, int maxSize) {
        if (list.size() <= maxSize) {
            return list;
        }
        return new ArrayList<>(list.subList(0, maxSize));
    }
}