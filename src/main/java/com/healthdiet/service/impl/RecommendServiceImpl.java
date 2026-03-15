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
import com.healthdiet.recommend.enums.VeggieKind;
import com.healthdiet.recommend.model.CandidateBuckets;
import com.healthdiet.recommend.model.DayPlanCandidate;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.MealSolution;
import com.healthdiet.recommend.model.MealTarget;
import com.healthdiet.recommend.model.NutritionSnapshot;
import com.healthdiet.recommend.model.SnackCandidate;
import com.healthdiet.recommend.rule.DayPlanScorer;
import com.healthdiet.recommend.rule.FoodRuleHelper;
import com.healthdiet.recommend.rule.GoalRuleProfile;
import com.healthdiet.recommend.rule.RecommendationScorer;
import com.healthdiet.recommend.rule.SnackPlanner;
import com.healthdiet.service.DailyTargetService;
import com.healthdiet.service.IRecommendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendServiceImpl extends ServiceImpl<RecommendMapper, Recommendation> implements IRecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);
    private static final long FOOD_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private static final List<String> HEALTH_TIPS = Arrays.asList(
            "进食放慢一些，更有利于稳定饱腹感。",
            "吃饭时尽量减少分心，更容易控制摄入量。",
            "规律睡眠有助于食欲控制和身体恢复。",
            "饭后适当散步，有助于消化和血糖稳定。",
            "尽量避免太晚进食，减轻夜间负担。",
            "少吃高加工食品，更利于长期饮食管理。",
            "全天保持稳定饮水。"
    );

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FoodMapper foodMapper;

    @Autowired
    private DietRecordMapper dietRecordMapper;

    @Autowired
    private DailyTargetService dailyTargetService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecommendationScorer scorer = new RecommendationScorer();
    private final DayPlanScorer dayPlanScorer = new DayPlanScorer();
    private final SnackPlanner snackPlanner = new SnackPlanner();
    private final Object foodCacheLock = new Object();

    private volatile List<Food> allFoodsCache = List.of();
    private volatile long foodCacheExpiresAt = 0L;

    @Override
    public Recommendation getTodayRecommend(Long userId) {
        LocalDate today = LocalDate.now();
        Recommendation existing = findByUserAndDate(userId, today);
        if (existing != null) {
            return existing;
        }

        refreshFoodCache();
        User user = requireValidUser(userId);
        RecommendVO vo = generateSmartPlan(user, today);

        Recommendation recommendation = new Recommendation();
        recommendation.setUserId(userId);
        recommendation.setDate(today);
        recommendation.setResultJson(writeAsJson(vo));
        this.save(recommendation);
        return recommendation;
    }

    @Override
    public Recommendation refreshTodayRecommend(Long userId) {
        refreshFoodCache();

        LocalDate today = LocalDate.now();
        Recommendation existing = findByUserAndDate(userId, today);
        User user = requireValidUser(userId);

        RecommendVO existingVo = parseRecommend(existing);
        Map<MealType, RecommendVO.Meal> lockedMeals = buildLockedMeals(userId, today, existingVo);
        RecommendVO refreshedVo = generateSmartPlan(user, today, lockedMeals, true);

        Recommendation target = existing == null ? new Recommendation() : existing;
        target.setUserId(userId);
        target.setDate(today);
        target.setResultJson(writeAsJson(refreshedVo));

        if (existing == null) {
            this.save(target);
        } else {
            this.updateById(target);
        }
        return target;
    }

    private User requireValidUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getHeight() == null || user.getWeight() == null || user.getAge() == null || user.getGender() == null) {
            throw new RuntimeException("请先完善身高、体重、年龄和性别信息");
        }
        return user;
    }

    private RecommendVO parseRecommend(Recommendation recommendation) {
        if (recommendation == null || recommendation.getResultJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(recommendation.getResultJson(), RecommendVO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String writeAsJson(RecommendVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("推荐结果序列化失败", e);
        }
    }

    private void refreshFoodCache() {
        long now = System.currentTimeMillis();
        if (foodCacheExpiresAt > now && !allFoodsCache.isEmpty()) {
            return;
        }

        synchronized (foodCacheLock) {
            long recheckNow = System.currentTimeMillis();
            if (foodCacheExpiresAt > recheckNow && !allFoodsCache.isEmpty()) {
                return;
            }
            allFoodsCache = Collections.unmodifiableList(new ArrayList<>(foodMapper.selectList(null)));
            foodCacheExpiresAt = recheckNow + FOOD_CACHE_TTL_MILLIS;
        }
    }

    private RecommendVO generateSmartPlan(User user, LocalDate date) {
        return generateSmartPlan(user, date, Map.of(), false);
    }

    private RecommendVO generateSmartPlan(User user, LocalDate date, Map<MealType, RecommendVO.Meal> lockedMeals) {
        return generateSmartPlan(user, date, lockedMeals, true);
    }

    private RecommendVO generateSmartPlan(User user, LocalDate date, Map<MealType, RecommendVO.Meal> lockedMeals, boolean refreshed) {
        long startedAt = System.nanoTime();
        GoalType goalType = GoalType.fromCode(user.getTarget());
        GoalRuleProfile profile = GoalRuleProfile.of(goalType);
        DailyTargetProfile targetProfile = dailyTargetService.buildDailyTarget(user, goalType, date);
        Map<MealType, Integer> mealTargetCalories = buildMealTargetCalories(targetProfile.getTargetCalories(), lockedMeals);
        int breakfastTargetCalories = mealTargetCalories.getOrDefault(MealType.BREAKFAST, targetCaloriesForMeal(MealType.BREAKFAST, targetProfile.getTargetCalories()));
        int lunchTargetCalories = mealTargetCalories.getOrDefault(MealType.LUNCH, targetCaloriesForMeal(MealType.LUNCH, targetProfile.getTargetCalories()));
        int dinnerTargetCalories = mealTargetCalories.getOrDefault(MealType.DINNER, targetCaloriesForMeal(MealType.DINNER, targetProfile.getTargetCalories()));
        long targetStageMs = elapsedMillis(startedAt);

        long candidateStageStartedAt = System.nanoTime();
        CompletableFuture<List<MealSolution>> breakfastFuture = generateMealCandidatesAsync(
                MealType.BREAKFAST, breakfastTargetCalories, profile, lockedMeals);
        CompletableFuture<List<MealSolution>> lunchFuture = generateMealCandidatesAsync(
                MealType.LUNCH, lunchTargetCalories, profile, lockedMeals);
        CompletableFuture<List<MealSolution>> dinnerFuture = generateMealCandidatesAsync(
                MealType.DINNER, dinnerTargetCalories, profile, lockedMeals);

        List<MealSolution> breakfastCandidates = joinMealCandidates(breakfastFuture, MealType.BREAKFAST);
        List<MealSolution> lunchCandidates = joinMealCandidates(lunchFuture, MealType.LUNCH);
        List<MealSolution> dinnerCandidates = joinMealCandidates(dinnerFuture, MealType.DINNER);
        long candidateStageMs = elapsedMillis(candidateStageStartedAt);

        long dayPlanStageStartedAt = System.nanoTime();
        DayPlanCandidate bestPlan = selectDailyBestPlan(breakfastCandidates, lunchCandidates, dinnerCandidates, targetProfile, goalType);
        long dayPlanStageMs = elapsedMillis(dayPlanStageStartedAt);

        long assembleStageStartedAt = System.nanoTime();
        List<MealBuildResult> mealResults = new ArrayList<>();
        MealBuildResult breakfastResult = lockedMeals.containsKey(MealType.BREAKFAST)
                ? lockedMealResult(lockedMeals.get(MealType.BREAKFAST))
                : buildMeal(bestPlan.getBreakfast(), MealType.BREAKFAST, profile, breakfastTargetCalories, false, null);
        mealResults.add(breakfastResult);
        MealBuildResult lunchResult = lockedMeals.containsKey(MealType.LUNCH)
                ? lockedMealResult(lockedMeals.get(MealType.LUNCH))
                : buildMeal(bestPlan.getLunch(), MealType.LUNCH, profile, lunchTargetCalories, false, null);
        mealResults.add(lunchResult);
        mealResults.add(lockedMeals.containsKey(MealType.DINNER)
                ? lockedMealResult(lockedMeals.get(MealType.DINNER))
                : buildMeal(bestPlan.getDinner(), MealType.DINNER, profile, dinnerTargetCalories, false, lunchResult.reference()));
        if (lockedMeals.containsKey(MealType.SNACK)) {
            mealResults.add(lockedMealResult(lockedMeals.get(MealType.SNACK)));
        }

        List<RecommendVO.Meal> meals = mealResults.stream().map(MealBuildResult::meal).collect(Collectors.toCollection(ArrayList::new));
        NutritionSnapshot baseSnapshot = buildSnapshotFromResults(mealResults);
        SnackCandidate snackCandidate = lockedMeals.containsKey(MealType.SNACK)
                ? null
                : snackPlanner.planSnack(allFoodsCache, goalType, targetProfile, baseSnapshot);
        if (snackCandidate != null) {
            meals.add(buildSnack(snackCandidate, goalType, targetProfile, baseSnapshot));
        }

        RecommendVO vo = new RecommendVO();
        vo.setDate(date.toString());
        vo.setSummary(buildSummary(user, goalType, targetProfile));
        vo.setMeals(meals);
        vo.setDailySummary(buildDailySummary(goalType, meals, targetProfile, lockedMeals, snackCandidate, lockedMeals.containsKey(MealType.SNACK)));
        vo.setExtraAdvice(buildExtraAdvice());
        vo.setRefreshInfo(buildRefreshInfo(refreshed, lockedMeals));
        long assembleStageMs = elapsedMillis(assembleStageStartedAt);
        long totalStageMs = elapsedMillis(startedAt);
        logRecommendCost(user.getId(), date, lockedMeals, targetStageMs, candidateStageMs, dayPlanStageMs, assembleStageMs, totalStageMs);
        return vo;
    }

    private CompletableFuture<List<MealSolution>> generateMealCandidatesAsync(
            MealType mealType,
            int mealCalories,
            GoalRuleProfile profile,
            Map<MealType, RecommendVO.Meal> lockedMeals
    ) {
        if (lockedMeals.containsKey(mealType)) {
            return CompletableFuture.completedFuture(
                    List.of(toLockedMealSolution(lockedMeals.get(mealType), mealType))
            );
        }
        return CompletableFuture.supplyAsync(() -> generateMealCandidates(mealType, mealCalories, profile));
    }

    private List<MealSolution> joinMealCandidates(CompletableFuture<List<MealSolution>> future, MealType mealType) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException(mealType.getCode() + " 候选生成失败", cause);
        }
    }

    private RecommendVO.Summary buildSummary(User user, GoalType goalType, DailyTargetProfile targetProfile) {
        double bmi = calculateBMI(user);
        RecommendVO.Summary summary = new RecommendVO.Summary();
        summary.setBmi(bmi);
        summary.setStatus(getBMIStatus(bmi));
        summary.setCaloriesTarget(targetProfile.getTargetCalories());
        summary.setActivityFactor(round2(targetProfile.getActivityFactor()));
        summary.setTdee(round1(targetProfile.getTdee()));
        summary.setGoal(mapGoal(goalType));
        summary.setKeyMessage(buildKeyMessage(goalType, targetProfile));
        summary.setReasons(buildSummaryReasons(goalType, targetProfile));
        return summary;
    }

    private List<String> buildSummaryReasons(GoalType goalType, DailyTargetProfile targetProfile) {
        List<String> reasons = new ArrayList<>();
        reasons.add("今日目标热量由基础代谢、活动系数和当前目标共同计算得出。");
        reasons.add("当前活动系数为 " + round2(targetProfile.getActivityFactor()) + "，估算 TDEE 约为 " + Math.round(targetProfile.getTdee()) + " 千卡。");
        if (targetProfile.getHistoryCalorieAdjustment() != 0) {
            reasons.add("近 7 天摄入触发了平滑热量修正，调整幅度为 " + targetProfile.getHistoryCalorieAdjustment() + " 千卡。");
        } else {
            reasons.add("近 7 天摄入较稳定，因此没有额外热量修正。");
        }
        if (targetProfile.hasTag("HISTORY_PROTEIN_UP")) {
            reasons.add("近期蛋白质摄入偏低，因此今天会提高蛋白优先级。");
        }
        if (targetProfile.hasTag("HISTORY_CARB_DOWN")) {
            reasons.add("近期碳水占比偏高，因此今天会适当收紧主食比例。");
        }
        if (targetProfile.hasTag("HISTORY_FAT_DOWN")) {
            reasons.add("近期脂肪占比偏高，因此今天会更严格限制高脂食物。");
        }
        reasons.add(goalSpecificReason(goalType));
        return reasons;
    }

    private List<MealSolution> generateMealCandidates(MealType mealType, int mealCalories, GoalRuleProfile profile) {
        MealTarget target = profile.buildMealTarget(mealType, mealCalories);
        CandidateBuckets buckets = buildFoodCandidates(mealType, profile);
        if (buckets.getStaples().isEmpty() || buckets.getProteins().isEmpty() || buckets.getVeggies().isEmpty()) {
            return List.of();
        }
        return solveTopMealPortions(mealType, profile, target, buckets, 18);
    }

    private DayPlanCandidate selectDailyBestPlan(
            List<MealSolution> breakfastCandidates,
            List<MealSolution> lunchCandidates,
            List<MealSolution> dinnerCandidates,
            DailyTargetProfile targetProfile,
            GoalType goalType
    ) {
        if (breakfastCandidates.isEmpty() || lunchCandidates.isEmpty() || dinnerCandidates.isEmpty()) {
            return buildFallbackPlan(breakfastCandidates, lunchCandidates, dinnerCandidates);
        }

        List<DayPlanCandidate> topBreakfastLunchPairs = buildTopDayPlanPairs(
                breakfastCandidates,
                lunchCandidates,
                targetProfile,
                goalType,
                36
        );
        if (topBreakfastLunchPairs.isEmpty()) {
            topBreakfastLunchPairs = buildTopDayPlanPairsWithoutPatternFilter(
                    breakfastCandidates,
                    lunchCandidates,
                    targetProfile,
                    goalType,
                    36
            );
        }

        DayPlanCandidate bestPlan = null;
        for (DayPlanCandidate partialPlan : topBreakfastLunchPairs) {
            for (MealSolution dinner : dinnerCandidates) {
                if (!isAllowedDailyStaplePattern(partialPlan.getBreakfast(), partialPlan.getLunch(), dinner)) {
                    continue;
                }
                if (!isAllowedLunchDinnerVariety(partialPlan.getLunch(), dinner)) {
                    continue;
                }
                DayPlanCandidate candidate = new DayPlanCandidate();
                candidate.setBreakfast(partialPlan.getBreakfast());
                candidate.setLunch(partialPlan.getLunch());
                candidate.setDinner(dinner);
                candidate.setScore(dayPlanScorer.evaluate(targetProfile, goalType, candidate));
                if (bestPlan == null || candidate.getScore() < bestPlan.getScore()) {
                    bestPlan = candidate;
                }
            }
        }
        if (bestPlan != null) {
            return bestPlan;
        }

        for (DayPlanCandidate partialPlan : topBreakfastLunchPairs) {
            for (MealSolution dinner : dinnerCandidates) {
                DayPlanCandidate candidate = new DayPlanCandidate();
                candidate.setBreakfast(partialPlan.getBreakfast());
                candidate.setLunch(partialPlan.getLunch());
                candidate.setDinner(dinner);
                candidate.setScore(dayPlanScorer.evaluate(targetProfile, goalType, candidate));
                if (bestPlan == null || candidate.getScore() < bestPlan.getScore()) {
                    bestPlan = candidate;
                }
            }
        }
        return bestPlan == null ? buildFallbackPlan(breakfastCandidates, lunchCandidates, dinnerCandidates) : bestPlan;
    }

    private List<DayPlanCandidate> buildTopDayPlanPairs(
            List<MealSolution> firstMealCandidates,
            List<MealSolution> secondMealCandidates,
            DailyTargetProfile targetProfile,
            GoalType goalType,
            int keepTopK
    ) {
        List<DayPlanCandidate> bestPairs = new ArrayList<>();
        for (MealSolution firstMeal : firstMealCandidates) {
            for (MealSolution secondMeal : secondMealCandidates) {
                if (!isAllowedDailyStaplePattern(firstMeal, secondMeal, null)) {
                    continue;
                }
                DayPlanCandidate candidate = new DayPlanCandidate();
                candidate.setBreakfast(firstMeal);
                candidate.setLunch(secondMeal);
                candidate.setDinner(emptyMealSolution());
                candidate.setScore(dayPlanScorer.evaluate(targetProfile, goalType, candidate));
                insertDayPlanCandidate(bestPairs, candidate, keepTopK);
            }
        }
        return bestPairs;
    }

    private List<DayPlanCandidate> buildTopDayPlanPairsWithoutPatternFilter(
            List<MealSolution> firstMealCandidates,
            List<MealSolution> secondMealCandidates,
            DailyTargetProfile targetProfile,
            GoalType goalType,
            int keepTopK
    ) {
        List<DayPlanCandidate> bestPairs = new ArrayList<>();
        for (MealSolution firstMeal : firstMealCandidates) {
            for (MealSolution secondMeal : secondMealCandidates) {
                DayPlanCandidate candidate = new DayPlanCandidate();
                candidate.setBreakfast(firstMeal);
                candidate.setLunch(secondMeal);
                candidate.setDinner(emptyMealSolution());
                candidate.setScore(dayPlanScorer.evaluate(targetProfile, goalType, candidate));
                insertDayPlanCandidate(bestPairs, candidate, keepTopK);
            }
        }
        return bestPairs;
    }

    private boolean isAllowedDailyStaplePattern(MealSolution breakfast, MealSolution lunch, MealSolution dinner) {
        int breakfastStyleCount = 0;
        if (isBreakfastStyleStaple(breakfast)) {
            breakfastStyleCount++;
        }
        if (isBreakfastStyleStaple(lunch)) {
            breakfastStyleCount++;
        }
        if (isBreakfastStyleStaple(dinner)) {
            breakfastStyleCount++;
        }
        if (breakfastStyleCount > 1) {
            return false;
        }
        return !hasRepeatedStapleFood(breakfast, lunch, dinner);
    }

    private boolean isAllowedLunchDinnerVariety(MealSolution lunch, MealSolution dinner) {
        if (!isTrackableMeal(lunch) || !isTrackableMeal(dinner)) {
            return true;
        }

        if (sameFoodId(lunch.getStaple(), dinner.getStaple()) || sameFoodId(lunch.getProtein(), dinner.getProtein())) {
            return false;
        }

        String lunchStapleGroup = FoodRuleHelper.stapleGroup(lunch.getStaple());
        String dinnerStapleGroup = FoodRuleHelper.stapleGroup(dinner.getStaple());
        String lunchProteinGroup = FoodRuleHelper.proteinSourceGroup(lunch.getProtein());
        String dinnerProteinGroup = FoodRuleHelper.proteinSourceGroup(dinner.getProtein());
        String lunchVegGroup = FoodRuleHelper.vegGroup(lunch.getVeggie());
        String dinnerVegGroup = FoodRuleHelper.vegGroup(dinner.getVeggie());

        if (mealPatternSignature(lunch).equals(mealPatternSignature(dinner))) {
            return false;
        }
        if (!lunchStapleGroup.isEmpty() && lunchStapleGroup.equals(dinnerStapleGroup)
                && !lunchProteinGroup.isEmpty() && lunchProteinGroup.equals(dinnerProteinGroup)) {
            return false;
        }
        return !lunchProteinGroup.isEmpty() && !lunchProteinGroup.equals(dinnerProteinGroup)
                || lunchVegGroup.isEmpty()
                || !lunchVegGroup.equals(dinnerVegGroup);
    }

    private boolean isBreakfastStyleStaple(MealSolution meal) {
        if (meal == null || meal.getStaple() == null) {
            return false;
        }
        return isBreakfastStyleStaple(meal.getStaple());
    }

    private boolean isBreakfastStyleStaple(Food staple) {
        return FoodRuleHelper.isBreakfastStyleStaple(staple);
    }

    private boolean hasRepeatedStapleFood(MealSolution breakfast, MealSolution lunch, MealSolution dinner) {
        Set<Long> stapleIds = new HashSet<>();
        return hasRepeatedStapleFood(stapleIds, breakfast)
                || hasRepeatedStapleFood(stapleIds, lunch)
                || hasRepeatedStapleFood(stapleIds, dinner);
    }

    private boolean hasRepeatedStapleFood(Set<Long> stapleIds, MealSolution meal) {
        if (meal == null || meal.getStaple() == null || meal.getStaple().getId() == null) {
            return false;
        }
        Long stapleId = meal.getStaple().getId();
        if (stapleId <= 0) {
            return false;
        }
        return !stapleIds.add(stapleId);
    }

    private boolean sameFoodId(Food first, Food second) {
        if (first == null || second == null || first.getId() == null || second.getId() == null) {
            return false;
        }
        return first.getId().equals(second.getId()) && first.getId() > 0;
    }

    private boolean isTrackableMeal(MealSolution meal) {
        return meal != null
                && meal.getStaple() != null && meal.getStaple().getId() != null && meal.getStaple().getId() > 0
                && meal.getProtein() != null && meal.getProtein().getId() != null && meal.getProtein().getId() > 0
                && meal.getVeggie() != null && meal.getVeggie().getId() != null && meal.getVeggie().getId() > 0;
    }

    private String mealPatternSignature(MealSolution meal) {
        return FoodRuleHelper.stapleGroup(meal.getStaple()) + "|"
                + FoodRuleHelper.proteinSourceGroup(meal.getProtein()) + "|"
                + FoodRuleHelper.vegGroup(meal.getVeggie());
    }

    private DayPlanCandidate buildFallbackPlan(
            List<MealSolution> breakfastCandidates,
            List<MealSolution> lunchCandidates,
            List<MealSolution> dinnerCandidates
    ) {
        DayPlanCandidate fallback = new DayPlanCandidate();
        fallback.setBreakfast(breakfastCandidates.isEmpty() ? emptyMealSolution() : breakfastCandidates.get(0));
        fallback.setLunch(lunchCandidates.isEmpty() ? emptyMealSolution() : lunchCandidates.get(0));
        fallback.setDinner(dinnerCandidates.isEmpty() ? emptyMealSolution() : dinnerCandidates.get(0));
        fallback.setScore(Double.MAX_VALUE);
        return fallback;
    }

    private Recommendation findByUserAndDate(Long userId, LocalDate date) {
        QueryWrapper<Recommendation> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", date);
        return this.getOne(query);
    }

    private Map<MealType, RecommendVO.Meal> buildLockedMeals(Long userId, LocalDate date, RecommendVO existingVo) {
        QueryWrapper<DietRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("date", date);
        List<DietRecord> records = dietRecordMapper.selectList(query);
        if (records.isEmpty()) {
            return Map.of();
        }

        Map<Integer, List<DietRecord>> mealRecordMap = records.stream()
                .filter(record -> record.getMealType() != null && record.getMealType() >= 1 && record.getMealType() <= 4)
                .collect(Collectors.groupingBy(DietRecord::getMealType));
        if (mealRecordMap.isEmpty()) {
            return Map.of();
        }

        Set<Long> foodIds = records.stream().map(DietRecord::getFoodId).collect(Collectors.toSet());
        Map<Long, Food> foodMap = foodIds.isEmpty() ? Map.of() : foodMapper.selectBatchIds(foodIds).stream()
                .collect(Collectors.toMap(Food::getId, food -> food));

        Map<MealType, RecommendVO.Meal> lockedMeals = new LinkedHashMap<>();
        mealRecordMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
            MealType mealType = switch (entry.getKey()) {
                case 1 -> MealType.BREAKFAST;
                case 2 -> MealType.LUNCH;
                case 3 -> MealType.DINNER;
                case 4 -> MealType.SNACK;
                default -> null;
            };
            if (mealType == null) {
                return;
            }

            RecommendVO.Meal meal = buildLockedMealFromRecords(entry.getValue(), mealType, foodMap);
            if (meal == null && existingVo != null && existingVo.getMeals() != null) {
                meal = existingVo.getMeals().stream()
                        .filter(item -> mealType.getCode().equals(item.getType()))
                        .findFirst()
                        .orElse(null);
                if (meal != null) {
                    meal.setLocked(true);
                    if (meal.getReasons() == null || meal.getReasons().isEmpty()) {
                        meal.setReasons(defaultLockedReasons(mealType));
                    }
                }
            }
            if (meal != null) {
                lockedMeals.put(mealType, meal);
            }
        });
        return lockedMeals;
    }

    private RecommendVO.Meal buildLockedMealFromRecords(List<DietRecord> records, MealType mealType, Map<Long, Food> foodMap) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        double calories = 0.0;
        double protein = 0.0;
        double fat = 0.0;
        double carbs = 0.0;
        List<String> items = new ArrayList<>();

        for (DietRecord record : records) {
            Food food = foodMap.get(record.getFoodId());
            if (food == null) {
                continue;
            }
            int quantity = record.getQuantity() == null ? 0 : record.getQuantity();
            int grams = quantity * 100;
            items.add(food.getName() + " " + grams + "g");
            calories += safe(food.getCalories()) * quantity;
            protein += safe(food.getProtein()) * quantity;
            fat += safe(food.getFat()) * quantity;
            carbs += safe(food.getCarb()) * quantity;
        }

        if (items.isEmpty()) {
            return null;
        }

        return new RecommendVO.Meal(
                mealType.getCode(),
                mealType.getTitle(),
                String.join(" + ", items),
                (int) Math.round(calories),
                new RecommendVO.Macros(round1(protein), round1(fat), round1(carbs)),
                "该餐已根据今日实际饮食记录锁定保留。",
                defaultLockedReasons(mealType),
                true
        );
    }

    private List<String> defaultLockedReasons(MealType mealType) {
        return List.of(
                mealType.getTitle() + "今天已经有饮食记录。",
                "刷新时会保留已吃餐次，只重算后续未吃餐次。"
        );
    }

    private MealSolution emptyMealSolution() {
        MealSolution solution = new MealSolution();
        Food emptyFood = new Food();
        emptyFood.setId(-1L);
        emptyFood.setName("暂无可用食物");
        solution.setStaple(emptyFood);
        solution.setProtein(emptyFood);
        solution.setVeggie(emptyFood);
        solution.setActualCalories(0);
        return solution;
    }

    private MealSolution toLockedMealSolution(RecommendVO.Meal meal, MealType mealType) {
        MealSolution solution = new MealSolution();
        solution.setStaple(pseudoLockedFood(-10L - mealType.ordinal() * 3L, meal.getMenu() + "-locked-staple"));
        solution.setProtein(pseudoLockedFood(-11L - mealType.ordinal() * 3L, meal.getMenu() + "-locked-protein"));
        solution.setVeggie(pseudoLockedFood(-12L - mealType.ordinal() * 3L, meal.getMenu() + "-locked-veggie"));
        solution.setStapleWeight(0);
        solution.setProteinWeight(0);
        solution.setVeggieWeight(0);
        solution.setActualCalories(meal.getCalories() == null ? 0 : meal.getCalories());
        solution.setProteinG(meal.getMacros() == null || meal.getMacros().getProtein() == null ? 0.0 : meal.getMacros().getProtein());
        solution.setFatG(meal.getMacros() == null || meal.getMacros().getFat() == null ? 0.0 : meal.getMacros().getFat());
        solution.setCarbG(meal.getMacros() == null || meal.getMacros().getCarbs() == null ? 0.0 : meal.getMacros().getCarbs());
        solution.setScore(0.0);
        return solution;
    }

    private Food pseudoLockedFood(Long id, String name) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        return food;
    }

    private MealBuildResult buildMeal(MealSolution solution, MealType mealType, GoalRuleProfile profile, int targetCalories, boolean locked, MealReference referenceMeal) {
        MealReference safeReference = referenceMeal == null ? MealReference.empty() : referenceMeal;
        MealComposition composition = composeMeal(solution, mealType, profile, targetCalories, safeReference);
        RecommendVO.Meal meal = new RecommendVO.Meal(
                mealType.getCode(),
                mealType.getTitle(),
                composition.menu(),
                composition.calories(),
                new RecommendVO.Macros(round1(composition.protein()), round1(composition.fat()), round1(composition.carbs())),
                buildMealAdvice(mealType, profile.getGoalType()),
                buildMealReasonsV3(solution, mealType, profile.getGoalType(), locked, composition),
                locked
        );
        return new MealBuildResult(meal, composition.fiber(), composition.toReference());
    }

    private MealBuildResult lockedMealResult(RecommendVO.Meal meal) {
        return new MealBuildResult(meal, 0.0, MealReference.empty());
    }

    private NutritionSnapshot buildSnapshotFromResults(List<MealBuildResult> mealResults) {
        NutritionSnapshot snapshot = new NutritionSnapshot();
        for (MealBuildResult result : mealResults) {
            RecommendVO.Meal meal = result.meal();
            snapshot.setCalories(snapshot.getCalories() + (meal.getCalories() == null ? 0 : meal.getCalories()));
            RecommendVO.Macros macros = meal.getMacros() == null ? new RecommendVO.Macros(0.0, 0.0, 0.0) : meal.getMacros();
            snapshot.setProteinG(snapshot.getProteinG() + safe(macros.getProtein()));
            snapshot.setFatG(snapshot.getFatG() + safe(macros.getFat()));
            snapshot.setCarbG(snapshot.getCarbG() + safe(macros.getCarbs()));
            snapshot.setFiberG(snapshot.getFiberG() + result.fiberG());
        }
        return snapshot;
    }

    private MealComposition composeMeal(MealSolution solution, MealType mealType, GoalRuleProfile profile, int targetCalories, MealReference referenceMeal) {
        MealTarget mealTarget = profile.buildMealTarget(mealType, targetCalories);
        MealComposition composition = new MealComposition();
        Set<Long> usedIds = new LinkedHashSet<>();

        if (mealType == MealType.BREAKFAST) {
            Food breakfastStaple = resolveBreakfastStaple(solution.getStaple(), profile.getGoalType());
            addMealItem(composition, breakfastStaple, normalizeWeight(solution.getStapleWeight(), 70, 120), "staple");

            Food breakfastProtein = resolveBreakfastProtein(solution.getProtein(), breakfastStaple, usedIds, profile.getGoalType());
            if (breakfastProtein != null) {
                addMealItem(composition, breakfastProtein, defaultBreakfastProteinWeight(breakfastProtein, solution.getProteinWeight()), "protein");
            }

            if (composition.itemCount() < 3 && shouldAddBreakfastExtra(composition, mealTarget)) {
                Food extra = chooseBreakfastExtra(usedIds, profile.getGoalType(), composition, mealTarget);
                if (extra != null) {
                    addMealItem(composition, extra, defaultBreakfastExtraWeight(extra), "extra");
                }
            }
            return composition;
        }

        Food staple = resolveMainMealStaple(solution.getStaple(), mealType, profile, referenceMeal);
        addMealItem(composition, staple, normalizeWeight(solution.getStapleWeight(), 80, 150), "staple");
        addMealItem(composition, resolveMainMealProtein(solution.getProtein(), mealType, profile, referenceMeal), normalizeWeight(solution.getProteinWeight(), 90, 170), "protein");
        addMealItem(composition, resolveMainMealVeggie(solution.getVeggie(), mealType, profile, referenceMeal), normalizeWeight(solution.getVeggieWeight(), 120, 220), "veg");

        if (shouldAddSecondaryProtein(mealType, profile.getGoalType(), composition, mealTarget)) {
            Food extraProtein = chooseAdditionalProtein(solution, mealType, profile, usedIds, referenceMeal);
            if (extraProtein != null) {
                addMealItem(composition, extraProtein, defaultAdditionalProteinWeight(extraProtein, profile.getGoalType()), "protein");
            }
        }
        if (shouldAddSecondaryVeggie(mealType, composition, mealTarget)) {
            Food extraVeggie = chooseAdditionalVeggie(solution, mealType, profile, usedIds, referenceMeal);
            if (extraVeggie != null) {
                addMealItem(composition, extraVeggie, defaultAdditionalVeggieWeight(extraVeggie, mealType), "veg");
            }
        }
        return composition;
    }

    private void addMealItem(MealComposition composition, Food food, int weightG, String role) {
        if (food == null || food.getId() == null || weightG <= 0 || composition.itemCount() >= composition.maxItems()) {
            return;
        }
        if ("protein".equals(role) && composition.proteinCount() >= 2) {
            return;
        }
        if ("veg".equals(role) && composition.vegCount() >= 2) {
            return;
        }
        if ("staple".equals(role) && composition.hasStaple()) {
            return;
        }
        if (composition.hasFood(food.getId())) {
            return;
        }
        composition.add(food, weightG, role);
    }

    private Food resolveBreakfastStaple(Food currentStaple, GoalType goalType) {
        if (isPreferredBreakfastStaple(currentStaple)) {
            return currentStaple;
        }
        return allFoodsCache.stream()
                .filter(food -> "staple".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> FoodRuleHelper.isMealFriendly(food, MealType.BREAKFAST))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, goalType))
                .filter(this::isPreferredBreakfastStaple)
                .sorted(Comparator.comparingInt(this::breakfastStaplePriority).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()))
                .findFirst()
                .orElse(currentStaple);
    }

    /*
    private boolean isPreferredBreakfastStaple(Food food) {
        if (food == null || !"staple".equals(FoodRuleHelper.normalizeCategory(food))) {
            return false;
        }
        String group = FoodRuleHelper.stapleGroup(food);
        String name = FoodRuleHelper.safeName(food);
        return FoodRuleHelper.isBreakfastStyleStaple(food)
                || "corn".equals(group)
                || "potato".equals(group)
                || name.contains("粥")
                || name.contains("玉米");
    }

    private int breakfastStaplePriority(Food food) {
        String name = FoodRuleHelper.safeName(food);
        String group = FoodRuleHelper.stapleGroup(food);
        if (name.contains("豆浆") || name.contains("粥")) {
            return 0;
        }
        if ("corn".equals(group) || "potato".equals(group)) {
            return 1;
        }
        if ("oat_wheat".equals(group)) {
            return 2;
        }
        if ("bakery".equals(group)) {
            return 3;
        }
        return 4;
    }
    */

    private boolean isPreferredBreakfastStaple(Food food) {
        if (food == null || !"staple".equals(FoodRuleHelper.normalizeCategory(food))) {
            return false;
        }
        String group = FoodRuleHelper.stapleGroup(food);
        String name = FoodRuleHelper.safeName(food);
        return FoodRuleHelper.isBreakfastStyleStaple(food)
                || "corn".equals(group)
                || "potato".equals(group)
                || name.contains("粥")
                || name.contains("玉米");
    }

    private int breakfastStaplePriority(Food food) {
        String name = FoodRuleHelper.safeName(food);
        String group = FoodRuleHelper.stapleGroup(food);
        if (name.contains("豆浆") || name.contains("粥")) {
            return 0;
        }
        if ("corn".equals(group) || "potato".equals(group)) {
            return 1;
        }
        if ("oat_wheat".equals(group)) {
            return 2;
        }
        if ("bakery".equals(group)) {
            return 3;
        }
        return 4;
    }

    private Food resolveBreakfastProtein(Food currentProtein, Food staple, Set<Long> usedIds, GoalType goalType) {
        if (currentProtein != null && FoodRuleHelper.isGoodBreakfastProtein(currentProtein)) {
            if (staple != null && staple.getId() != null) {
                usedIds.add(staple.getId());
            }
            return currentProtein;
        }
        if (staple != null) {
            usedIds.add(staple.getId());
        }
        return allFoodsCache.stream()
                .filter(food -> FoodRuleHelper.isMealFriendly(food, MealType.BREAKFAST))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, goalType))
                .filter(FoodRuleHelper::isGoodBreakfastProtein)
                .filter(food -> !usedIds.contains(food.getId()))
                .sorted(Comparator.comparingDouble(FoodRuleHelper::proteinDensity).reversed())
                .findFirst()
                .orElse(currentProtein);
    }

    private boolean shouldAddBreakfastExtra(MealComposition composition, MealTarget mealTarget) {
        return composition.calories() < mealTarget.getMealCalories() - 120
                || composition.protein() < mealTarget.getProteinG() - 8;
    }

    private Food chooseBreakfastExtra(Set<Long> usedIds, GoalType goalType, MealComposition composition, MealTarget mealTarget) {
        return allFoodsCache.stream()
                .filter(food -> FoodRuleHelper.isMealFriendly(food, MealType.BREAKFAST))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, goalType))
                .filter(food -> !usedIds.contains(food.getId()))
                .filter(food -> !composition.hasFood(food.getId()))
                .filter(food -> isPreferredBreakfastStaple(food) || FoodRuleHelper.isGoodBreakfastProtein(food))
                .sorted(Comparator
                        .comparingInt((Food food) -> breakfastExtraPriority(food, composition, mealTarget))
                        .thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private int breakfastExtraPriority(Food food, MealComposition composition, MealTarget mealTarget) {
        boolean proteinNeeded = composition.protein() < mealTarget.getProteinG() - 8;
        if (proteinNeeded && FoodRuleHelper.isGoodBreakfastProtein(food)) {
            return 0;
        }
        if (isPreferredBreakfastStaple(food)) {
            return 1;
        }
        return 2;
    }

    private Food resolveMainMealStaple(Food currentStaple, MealType mealType, GoalRuleProfile profile, MealReference referenceMeal) {
        if (currentStaple == null) {
            return pickPreferredMainStaple(null, mealType, profile, referenceMeal);
        }
        Food preferredRice = pickPreferredRiceStaple(mealType, profile, referenceMeal);
        if (conflictsWithReferenceStaple(currentStaple, referenceMeal)) {
            Food replacement = pickPreferredMainStaple(currentStaple, mealType, profile, referenceMeal);
            return replacement == null ? currentStaple : replacement;
        }
        if ("rice".equals(FoodRuleHelper.stapleGroup(currentStaple))) {
            return currentStaple;
        }
        if (FoodRuleHelper.isRestrictedMainMealStaple(currentStaple)) {
            Food replacement = preferredRice != null ? preferredRice : pickPreferredMainStaple(currentStaple, mealType, profile, referenceMeal);
            return replacement == null ? currentStaple : replacement;
        }
        if (preferredRice == null) {
            return currentStaple;
        }

        double currentScore = scoreStapleCandidate(currentStaple, profile, mealType);
        double riceScore = scoreStapleCandidate(preferredRice, profile, mealType) - 18;
        return riceScore <= currentScore + 12 ? preferredRice : currentStaple;
    }

    private Food pickPreferredRiceStaple(MealType mealType, GoalRuleProfile profile, MealReference referenceMeal) {
        return allFoodsCache.stream()
                .filter(food -> "staple".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> "rice".equals(FoodRuleHelper.stapleGroup(food)))
                .filter(food -> !FoodRuleHelper.isRestrictedMainMealStaple(food))
                .filter(food -> !conflictsWithReferenceStaple(food, referenceMeal))
                .sorted(Comparator.comparing((Food food) -> scoreStapleCandidate(food, profile, mealType)).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private Food pickPreferredMainStaple(Food fallback, MealType mealType, GoalRuleProfile profile, MealReference referenceMeal) {
        return allFoodsCache.stream()
                .filter(food -> "staple".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> !FoodRuleHelper.isRestrictedMainMealStaple(food))
                .filter(food -> !conflictsWithReferenceStaple(food, referenceMeal))
                .sorted(Comparator
                        .comparingInt((Food food) -> mainMealStaplePriority(food))
                        .thenComparing((Food food) -> scoreStapleCandidate(food, profile, mealType)))
                .findFirst()
                .orElse(fallback);
    }

    private int mainMealStaplePriority(Food food) {
        return switch (FoodRuleHelper.stapleGroup(food)) {
            case "rice" -> 0;
            case "potato" -> 1;
            case "corn" -> 2;
            case "noodle" -> 3;
            case "whole_grain" -> 4;
            default -> 5;
        };
    }

    private Food resolveMainMealProtein(Food currentProtein, MealType mealType, GoalRuleProfile profile, MealReference referenceMeal) {
        if (!conflictsWithReferenceProtein(currentProtein, referenceMeal)) {
            return currentProtein;
        }
        return allFoodsCache.stream()
                .filter(food -> {
                    String category = FoodRuleHelper.normalizeCategory(food);
                    return "protein".equals(category) || "dairy".equals(category);
                })
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> !conflictsWithReferenceProtein(food, referenceMeal))
                .sorted(Comparator.comparing((Food food) -> scoreProteinCandidate(food, profile, mealType)).thenComparing(FoodRuleHelper::proteinDensity, Comparator.reverseOrder()))
                .findFirst()
                .orElse(currentProtein);
    }

    private Food resolveMainMealVeggie(Food currentVeggie, MealType mealType, GoalRuleProfile profile, MealReference referenceMeal) {
        if (!conflictsWithReferenceVeggie(currentVeggie, referenceMeal)) {
            return currentVeggie;
        }
        return allFoodsCache.stream()
                .filter(food -> "vegetable".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> !conflictsWithReferenceVeggie(food, referenceMeal))
                .sorted(Comparator.comparing((Food food) -> scoreVeggieCandidate(food, profile, mealType)).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()))
                .findFirst()
                .orElse(currentVeggie);
    }

    private boolean shouldAddSecondaryProtein(MealType mealType, GoalType goalType, MealComposition composition, MealTarget mealTarget) {
        if (mealType == MealType.BREAKFAST || composition.proteinCount() >= 2) {
            return false;
        }
        return goalType == GoalType.GAIN_MUSCLE
                || composition.protein() < mealTarget.getProteinG() - 10
                || composition.calories() < mealTarget.getMealCalories() - 180;
    }

    private boolean shouldAddSecondaryVeggie(MealType mealType, MealComposition composition, MealTarget mealTarget) {
        if (mealType == MealType.BREAKFAST || composition.vegCount() >= 2) {
            return false;
        }
        return composition.fiber() < 6.0
                || composition.calories() < mealTarget.getMealCalories() - 120;
    }

    private Food chooseAdditionalProtein(MealSolution solution, MealType mealType, GoalRuleProfile profile, Set<Long> usedIds, MealReference referenceMeal) {
        usedIds.addAll(solutionFoodIds(solution));
        usedIds.addAll(referenceMeal.foodIds());
        String currentGroup = FoodRuleHelper.proteinSourceGroup(solution.getProtein());
        return allFoodsCache.stream()
                .filter(food -> {
                    String category = FoodRuleHelper.normalizeCategory(food);
                    return "protein".equals(category) || "dairy".equals(category);
                })
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> !usedIds.contains(food.getId()))
                .filter(food -> !"dairy".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> !FoodRuleHelper.proteinSourceGroup(food).equals(currentGroup))
                .filter(food -> !referenceMeal.proteinGroups().contains(FoodRuleHelper.proteinSourceGroup(food)))
                .sorted(Comparator.comparing((Food food) -> scoreProteinCandidate(food, profile, mealType)).thenComparing(FoodRuleHelper::proteinDensity, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private Food chooseAdditionalVeggie(MealSolution solution, MealType mealType, GoalRuleProfile profile, Set<Long> usedIds, MealReference referenceMeal) {
        usedIds.addAll(solutionFoodIds(solution));
        usedIds.addAll(referenceMeal.foodIds());
        String currentGroup = FoodRuleHelper.vegGroup(solution.getVeggie());
        return allFoodsCache.stream()
                .filter(food -> "vegetable".equals(FoodRuleHelper.normalizeCategory(food)))
                .filter(food -> FoodRuleHelper.isMealFriendly(food, mealType))
                .filter(food -> !FoodRuleHelper.shouldExclude(food, profile.getGoalType()))
                .filter(food -> !usedIds.contains(food.getId()))
                .filter(food -> !FoodRuleHelper.vegGroup(food).equals(currentGroup))
                .filter(food -> !referenceMeal.vegGroups().contains(FoodRuleHelper.vegGroup(food)))
                .sorted(Comparator.comparing((Food food) -> scoreVeggieCandidate(food, profile, mealType)).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private Set<Long> solutionFoodIds(MealSolution solution) {
        Set<Long> ids = new LinkedHashSet<>();
        if (solution == null) {
            return ids;
        }
        if (solution.getStaple() != null && solution.getStaple().getId() != null) {
            ids.add(solution.getStaple().getId());
        }
        if (solution.getProtein() != null && solution.getProtein().getId() != null) {
            ids.add(solution.getProtein().getId());
        }
        if (solution.getVeggie() != null && solution.getVeggie().getId() != null) {
            ids.add(solution.getVeggie().getId());
        }
        return ids;
    }

    private boolean conflictsWithReferenceStaple(Food food, MealReference referenceMeal) {
        if (food == null || referenceMeal == null || referenceMeal.isEmpty()) {
            return false;
        }
        if (food.getId() != null && food.getId().equals(referenceMeal.stapleId()) && food.getId() > 0) {
            return true;
        }
        String foodGroup = FoodRuleHelper.stapleGroup(food);
        return !foodGroup.isEmpty() && foodGroup.equals(referenceMeal.stapleGroup());
    }

    private boolean conflictsWithReferenceProtein(Food food, MealReference referenceMeal) {
        if (food == null || referenceMeal == null || referenceMeal.isEmpty()) {
            return false;
        }
        if (food.getId() != null && referenceMeal.foodIds().contains(food.getId()) && food.getId() > 0) {
            return true;
        }
        String group = FoodRuleHelper.proteinSourceGroup(food);
        return !group.isEmpty() && referenceMeal.proteinGroups().contains(group);
    }

    private boolean conflictsWithReferenceVeggie(Food food, MealReference referenceMeal) {
        if (food == null || referenceMeal == null || referenceMeal.isEmpty()) {
            return false;
        }
        if (food.getId() != null && referenceMeal.foodIds().contains(food.getId()) && food.getId() > 0) {
            return true;
        }
        String group = FoodRuleHelper.vegGroup(food);
        return !group.isEmpty() && referenceMeal.vegGroups().contains(group);
    }

    private int normalizeWeight(int rawWeight, int minWeight, int maxWeight) {
        if (rawWeight <= 0) {
            return minWeight;
        }
        return Math.max(minWeight, Math.min(maxWeight, rawWeight));
    }

    private int defaultBreakfastProteinWeight(Food food, int rawWeight) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if ("dairy".equals(category)) {
            return normalizeWeight(rawWeight, 180, 250);
        }
        return normalizeWeight(rawWeight, 60, 120);
    }

    private int defaultBreakfastExtraWeight(Food food) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if ("dairy".equals(category)) {
            return 200;
        }
        if ("protein".equals(category)) {
            return 80;
        }
        return 80;
    }

    private int defaultAdditionalProteinWeight(Food food, GoalType goalType) {
        if ("dairy".equals(FoodRuleHelper.normalizeCategory(food))) {
            return goalType == GoalType.GAIN_MUSCLE ? 200 : 150;
        }
        return goalType == GoalType.GAIN_MUSCLE ? 90 : 70;
    }

    private int defaultAdditionalVeggieWeight(Food food, MealType mealType) {
        return mealType == MealType.DINNER ? 120 : 150;
    }

    private RecommendVO.Meal buildSnack(SnackCandidate snackCandidate, GoalType goalType, DailyTargetProfile targetProfile, NutritionSnapshot baseSnapshot) {
        MealComposition composition = new MealComposition(2);
        addMealItem(composition, snackCandidate.getFood(), snackCandidate.getWeightG(), "snack");

        double remainingCalories = targetProfile.getTargetCalories() - baseSnapshot.getCalories() - composition.calories();
        double remainingProtein = targetProfile.getTargetProteinG() - baseSnapshot.getProteinG() - composition.protein();
        double remainingFiber = Math.max(0.0, 25.0 - baseSnapshot.getFiberG() - composition.fiber());

        Food secondarySnack = chooseSecondarySnackFood(snackCandidate.getFood(), goalType, remainingCalories, remainingProtein, remainingFiber);
        if (secondarySnack != null) {
            addMealItem(composition, secondarySnack, defaultSecondarySnackWeight(secondarySnack), "snack");
        }

        List<String> reasons = new ArrayList<>(buildSnackReasonsV2(snackCandidate, goalType));
        if (composition.itemCount() > 1) {
            reasons.add("这次加餐拆成了两种食物，兼顾能量补充和营养平衡。");
        }

        return new RecommendVO.Meal(
                MealType.SNACK.getCode(),
                MealType.SNACK.getTitle(),
                composition.menu(),
                composition.calories(),
                new RecommendVO.Macros(round1(composition.protein()), round1(composition.fat()), round1(composition.carbs())),
                buildMealAdvice(MealType.SNACK, goalType),
                reasons,
                false
        );
    }

    private Food chooseSecondarySnackFood(Food primary, GoalType goalType, double calorieGap, double proteinGap, double fiberGap) {
        if (calorieGap < 80 && proteinGap < 8 && fiberGap < 4) {
            return null;
        }
        String primaryCategory = FoodRuleHelper.normalizeCategory(primary);
        return allFoodsCache.stream()
                .filter(food -> !food.getId().equals(primary.getId()))
                .filter(food -> isSecondarySnackFriendly(food, goalType, primaryCategory, proteinGap, fiberGap))
                .sorted(Comparator
                        .comparingInt((Food food) -> secondarySnackPriority(food, proteinGap, fiberGap))
                        .thenComparing(FoodRuleHelper::proteinDensity, Comparator.reverseOrder()))
                .findFirst()
                .orElse(null);
    }

    private boolean isSecondarySnackFriendly(Food food, GoalType goalType, String primaryCategory, double proteinGap, double fiberGap) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if (!List.of("fruit", "nut", "protein", "dairy").contains(category)) {
            return false;
        }
        if (FoodRuleHelper.shouldExclude(food, goalType)) {
            return false;
        }
        if (category.equals(primaryCategory) && proteinGap < 12 && fiberGap < 6) {
            return false;
        }
        if (goalType == GoalType.DIABETES_CONTROL && "high".equalsIgnoreCase(FoodRuleHelper.nullToDefault(food.getGiLevel(), "medium"))) {
            return false;
        }
        return true;
    }

    private int secondarySnackPriority(Food food, double proteinGap, double fiberGap) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if (proteinGap >= 12 && ("protein".equals(category) || "dairy".equals(category))) {
            return 0;
        }
        if (fiberGap >= 6 && ("fruit".equals(category) || "nut".equals(category))) {
            return 1;
        }
        if ("dairy".equals(category) || "fruit".equals(category)) {
            return 2;
        }
        return 3;
    }

    private int defaultSecondarySnackWeight(Food food) {
        return switch (FoodRuleHelper.normalizeCategory(food)) {
            case "nut" -> 20;
            case "protein" -> 60;
            case "dairy" -> 150;
            default -> 100;
        };
    }

    private List<String> buildSnackReasons(SnackCandidate snackCandidate, GoalType goalType) {
        List<String> reasons = new ArrayList<>();
        reasons.add("系统是在评估全天剩余缺口后，才决定是否增加加餐。");
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_ENERGY_GAP")) {
            reasons.add("全天热量仍低于目标，因此补充少量加餐。");
        }
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_PROTEIN_GAP")) {
            reasons.add("全天蛋白质仍有缺口，因此优先补充蛋白。");
        }
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_FIBER_GAP")) {
            reasons.add("全天膳食纤维仍有缺口，因此补充高纤维食物。");
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            reasons.add("加餐优先选择低 GI 或低糖食物。");
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            reasons.add("加餐用于支持全天恢复和额外能量补充。");
        }
        return reasons;
    }

    private List<String> buildMealReasons(MealSolution solution, MealType mealType, GoalType goalType, boolean locked) {
        if (locked) {
            return defaultLockedReasons(mealType);
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("这餐不是单独求最优，而是放在全天组合里一起优化得到的。");
        reasons.add("本餐核心搭配为：" + solution.getStaple().getName() + "、" + solution.getProtein().getName() + "、" + solution.getVeggie().getName() + "。");
        if (mealType == MealType.DINNER && (goalType == GoalType.LOSE_FAT || goalType == GoalType.DIABETES_CONTROL)) {
            reasons.add("晚餐主食控制会更严格，用来收住全天碳水预算。");
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            reasons.add("本餐优先考虑低 GI 和较高纤维结构。");
        }
        if (goalType == GoalType.HYPERTENSION_CONTROL) {
            reasons.add("本餐会优先避开高钠食物。");
        }
        if (goalType == GoalType.HYPERLIPIDEMIA_CONTROL) {
            reasons.add("本餐会更严格限制高饱和脂肪食物。");
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            reasons.add("本餐优先兼顾蛋白供给和恢复支持。");
        }
        if (FoodRuleHelper.fiberValue(solution.getVeggie()) >= 2.5 || FoodRuleHelper.fiberValue(solution.getStaple()) >= 2.5) {
            reasons.add("这餐能为全天提供较好的膳食纤维。");
        }
        return reasons;
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

        staples.sort(Comparator.comparing((Food f) -> scoreStapleCandidate(f, profile, mealType)).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()));
        staples = filterStaplesForMealType(staples, mealType);
        proteins.sort(Comparator.comparing((Food f) -> scoreProteinCandidate(f, profile, mealType)).thenComparing(FoodRuleHelper::proteinDensity, Comparator.reverseOrder()));
        veggies.sort(Comparator.comparing((Food f) -> scoreVeggieCandidate(f, profile, mealType)).thenComparing(FoodRuleHelper::fiberValue, Comparator.reverseOrder()));

        CandidateBuckets buckets = new CandidateBuckets();
        buckets.setStaples(diversifyCandidates(staples, 18, 6, FoodRuleHelper::stapleGroup));
        buckets.setProteins(diversifyCandidates(proteins, 15, 6, FoodRuleHelper::proteinSourceGroup));
        buckets.setVeggies(diversifyCandidates(veggies, 15, 6, FoodRuleHelper::vegGroup));
        return buckets;
    }

    private List<Food> filterStaplesForMealType(List<Food> rankedStaples, MealType mealType) {
        if (mealType == MealType.BREAKFAST || rankedStaples.isEmpty()) {
            return rankedStaples;
        }

        List<Food> nonBreakfastStyle = rankedStaples.stream()
                .filter(food -> !isLunchDinnerRestrictedStaple(food))
                .collect(Collectors.toCollection(ArrayList::new));
        if (nonBreakfastStyle.isEmpty()) {
            return rankedStaples;
        }

        if (nonBreakfastStyle.size() >= 6) {
            return nonBreakfastStyle;
        }

        long fallbackSlots = 1;
        rankedStaples.stream()
                .filter(this::isLunchDinnerRestrictedStaple)
                .limit(fallbackSlots)
                .forEach(nonBreakfastStyle::add);
        return nonBreakfastStyle;
    }

    private boolean isLunchDinnerRestrictedStaple(Food staple) {
        if (staple == null) {
            return false;
        }
        String group = FoodRuleHelper.stapleGroup(staple);
        String name = FoodRuleHelper.safeName(staple).toLowerCase();
        String gi = FoodRuleHelper.nullToDefault(staple.getGiLevel(), "medium");
        double sodium = FoodRuleHelper.decimalValue(staple.getSodiumMg());
        double fiber = FoodRuleHelper.fiberValue(staple);

        if ("oat_wheat".equals(group) || "bakery".equals(group)) {
            return true;
        }
        if ("high".equalsIgnoreCase(gi) && sodium >= 300) {
            return true;
        }
        return fiber < 3 && containsStapleKeyword(name, "bagel", "cracker", "bread", "贝果", "饼干", "面包", "苏打");
    }

    private boolean containsStapleKeyword(String value, String... keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && value.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildSnackReasonsV2(SnackCandidate snackCandidate, GoalType goalType) {
        List<String> reasons = new ArrayList<>();
        reasons.add("这次加餐是因为三餐之后仍存在明确营养缺口。");
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_ENERGY_GAP")) {
            reasons.add("全天热量仍偏低，因此补充了小份能量。");
        }
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_PROTEIN_GAP")) {
            reasons.add("全天蛋白质仍不足，因此优先补充蛋白来源。");
        }
        if (snackCandidate.getReasonTags().contains("SNACK_FOR_FIBER_GAP")) {
            reasons.add("全天膳食纤维仍有缺口，因此补充了高纤维食物。");
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            reasons.add("加餐优先选择低 GI、低糖食物。");
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            reasons.add("这次加餐主要用于支持恢复和补足蛋白。");
        }
        return reasons;
    }

    private List<String> buildMealReasonsV2(MealSolution solution, MealType mealType, GoalType goalType, boolean locked) {
        if (locked) {
            return defaultLockedReasons(mealType);
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("本餐主食、蛋白和蔬菜按当天目标做了平衡。");
        reasons.add("本餐搭配为：" + solution.getStaple().getName() + "、" + solution.getProtein().getName() + "、" + solution.getVeggie().getName() + "。");
        if (mealType == MealType.BREAKFAST) {
            reasons.add("早餐会更强调易消化的主食和稳定蛋白。");
        }
        if (mealType == MealType.LUNCH) {
            reasons.add("午餐承担白天主要能量，因此会保证主食和蛋白都到位。");
        }
        if (mealType == MealType.DINNER && (goalType == GoalType.LOSE_FAT || goalType == GoalType.DIABETES_CONTROL)) {
            reasons.add("晚餐主食份量会更谨慎，避免夜间摄入过重。");
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            reasons.add("本餐优先选择低 GI、较高纤维的搭配。");
        }
        if (goalType == GoalType.HYPERTENSION_CONTROL) {
            reasons.add("本餐会优先避开高钠食物。");
        }
        if (goalType == GoalType.HYPERLIPIDEMIA_CONTROL) {
            reasons.add("本餐会更严格限制高饱和脂肪食物。");
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            reasons.add("本餐会优先保证蛋白质，帮助训练后恢复。");
        }
        if (FoodRuleHelper.fiberValue(solution.getVeggie()) >= 2.5 || FoodRuleHelper.fiberValue(solution.getStaple()) >= 2.5) {
            reasons.add("这餐还能提供较好的膳食纤维。");
        }
        return reasons;
    }

    private List<String> buildMealReasonsV3(MealSolution solution, MealType mealType, GoalType goalType, boolean locked, MealComposition composition) {
        if (locked) {
            return defaultLockedReasons(mealType);
        }
        List<String> reasons = new ArrayList<>(buildMealReasonsV2(solution, mealType, goalType, false));
        reasons.removeIf(reason -> reason.startsWith("本餐搭配为："));
        reasons.add(1, "本餐组合为：" + composition.menu() + "。");
        if (mealType == MealType.BREAKFAST) {
            reasons.add("早餐控制在 3 种以内，尽量更接近日常早餐结构。");
        }
        if (mealType == MealType.LUNCH || mealType == MealType.DINNER) {
            reasons.add("午晚餐按 1 份主食搭配 1 到 2 份蛋白和 1 到 2 份蔬菜来组织。");
            if (composition.menu().contains("米饭")) {
                reasons.add("这餐优先保留了米饭主食，更贴近日常中式饮食习惯。");
            }
        }
        return limitList(reasons, 5);
    }

    private List<String> buildDailyReasonsV2(
            GoalType goalType,
            DailyTargetProfile targetProfile,
            RecommendVO.DailySummary summary,
            Map<MealType, RecommendVO.Meal> lockedMeals,
            SnackCandidate snackCandidate,
            boolean lockedSnackPresent
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("今日总热量约为 " + summary.getTotalCalories() + " 千卡，目标为 " + targetProfile.getTargetCalories() + " 千卡。");
        reasons.add("全天蛋白质目标约 " + round1(targetProfile.getTargetProteinG()) + "g，当前方案约 " + round1(summary.getTotalMacros().getProtein()) + "g。");
        if (!lockedMeals.isEmpty()) {
            reasons.add("已记录的餐次会保留，系统只调整未吃的部分。");
        }
        if (snackCandidate == null && !lockedSnackPresent) {
            reasons.add("当前三餐已经基本覆盖今日需求，因此未额外安排加餐。");
        } else {
            reasons.add(lockedSnackPresent
                    ? "今日已记录的加餐会保留，刷新时不会被覆盖。"
                    : "由于三餐后仍有缺口，因此补充了小份加餐。");
        }
        return reasons;
    }

    private String buildDailySummaryTextV2(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "执行重点：控制总热量，晚餐不过量。";
            case GAIN_MUSCLE -> "执行重点：保证主食和蛋白，支持恢复与增肌。";
            case DIABETES_CONTROL -> "执行重点：优先低 GI 主食，控制高糖高钠加工食物。";
            case HYPERTENSION_CONTROL -> "执行重点：优先清淡食物，控制钠摄入。";
            case HYPERLIPIDEMIA_CONTROL -> "执行重点：减少高脂食物，增加纤维。";
            case MAINTAIN -> "执行重点：三餐均衡，避免单一化。";
        };
    }

    private List<Food> diversifyCandidates(
            List<Food> rankedFoods,
            int keepTopN,
            int keepTopCore,
            Function<Food, String> groupFunction
    ) {
        if (rankedFoods.size() <= keepTopN) {
            return rankedFoods;
        }

        List<Food> selected = new ArrayList<>(rankedFoods.subList(0, Math.min(keepTopCore, rankedFoods.size())));
        Set<Long> selectedIds = selected.stream().map(Food::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> coveredGroups = selected.stream().map(groupFunction).collect(Collectors.toCollection(LinkedHashSet::new));

        for (Food food : rankedFoods) {
            if (selected.size() >= keepTopN) {
                break;
            }
            if (selectedIds.contains(food.getId())) {
                continue;
            }

            String group = groupFunction.apply(food);
            if (group != null && !group.isBlank() && !coveredGroups.contains(group)) {
                selected.add(food);
                selectedIds.add(food.getId());
                coveredGroups.add(group);
            }
        }

        for (Food food : rankedFoods) {
            if (selected.size() >= keepTopN) {
                break;
            }
            if (selectedIds.add(food.getId())) {
                selected.add(food);
            }
        }
        return selected;
    }

    private double scoreStapleCandidate(Food food, GoalRuleProfile profile, MealType mealType) {
        double penalty = 0.0;
        String gi = FoodRuleHelper.nullToDefault(food.getGiLevel(), "medium");
        double fiber = FoodRuleHelper.fiberValue(food);
        String name = FoodRuleHelper.safeName(food);
        String stapleGroup = FoodRuleHelper.stapleGroup(food);

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
                if ("high".equalsIgnoreCase(gi)) {
                    penalty += 80;
                } else if ("medium".equalsIgnoreCase(gi)) {
                    penalty += 15;
                }
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
            if (name.contains("燕麦")) {
                penalty -= 10;
            }
            if (name.contains("全麦") || name.contains("玉米") || name.contains("红薯") || name.contains("小米")) {
                penalty -= 14;
            }
            if (name.contains("意面") || name.contains("藜麦")) {
                penalty += 10;
            }
        }
        if (mealType == MealType.LUNCH && name.contains("燕麦")) {
            penalty += 36;
        }
        if (mealType == MealType.DINNER) {
            penalty += FoodRuleHelper.safe(food.getCalories()) * 0.015;
            if (name.contains("燕麦")) {
                penalty += 60;
            }
        }
        if (mealType == MealType.BREAKFAST) {
            if ("corn".equals(stapleGroup) || "potato".equals(stapleGroup) || "whole_grain".equals(stapleGroup)) {
                penalty -= 14;
            }
            if ("noodle".equals(stapleGroup)) {
                penalty += 10;
            }
        }
        if (mealType == MealType.LUNCH && "oat_wheat".equals(stapleGroup)) {
            penalty += switch (profile.getGoalType()) {
                case DIABETES_CONTROL -> 56;
                case GAIN_MUSCLE -> 48;
                default -> 36;
            };
        }
        if (mealType == MealType.DINNER) {
            if ("oat_wheat".equals(stapleGroup)) {
                penalty += switch (profile.getGoalType()) {
                    case DIABETES_CONTROL -> 90;
                    case GAIN_MUSCLE -> 72;
                    default -> 60;
                };
            }
            if (profile.getGoalType() == GoalType.DIABETES_CONTROL && "rice".equals(stapleGroup) && "low".equalsIgnoreCase(gi)) {
                penalty -= 8;
            }
        }
        if ("bakery".equals(stapleGroup)) {
            penalty += mealType == MealType.BREAKFAST ? 22 : 85;
        }
        if (mealType != MealType.BREAKFAST && FoodRuleHelper.isRestrictedMainMealStaple(food)) {
            penalty += switch (mealType) {
                case LUNCH -> switch (profile.getGoalType()) {
                    case GAIN_MUSCLE -> 72;
                    case DIABETES_CONTROL -> 84;
                    default -> 60;
                };
                case DINNER -> switch (profile.getGoalType()) {
                    case GAIN_MUSCLE -> 96;
                    case DIABETES_CONTROL -> 120;
                    default -> 84;
                };
                default -> 0;
            };
        }
        if (mealType != MealType.BREAKFAST && "high".equalsIgnoreCase(gi)) {
            penalty += 30;
        }
        if (mealType != MealType.BREAKFAST && FoodRuleHelper.decimalValue(food.getSodiumMg()) >= 300) {
            penalty += 24;
        }
        if (profile.getGoalType() == GoalType.GAIN_MUSCLE && mealType != MealType.BREAKFAST) {
            if ("rice".equals(stapleGroup) || "potato".equals(stapleGroup) || "corn".equals(stapleGroup) || "noodle".equals(stapleGroup)) {
                penalty -= 10;
            }
        }
        if ((mealType == MealType.LUNCH || mealType == MealType.DINNER) && "rice".equals(stapleGroup)) {
            penalty -= profile.getGoalType() == GoalType.GAIN_MUSCLE ? 22 : 16;
        }
        return penalty;
    }

    private double scoreProteinCandidate(Food food, GoalRuleProfile profile, MealType mealType) {
        double penalty = 0.0;
        String name = FoodRuleHelper.safeName(food);
        penalty += FoodRuleHelper.decimalValue(food.getSaturatedFat()) * (profile.getGoalType() == GoalType.HYPERLIPIDEMIA_CONTROL ? 10.0 : 2.0);
        penalty += FoodRuleHelper.decimalValue(food.getSodiumMg()) * (profile.getGoalType() == GoalType.HYPERTENSION_CONTROL ? 0.03 : 0.005);
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
            if (name.contains("鳕鱼") || name.contains("虾仁") || name.contains("豆腐") || name.contains("鸡胸") || name.contains("火鸡胸")) {
                penalty -= 8;
            }
            if (FoodRuleHelper.isNotIdealForHypertension(food)) {
                penalty += 16;
            }
        }
        if (profile.getGoalType() == GoalType.DIABETES_CONTROL) {
            if (name.contains("豆腐") || name.contains("鳕鱼") || name.contains("鸡胸") || name.contains("虾仁") || name.contains("三文鱼")) {
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
        if (FoodRuleHelper.getVeggieKind(food) == VeggieKind.STARCHY) {
            penalty += 16;
            if (mealType == MealType.DINNER) {
                penalty += 12;
            }
            if (profile.getGoalType() == GoalType.LOSE_FAT || profile.getGoalType() == GoalType.DIABETES_CONTROL) {
                penalty += 18;
            }
        }
        if (mealType == MealType.BREAKFAST && FoodRuleHelper.isLightLeafyVeg(food)) {
            penalty -= 6;
        }
        return penalty;
    }

    private List<MealSolution> solveTopMealPortions(MealType mealType, GoalRuleProfile profile, MealTarget target, CandidateBuckets buckets, int keepTopN) {
        int poolSize = Math.max(keepTopN * 4, 48);
        int coarsePoolSize = Math.max(keepTopN * 2, 24);
        int mealCaloriesUpperBound = mealCaloriesUpperBound(mealType, target);
        List<MealSolution> coarseSolutions = enumerateMealSolutions(
                mealType,
                profile,
                target,
                buckets,
                coarsePoolSize,
                mealCaloriesUpperBound,
                stapleCoarseStep(mealType),
                proteinCoarseStep(mealType),
                veggieCoarseStep(mealType)
        );
        if (coarseSolutions.isEmpty()) {
            return coarseSolutions;
        }

        List<MealSolution> refinedSolutions = refineMealSolutions(
                mealType,
                profile,
                target,
                coarseSolutions,
                poolSize,
                mealCaloriesUpperBound
        );
        return diversifyMealSolutions(refinedSolutions.isEmpty() ? coarseSolutions : refinedSolutions, keepTopN);
    }

    private List<MealSolution> enumerateMealSolutions(
            MealType mealType,
            GoalRuleProfile profile,
            MealTarget target,
            CandidateBuckets buckets,
            int poolSize,
            int mealCaloriesUpperBound,
            int stapleStep,
            int proteinStep,
            int veggieStep
    ) {
        List<MealSolution> bestSolutions = new ArrayList<>();
        Set<Long> noUsedFoodIds = new HashSet<>();

        for (Food staple : buckets.getStaples()) {
            int stapleMax = FoodRuleHelper.getStapleMaxWeight(staple, mealType, profile);
            double stapleCaloriesPerG = FoodRuleHelper.safe(staple.getCalories()) / 100.0;
            for (Food protein : buckets.getProteins()) {
                int proteinMax = FoodRuleHelper.getProteinMaxWeight(protein, mealType, profile);
                double proteinCaloriesPerG = FoodRuleHelper.safe(protein.getCalories()) / 100.0;
                for (Food veggie : buckets.getVeggies()) {
                    int veggieMax = FoodRuleHelper.getVeggieMaxWeight(veggie, mealType, profile);
                    double veggieCaloriesPerG = FoodRuleHelper.safe(veggie.getCalories()) / 100.0;
                    double minProteinCalories = proteinCaloriesPerG * 50;
                    double minVeggieCalories = veggieCaloriesPerG * 100;
                    for (int stapleWeight = 50; stapleWeight <= stapleMax; stapleWeight += stapleStep) {
                        double stapleCalories = stapleCaloriesPerG * stapleWeight;
                        if (stapleCalories + minProteinCalories + minVeggieCalories > mealCaloriesUpperBound) {
                            break;
                        }
                        for (int proteinWeight = 50; proteinWeight <= proteinMax; proteinWeight += proteinStep) {
                            double stapleProteinCalories = stapleCalories + proteinCaloriesPerG * proteinWeight;
                            if (stapleProteinCalories + minVeggieCalories > mealCaloriesUpperBound) {
                                break;
                            }
                            for (int veggieWeight = 100; veggieWeight <= veggieMax; veggieWeight += veggieStep) {
                                if (stapleProteinCalories + veggieCaloriesPerG * veggieWeight > mealCaloriesUpperBound) {
                                    break;
                                }
                                if (!FoodRuleHelper.isReasonableCombination(profile, mealType, staple, stapleWeight, veggie, veggieWeight)) {
                                    continue;
                                }
                                MealSolution candidate = scorer.evaluate(profile, mealType, staple, stapleWeight, protein, proteinWeight, veggie, veggieWeight, target, noUsedFoodIds);
                                insertCandidate(bestSolutions, candidate, poolSize);
                            }
                        }
                    }
                }
            }
        }
        return bestSolutions;
    }

    private List<MealSolution> refineMealSolutions(
            MealType mealType,
            GoalRuleProfile profile,
            MealTarget target,
            List<MealSolution> coarseSolutions,
            int poolSize,
            int mealCaloriesUpperBound
    ) {
        List<MealSolution> refinedSolutions = new ArrayList<>();
        Set<Long> noUsedFoodIds = new HashSet<>();
        int seedLimit = Math.min(coarseSolutions.size(), Math.max(18, poolSize / 2));

        for (int index = 0; index < seedLimit; index++) {
            MealSolution seed = coarseSolutions.get(index);
            Food staple = seed.getStaple();
            Food protein = seed.getProtein();
            Food veggie = seed.getVeggie();

            int stapleMax = FoodRuleHelper.getStapleMaxWeight(staple, mealType, profile);
            int proteinMax = FoodRuleHelper.getProteinMaxWeight(protein, mealType, profile);
            int veggieMax = FoodRuleHelper.getVeggieMaxWeight(veggie, mealType, profile);

            int stapleStart = Math.max(50, seed.getStapleWeight() - 25);
            int stapleEnd = Math.min(stapleMax, seed.getStapleWeight() + 25);
            int proteinStart = Math.max(50, seed.getProteinWeight() - 20);
            int proteinEnd = Math.min(proteinMax, seed.getProteinWeight() + 20);
            int veggieStart = Math.max(100, seed.getVeggieWeight() - 50);
            int veggieEnd = Math.min(veggieMax, seed.getVeggieWeight() + 50);

            double stapleCaloriesPerG = FoodRuleHelper.safe(staple.getCalories()) / 100.0;
            double proteinCaloriesPerG = FoodRuleHelper.safe(protein.getCalories()) / 100.0;
            double veggieCaloriesPerG = FoodRuleHelper.safe(veggie.getCalories()) / 100.0;

            for (int stapleWeight = stapleStart; stapleWeight <= stapleEnd; stapleWeight += 25) {
                double stapleCalories = stapleCaloriesPerG * stapleWeight;
                for (int proteinWeight = proteinStart; proteinWeight <= proteinEnd; proteinWeight += 10) {
                    double stapleProteinCalories = stapleCalories + proteinCaloriesPerG * proteinWeight;
                    if (stapleProteinCalories + veggieCaloriesPerG * veggieStart > mealCaloriesUpperBound) {
                        break;
                    }
                    for (int veggieWeight = veggieStart; veggieWeight <= veggieEnd; veggieWeight += 25) {
                        if (stapleProteinCalories + veggieCaloriesPerG * veggieWeight > mealCaloriesUpperBound) {
                            break;
                        }
                        if (!FoodRuleHelper.isReasonableCombination(profile, mealType, staple, stapleWeight, veggie, veggieWeight)) {
                            continue;
                        }
                        MealSolution candidate = scorer.evaluate(profile, mealType, staple, stapleWeight, protein, proteinWeight, veggie, veggieWeight, target, noUsedFoodIds);
                        insertCandidate(refinedSolutions, candidate, poolSize);
                    }
                }
            }
        }
        return refinedSolutions;
    }

    private int stapleCoarseStep(MealType mealType) {
        return mealType == MealType.BREAKFAST ? 25 : 50;
    }

    private int proteinCoarseStep(MealType mealType) {
        return mealType == MealType.BREAKFAST ? 20 : 25;
    }

    private int veggieCoarseStep(MealType mealType) {
        return mealType == MealType.BREAKFAST ? 50 : 100;
    }

    private int mealCaloriesUpperBound(MealType mealType, MealTarget target) {
        int tolerance = switch (mealType) {
            case BREAKFAST -> 220;
            case LUNCH -> 260;
            case DINNER -> 200;
            case SNACK -> 120;
        };
        return target.getMealCalories() + tolerance;
    }

    private List<MealSolution> diversifyMealSolutions(List<MealSolution> rankedSolutions, int keepTopN) {
        if (rankedSolutions.size() <= keepTopN) {
            return rankedSolutions;
        }

        List<MealSolution> selected = new ArrayList<>();
        Set<String> coveredStaples = new LinkedHashSet<>();
        Set<String> coveredProteins = new LinkedHashSet<>();
        Set<String> coveredVeggies = new LinkedHashSet<>();
        Set<String> coveredPatterns = new LinkedHashSet<>();
        Set<Long> coveredStapleIds = new LinkedHashSet<>();
        Set<String> selectedKeys = new LinkedHashSet<>();
        int coreSize = Math.min(6, rankedSolutions.size());

        for (int i = 0; i < coreSize && selected.size() < keepTopN; i++) {
            MealSolution solution = rankedSolutions.get(i);
            selected.add(solution);
            coveredStaples.add(FoodRuleHelper.stapleGroup(solution.getStaple()));
            coveredProteins.add(FoodRuleHelper.proteinSourceGroup(solution.getProtein()));
            coveredVeggies.add(FoodRuleHelper.vegGroup(solution.getVeggie()));
            coveredPatterns.add(mealDiversitySignature(solution));
            coveredStapleIds.add(solution.getStaple().getId());
            selectedKeys.add(mealSolutionKey(solution));
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            String stapleGroup = FoodRuleHelper.stapleGroup(solution.getStaple());
            String key = mealSolutionKey(solution);
            if (!coveredStaples.contains(stapleGroup) && selectedKeys.add(key)) {
                selected.add(solution);
                coveredStaples.add(stapleGroup);
                coveredProteins.add(FoodRuleHelper.proteinSourceGroup(solution.getProtein()));
                coveredVeggies.add(FoodRuleHelper.vegGroup(solution.getVeggie()));
                coveredPatterns.add(mealDiversitySignature(solution));
                coveredStapleIds.add(solution.getStaple().getId());
            }
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            String proteinGroup = FoodRuleHelper.proteinSourceGroup(solution.getProtein());
            Long stapleId = solution.getStaple().getId();
            String key = mealSolutionKey(solution);
            if (!coveredProteins.contains(proteinGroup) && selectedKeys.add(key)) {
                selected.add(solution);
                coveredProteins.add(proteinGroup);
                coveredVeggies.add(FoodRuleHelper.vegGroup(solution.getVeggie()));
                coveredPatterns.add(mealDiversitySignature(solution));
                coveredStaples.add(FoodRuleHelper.stapleGroup(solution.getStaple()));
                coveredStapleIds.add(stapleId);
            }
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            String vegGroup = FoodRuleHelper.vegGroup(solution.getVeggie());
            Long stapleId = solution.getStaple().getId();
            String key = mealSolutionKey(solution);
            if (!coveredVeggies.contains(vegGroup) && selectedKeys.add(key)) {
                selected.add(solution);
                coveredVeggies.add(vegGroup);
                coveredProteins.add(FoodRuleHelper.proteinSourceGroup(solution.getProtein()));
                coveredPatterns.add(mealDiversitySignature(solution));
                coveredStaples.add(FoodRuleHelper.stapleGroup(solution.getStaple()));
                coveredStapleIds.add(stapleId);
            }
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            String pattern = mealDiversitySignature(solution);
            String key = mealSolutionKey(solution);
            if (!coveredPatterns.contains(pattern) && selectedKeys.add(key)) {
                selected.add(solution);
                coveredPatterns.add(pattern);
                coveredProteins.add(FoodRuleHelper.proteinSourceGroup(solution.getProtein()));
                coveredVeggies.add(FoodRuleHelper.vegGroup(solution.getVeggie()));
                coveredStaples.add(FoodRuleHelper.stapleGroup(solution.getStaple()));
                coveredStapleIds.add(solution.getStaple().getId());
            }
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            Long stapleId = solution.getStaple().getId();
            String key = mealSolutionKey(solution);
            if (!coveredStapleIds.contains(stapleId) && selectedKeys.add(key)) {
                selected.add(solution);
                coveredStapleIds.add(stapleId);
                coveredProteins.add(FoodRuleHelper.proteinSourceGroup(solution.getProtein()));
                coveredVeggies.add(FoodRuleHelper.vegGroup(solution.getVeggie()));
                coveredPatterns.add(mealDiversitySignature(solution));
                coveredStaples.add(FoodRuleHelper.stapleGroup(solution.getStaple()));
            }
        }

        for (MealSolution solution : rankedSolutions) {
            if (selected.size() >= keepTopN) {
                break;
            }
            String key = mealSolutionKey(solution);
            if (selectedKeys.add(key)) {
                selected.add(solution);
            }
        }

        return selected;
    }

    private String mealDiversitySignature(MealSolution solution) {
        return FoodRuleHelper.stapleGroup(solution.getStaple()) + "|"
                + FoodRuleHelper.proteinSourceGroup(solution.getProtein()) + "|"
                + FoodRuleHelper.vegGroup(solution.getVeggie());
    }

    private String mealSolutionKey(MealSolution solution) {
        return solution.getStaple().getId() + "-"
                + solution.getProtein().getId() + "-"
                + solution.getVeggie().getId() + "-"
                + solution.getStapleWeight() + "-"
                + solution.getProteinWeight() + "-"
                + solution.getVeggieWeight();
    }

    private RecommendVO.DailySummary buildDailySummary(
            GoalType goalType,
            List<RecommendVO.Meal> meals,
            DailyTargetProfile targetProfile,
            Map<MealType, RecommendVO.Meal> lockedMeals,
            SnackCandidate snackCandidate,
            boolean lockedSnackPresent
    ) {
        double totalProtein = meals.stream().mapToDouble(m -> safe(m.getMacros().getProtein())).sum();
        double totalFat = meals.stream().mapToDouble(m -> safe(m.getMacros().getFat())).sum();
        double totalCarb = meals.stream().mapToDouble(m -> safe(m.getMacros().getCarbs())).sum();
        int totalCalories = meals.stream().mapToInt(m -> m.getCalories() == null ? 0 : m.getCalories()).sum();

        RecommendVO.DailySummary summary = new RecommendVO.DailySummary();
        summary.setTotalCalories(totalCalories);
        summary.setTotalMacros(new RecommendVO.Macros(round1(totalProtein), round1(totalFat), round1(totalCarb)));

        double totalMacroCalories = totalProtein * 4 + totalFat * 9 + totalCarb * 4;
        if (totalMacroCalories > 0) {
            summary.setPfcRatio(new RecommendVO.Macros(
                    round3(totalProtein * 4 / totalMacroCalories),
                    round3(totalFat * 9 / totalMacroCalories),
                    round3(totalCarb * 4 / totalMacroCalories)
            ));
        } else {
            summary.setPfcRatio(new RecommendVO.Macros(0.0, 0.0, 0.0));
        }

        summary.setSummaryText(buildDailySummaryTextV2(goalType));
        summary.setReasons(buildDailyReasonsV2(goalType, targetProfile, summary, lockedMeals, snackCandidate, lockedSnackPresent));
        return summary;
    }

    private List<String> buildDailyReasons(
            GoalType goalType,
            DailyTargetProfile targetProfile,
            RecommendVO.DailySummary summary,
            Map<MealType, RecommendVO.Meal> lockedMeals,
            SnackCandidate snackCandidate
    ) {
        List<String> reasons = new ArrayList<>();
        reasons.add("全天评分会同时考虑热量、蛋白质、脂肪、碳水和膳食纤维。");
        reasons.add("今日目标热量为 " + targetProfile.getTargetCalories() + " 千卡，当前方案约为 " + summary.getTotalCalories() + " 千卡。");
        reasons.add("宏量目标大致为：蛋白质 " + round1(targetProfile.getTargetProteinG()) + "g / 脂肪 " + round1(targetProfile.getTargetFatG()) + "g / 碳水 " + round1(targetProfile.getTargetCarbG()) + "g。");
        if (!lockedMeals.isEmpty()) {
            reasons.add("本次刷新中，已吃餐次会按实际记录锁定保留。");
        }
        if (goalType == GoalType.LOSE_FAT || goalType == GoalType.DIABETES_CONTROL) {
            reasons.add("晚餐会帮助收住全天碳水预算。");
        }
        if (snackCandidate == null) {
            reasons.add("当前三餐已经基本满足需求，因此未额外安排加餐。");
        } else {
            reasons.add("由于全天仍存在明确缺口，因此补充了受控加餐。");
        }
        reasons.add("系统对跨餐重复食物和重复主食类型做了惩罚，尽量避免三餐过于单调。");
        return reasons;
    }

    private RecommendVO.RefreshInfo buildRefreshInfo(boolean refreshed, Map<MealType, RecommendVO.Meal> lockedMeals) {
        List<String> lockedMealCodes = lockedMeals.keySet().stream().map(MealType::getCode).collect(Collectors.toList());
        String message;
        if (!refreshed) {
            message = "今日推荐已生成。";
        } else if (lockedMealCodes.isEmpty()) {
            message = "今日推荐已根据最新信息重新生成。";
        } else {
            message = "今日推荐已刷新，已吃餐次已锁定保留。";
        }
        return new RecommendVO.RefreshInfo(refreshed, lockedMealCodes, message);
    }

    private List<String> buildExtraAdvice() {
        List<String> tips = new ArrayList<>(HEALTH_TIPS);
        Collections.shuffle(tips);
        return new ArrayList<>(tips.subList(0, Math.min(4, tips.size())));
    }

    private void insertCandidate(List<MealSolution> bestSolutions, MealSolution candidate, int keepTopN) {
        int insertIndex = 0;
        while (insertIndex < bestSolutions.size() && bestSolutions.get(insertIndex).getScore() <= candidate.getScore()) {
            insertIndex++;
        }
        bestSolutions.add(insertIndex, candidate);
        if (bestSolutions.size() > keepTopN) {
            bestSolutions.remove(bestSolutions.size() - 1);
        }
    }

    private void insertDayPlanCandidate(List<DayPlanCandidate> bestPlans, DayPlanCandidate candidate, int keepTopK) {
        int insertIndex = 0;
        while (insertIndex < bestPlans.size() && bestPlans.get(insertIndex).getScore() <= candidate.getScore()) {
            insertIndex++;
        }
        bestPlans.add(insertIndex, candidate);
        if (bestPlans.size() > keepTopK) {
            bestPlans.remove(bestPlans.size() - 1);
        }
    }

    private double calculateBMI(User user) {
        double heightMeter = user.getHeight() / 100.0;
        return round1(user.getWeight() / (heightMeter * heightMeter));
    }

    private String getBMIStatus(double bmi) {
        if (bmi < 18.5) {
            return "偏瘦";
        }
        if (bmi < 24) {
            return "正常";
        }
        if (bmi < 28) {
            return "超重";
        }
        return "肥胖";
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

    private String buildKeyMessage(GoalType goalType, DailyTargetProfile targetProfile) {
        List<String> parts = new ArrayList<>();
        if (targetProfile.hasTag("HISTORY_CAL_DOWN")) {
            parts.add("近期摄入偏高，今日热量已适当下调。");
        } else if (targetProfile.hasTag("HISTORY_CAL_UP")) {
            parts.add("近期摄入偏低，今日热量已小幅上调。");
        }
        if (targetProfile.hasTag("HISTORY_PROTEIN_UP")) {
            parts.add("今日会更强调蛋白供给。");
        }
        if (targetProfile.hasTag("HISTORY_CARB_DOWN")) {
            parts.add("今日碳水比例会更收紧。");
        }
        if (targetProfile.hasTag("HISTORY_FAT_DOWN")) {
            parts.add("今日会更严格限制高脂食物。");
        }
        parts.add(switch (goalType) {
            case LOSE_FAT -> "今日重点是高蛋白、控热量、晚餐主食更轻。";
            case GAIN_MUSCLE -> "今日重点是保证能量和蛋白，支持恢复。";
            case DIABETES_CONTROL -> "今日重点是低 GI 主食和更平稳的碳水结构。";
            case HYPERTENSION_CONTROL -> "今日重点是控钠和更清淡的食物选择。";
            case HYPERLIPIDEMIA_CONTROL -> "今日重点是降低饱和脂肪并增加膳食纤维。";
            case MAINTAIN -> "今日重点是保持全天均衡、可持续。";
        });
        return String.join(" ", parts);
    }

    private String goalSpecificReason(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "减脂模式下会优先控制总热量，并保证蛋白质密度。";
            case GAIN_MUSCLE -> "增肌模式下会优先保证全天能量和恢复支持。";
            case DIABETES_CONTROL -> "控糖模式下会优先考虑 GI 和糖分控制。";
            case HYPERTENSION_CONTROL -> "控压模式下会优先控制钠摄入。";
            case HYPERLIPIDEMIA_CONTROL -> "控脂模式下会优先限制饱和脂肪。";
            case MAINTAIN -> "维持模式下会优先保证全天均衡。";
        };
    }

    private String buildDailySummaryText(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> "执行重点：高蛋白、高纤维，晚餐主食更轻。";
            case GAIN_MUSCLE -> "执行重点：全天保证足够能量和蛋白质。";
            case DIABETES_CONTROL -> "执行重点：碳水结构更平稳，优先低 GI 选择。";
            case HYPERTENSION_CONTROL -> "执行重点：控钠、少重口、调味更简单。";
            case HYPERLIPIDEMIA_CONTROL -> "执行重点：降低脂肪密度并增加纤维。";
            case MAINTAIN -> "执行重点：三餐均衡，不过度矫正。";
        };
    }

    private String buildMealAdvice(MealType mealType, GoalType goalType) {
        if (mealType == MealType.BREAKFAST) {
            return "早餐负责打开全天节奏，并提供基础蛋白支持。";
        }
        if (mealType == MealType.LUNCH) {
            return "午餐承担白天主要能量和宏量平衡。";
        }
        if (mealType == MealType.SNACK) {
            return switch (goalType) {
                case LOSE_FAT -> "加餐会控制分量，只在确有缺口时补充。";
                case GAIN_MUSCLE -> "加餐用于支持恢复和补充额外能量。";
                case DIABETES_CONTROL -> "加餐优先低 GI、低糖选择。";
                case HYPERTENSION_CONTROL -> "加餐会尽量避开高盐加工食品。";
                case HYPERLIPIDEMIA_CONTROL -> "加餐优先低脂或高纤维食物。";
                case MAINTAIN -> "加餐不是固定存在，只会在全天仍有缺口时出现。";
            };
        }
        return "晚餐会收住全天节奏，同时尽量保留蛋白和蔬菜。";
    }

    private Map<MealType, Integer> buildMealTargetCalories(int dailyTargetCalories, Map<MealType, RecommendVO.Meal> lockedMeals) {
        List<MealType> unlockedMainMeals = List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).stream()
                .filter(mealType -> !lockedMeals.containsKey(mealType))
                .toList();
        if (unlockedMainMeals.isEmpty()) {
            return Map.of();
        }

        int lockedCalories = lockedMeals.values().stream()
                .mapToInt(meal -> meal.getCalories() == null ? 0 : meal.getCalories())
                .sum();
        int remainingCalories = dailyTargetCalories - lockedCalories;
        Map<MealType, Integer> targets = new LinkedHashMap<>();

        if (remainingCalories <= 0) {
            for (MealType mealType : unlockedMainMeals) {
                targets.put(mealType, compressedMealTargetCalories(mealType, dailyTargetCalories));
            }
            return targets;
        }

        double ratioSum = unlockedMainMeals.stream().mapToDouble(this::mealRatio).sum();
        int allocated = 0;
        for (int i = 0; i < unlockedMainMeals.size(); i++) {
            MealType mealType = unlockedMainMeals.get(i);
            int targetCalories = i == unlockedMainMeals.size() - 1
                    ? Math.max(0, remainingCalories - allocated)
                    : Math.max(0, (int) Math.round(remainingCalories * mealRatio(mealType) / ratioSum));
            targets.put(mealType, targetCalories);
            allocated += targetCalories;
        }
        return targets;
    }

    private double mealRatio(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> 0.30;
            case LUNCH -> 0.40;
            case DINNER -> 0.30;
            case SNACK -> 0.0;
        };
    }

    private int compressedMealTargetCalories(MealType mealType, int dailyTargetCalories) {
        int baseTarget = targetCaloriesForMeal(mealType, dailyTargetCalories);
        return switch (mealType) {
            case BREAKFAST -> Math.max(180, (int) Math.round(baseTarget * 0.35));
            case LUNCH -> Math.max(220, (int) Math.round(baseTarget * 0.35));
            case DINNER -> Math.max(180, (int) Math.round(baseTarget * 0.35));
            case SNACK -> 0;
        };
    }

    private int targetCaloriesForMeal(MealType mealType, int dailyTargetCalories) {
        return switch (mealType) {
            case BREAKFAST -> (int) Math.round(dailyTargetCalories * 0.30);
            case LUNCH -> (int) Math.round(dailyTargetCalories * 0.40);
            case DINNER -> dailyTargetCalories - (int) Math.round(dailyTargetCalories * 0.30) - (int) Math.round(dailyTargetCalories * 0.40);
            case SNACK -> 0;
        };
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private void logRecommendCost(
            Long userId,
            LocalDate date,
            Map<MealType, RecommendVO.Meal> lockedMeals,
            long targetStageMs,
            long candidateStageMs,
            long dayPlanStageMs,
            long assembleStageMs,
            long totalStageMs
    ) {
        if (totalStageMs < 1000) {
            return;
        }
        log.warn(
                "recommend generation slow userId={} date={} total={}ms target={}ms candidates={}ms dayPlan={}ms assemble={}ms lockedMeals={}",
                userId,
                date,
                totalStageMs,
                targetStageMs,
                candidateStageMs,
                dayPlanStageMs,
                assembleStageMs,
                lockedMeals.keySet()
        );
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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

    private record MealBuildResult(RecommendVO.Meal meal, double fiberG, MealReference reference) {
    }

    private record MealReference(Long stapleId, String stapleGroup, Set<Long> foodIds, Set<String> proteinGroups, Set<String> vegGroups) {
        private static MealReference empty() {
            return new MealReference(null, "", Set.of(), Set.of(), Set.of());
        }

        private boolean isEmpty() {
            return stapleId == null && foodIds.isEmpty();
        }
    }

    private static class MealComposition {
        private final List<FoodPortion> items = new ArrayList<>();
        private final int maxItems;
        private int proteinCount;
        private int vegCount;
        private boolean stapleAdded;
        private int calories;
        private double protein;
        private double fat;
        private double carbs;
        private double fiber;

        private MealComposition() {
            this(5);
        }

        private MealComposition(int maxItems) {
            this.maxItems = maxItems;
        }

        private void add(Food food, int weightG, String role) {
            items.add(new FoodPortion(food, weightG, role));
            calories += (int) Math.round(FoodRuleHelper.safe(food.getCalories()) * weightG / 100.0);
            protein += FoodRuleHelper.safe(food.getProtein()) * weightG / 100.0;
            fat += FoodRuleHelper.safe(food.getFat()) * weightG / 100.0;
            carbs += FoodRuleHelper.safe(food.getCarb()) * weightG / 100.0;
            fiber += FoodRuleHelper.decimalValue(food.getFiber()) * weightG / 100.0;
            if ("protein".equals(role)) {
                proteinCount++;
            }
            if ("veg".equals(role)) {
                vegCount++;
            }
            if ("staple".equals(role)) {
                stapleAdded = true;
            }
        }

        private boolean hasFood(Long foodId) {
            return items.stream().anyMatch(item -> item.food().getId().equals(foodId));
        }

        private String menu() {
            return items.stream()
                    .map(item -> item.food().getName() + " " + item.weightG() + "g")
                    .collect(Collectors.joining(" + "));
        }

        private int itemCount() {
            return items.size();
        }

        private int maxItems() {
            return maxItems;
        }

        private int proteinCount() {
            return proteinCount;
        }

        private int vegCount() {
            return vegCount;
        }

        private boolean hasStaple() {
            return stapleAdded;
        }

        private int calories() {
            return calories;
        }

        private double protein() {
            return protein;
        }

        private double fat() {
            return fat;
        }

        private double carbs() {
            return carbs;
        }

        private double fiber() {
            return fiber;
        }

        private MealReference toReference() {
            Long stapleId = null;
            String stapleGroup = "";
            Set<Long> foodIds = new LinkedHashSet<>();
            Set<String> proteinGroups = new LinkedHashSet<>();
            Set<String> vegGroups = new LinkedHashSet<>();

            for (FoodPortion item : items) {
                Food food = item.food();
                if (food == null || food.getId() == null || food.getId() <= 0) {
                    continue;
                }
                foodIds.add(food.getId());
                if ("staple".equals(item.role())) {
                    stapleId = food.getId();
                    stapleGroup = FoodRuleHelper.stapleGroup(food);
                }
                if ("protein".equals(item.role())) {
                    proteinGroups.add(FoodRuleHelper.proteinSourceGroup(food));
                }
                if ("veg".equals(item.role())) {
                    vegGroups.add(FoodRuleHelper.vegGroup(food));
                }
            }
            return new MealReference(stapleId, stapleGroup, foodIds, proteinGroups, vegGroups);
        }
    }

    private record FoodPortion(Food food, int weightG, String role) {
    }
}
