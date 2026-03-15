package com.healthdiet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.entity.Food;
import com.healthdiet.entity.Recommendation;
import com.healthdiet.entity.User;
import com.healthdiet.entity.vo.RecommendVO;
import com.healthdiet.mapper.DietRecordMapper;
import com.healthdiet.mapper.FoodMapper;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.model.DayPlanCandidate;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.MealSolution;
import com.healthdiet.recommend.rule.FoodRuleHelper;
import com.healthdiet.recommend.rule.GoalRuleProfile;
import com.healthdiet.service.impl.RecommendServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class RecommendServiceImplTest {

    private TestableRecommendService recommendService;
    private UserMapper userMapper;
    private FoodMapper foodMapper;
    private DietRecordMapper dietRecordMapper;
    private DailyTargetService dailyTargetService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        recommendService = new TestableRecommendService();
        userMapper = Mockito.mock(UserMapper.class);
        foodMapper = Mockito.mock(FoodMapper.class);
        dietRecordMapper = Mockito.mock(DietRecordMapper.class);
        dailyTargetService = Mockito.mock(DailyTargetService.class);

        ReflectionTestUtils.setField(recommendService, "userMapper", userMapper);
        ReflectionTestUtils.setField(recommendService, "foodMapper", foodMapper);
        ReflectionTestUtils.setField(recommendService, "dietRecordMapper", dietRecordMapper);
        ReflectionTestUtils.setField(recommendService, "dailyTargetService", dailyTargetService);
    }

    @Test
    void refreshShouldLockBreakfastWhenBreakfastRecordExists() throws Exception {
        LocalDate today = LocalDate.now();
        User user = baseUser();

        Recommendation existing = new Recommendation();
        existing.setId(99L);
        existing.setUserId(1L);
        existing.setDate(today);
        existing.setResultJson("{}");
        recommendService.existingRecommendation = existing;

        Food breakfastFood = food(100L, "Breakfast Oats", "staple", 0, 1);
        breakfastFood.setCalories(130.0);
        breakfastFood.setProtein(5.0);
        breakfastFood.setFat(2.0);
        breakfastFood.setCarb(24.0);

        DietRecord breakfastRecord = new DietRecord();
        breakfastRecord.setUserId(1L);
        breakfastRecord.setDate(today);
        breakfastRecord.setMealType(1);
        breakfastRecord.setFoodId(100L);
        breakfastRecord.setQuantity(2);

        Mockito.when(userMapper.selectById(1L)).thenReturn(user);
        Mockito.when(foodMapper.selectList(any())).thenReturn(recommendationFoods());
        Mockito.when(foodMapper.selectBatchIds(any())).thenReturn(List.of(breakfastFood));
        Mockito.when(dietRecordMapper.selectList(any())).thenReturn(List.of(breakfastRecord));
        Mockito.when(dailyTargetService.buildDailyTarget(any(), any(), any())).thenReturn(targetProfile());

        Recommendation refreshed = recommendService.refreshTodayRecommend(1L);
        RecommendVO vo = objectMapper.readValue(refreshed.getResultJson(), RecommendVO.class);

        RecommendVO.Meal breakfast = vo.getMeals().stream()
                .filter(meal -> MealType.BREAKFAST.getCode().equals(meal.getType()))
                .findFirst()
                .orElse(null);

        assertNotNull(breakfast);
        assertTrue(Boolean.TRUE.equals(breakfast.getLocked()));
        assertTrue(breakfast.getMenu().contains("Breakfast Oats"));
        assertEquals(Boolean.TRUE, vo.getRefreshInfo().getRefreshed());
        assertTrue(vo.getRefreshInfo().getLockedMeals().contains(MealType.BREAKFAST.getCode()));
        assertEquals(existing.getId(), refreshed.getId());
    }

    @Test
    void refreshShouldLockSnackWhenSnackRecordExists() throws Exception {
        LocalDate today = LocalDate.now();
        User user = baseUser();

        Food snackFood = food(200L, "Greek Yogurt", "dairy", 1, 1);
        snackFood.setCalories(63.0);
        snackFood.setProtein(5.3);
        snackFood.setFat(1.6);
        snackFood.setCarb(7.0);

        DietRecord snackRecord = new DietRecord();
        snackRecord.setUserId(1L);
        snackRecord.setDate(today);
        snackRecord.setMealType(4);
        snackRecord.setFoodId(200L);
        snackRecord.setQuantity(1);

        Mockito.when(userMapper.selectById(1L)).thenReturn(user);
        Mockito.when(foodMapper.selectList(any())).thenReturn(recommendationFoods());
        Mockito.when(foodMapper.selectBatchIds(any())).thenReturn(List.of(snackFood));
        Mockito.when(dietRecordMapper.selectList(any())).thenReturn(List.of(snackRecord));
        Mockito.when(dailyTargetService.buildDailyTarget(any(), any(), any())).thenReturn(targetProfile());

        Recommendation refreshed = recommendService.refreshTodayRecommend(1L);
        RecommendVO vo = objectMapper.readValue(refreshed.getResultJson(), RecommendVO.class);

        List<RecommendVO.Meal> snacks = vo.getMeals().stream()
                .filter(meal -> MealType.SNACK.getCode().equals(meal.getType()))
                .toList();

        assertEquals(1, snacks.size());
        assertTrue(Boolean.TRUE.equals(snacks.get(0).getLocked()));
        assertTrue(snacks.get(0).getMenu().contains("Greek Yogurt"));
        assertTrue(vo.getRefreshInfo().getLockedMeals().contains(MealType.SNACK.getCode()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMealTargetCaloriesShouldShrinkRemainingMealsAfterLockedBreakfast() {
        Map<MealType, RecommendVO.Meal> lockedMeals = new LinkedHashMap<>();
        lockedMeals.put(MealType.BREAKFAST, lockedMeal(MealType.BREAKFAST, 900));

        Map<MealType, Integer> mealTargets = (Map<MealType, Integer>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMealTargetCalories",
                1800,
                lockedMeals
        );

        assertEquals(2, mealTargets.size());
        assertEquals(900, mealTargets.get(MealType.LUNCH) + mealTargets.get(MealType.DINNER));
        assertTrue(mealTargets.get(MealType.LUNCH) < 720);
        assertTrue(mealTargets.get(MealType.DINNER) < 540);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildMealTargetCaloriesShouldUseCompressedModeAfterTargetIsExceeded() {
        Map<MealType, RecommendVO.Meal> lockedMeals = new LinkedHashMap<>();
        lockedMeals.put(MealType.BREAKFAST, lockedMeal(MealType.BREAKFAST, 1000));
        lockedMeals.put(MealType.LUNCH, lockedMeal(MealType.LUNCH, 1000));

        Map<MealType, Integer> mealTargets = (Map<MealType, Integer>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMealTargetCalories",
                1800,
                lockedMeals
        );
        Integer compressedDinner = (Integer) ReflectionTestUtils.invokeMethod(
                recommendService,
                "compressedMealTargetCalories",
                MealType.DINNER,
                1800
        );

        assertEquals(1, mealTargets.size());
        assertEquals(compressedDinner, mealTargets.get(MealType.DINNER));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diversifyCandidatesShouldReserveSlotsForDifferentGroups() {
        List<Food> rankedFoods = new ArrayList<>();
        rankedFoods.add(groupedFood(1L, "rice", "Brown Rice"));
        rankedFoods.add(groupedFood(2L, "rice", "Steamed Rice"));
        rankedFoods.add(groupedFood(3L, "rice", "Millet Porridge"));
        rankedFoods.add(groupedFood(4L, "rice", "Corn Porridge"));
        rankedFoods.add(groupedFood(5L, "rice", "Rice Bowl"));
        rankedFoods.add(groupedFood(6L, "rice", "Rice Plate"));
        rankedFoods.add(groupedFood(7L, "oat_wheat", "Oatmeal"));
        rankedFoods.add(groupedFood(8L, "corn", "Corn"));

        List<Food> diversified = (List<Food>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "diversifyCandidates",
                rankedFoods,
                8,
                6,
                (Function<Food, String>) Food::getFoodCategory
        );

        List<String> groups = diversified.stream().map(Food::getFoodCategory).toList();
        assertTrue(groups.contains("rice"));
        assertTrue(groups.contains("oat_wheat"));
        assertTrue(groups.contains("corn"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diversifyMealSolutionsShouldKeepDifferentStapleGroups() {
        List<MealSolution> rankedSolutions = new ArrayList<>();
        rankedSolutions.add(meal(1L, "Oatmeal", 101L, "Egg", 201L, "Broccoli", 510, 28, 14, 62));
        rankedSolutions.add(meal(2L, "Oat Rice", 102L, "Chicken", 202L, "Lettuce", 520, 32, 15, 64));
        rankedSolutions.add(meal(3L, "Whole Wheat Porridge", 103L, "Fish", 203L, "Cucumber", 530, 34, 16, 66));
        rankedSolutions.add(meal(4L, "Brown Rice", 104L, "Tofu", 204L, "Tomato", 540, 26, 15, 68));
        rankedSolutions.add(meal(5L, "Sweet Potato", 105L, "Chicken", 205L, "Spinach", 550, 33, 14, 70));

        List<MealSolution> diversified = (List<MealSolution>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "diversifyMealSolutions",
                rankedSolutions,
                5
        );

        List<String> stapleGroups = diversified.stream()
                .map(solution -> FoodRuleHelper.stapleGroup(solution.getStaple()))
                .toList();
        assertTrue(stapleGroups.contains("oat_wheat"));
        assertTrue(stapleGroups.contains("rice") || stapleGroups.contains("potato"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diversifyMealSolutionsShouldKeepDifferentMealPatterns() {
        List<MealSolution> rankedSolutions = new ArrayList<>();
        rankedSolutions.add(meal(21L, "Brown Rice", 301L, "Chicken Breast", 401L, "Spinach", 620, 38, 14, 78));
        rankedSolutions.add(meal(22L, "Steamed Rice", 302L, "Chicken Leg", 402L, "Lettuce", 630, 39, 15, 80));
        rankedSolutions.add(meal(23L, "Brown Rice", 303L, "Turkey Breast", 403L, "Cabbage", 635, 40, 14, 81));
        rankedSolutions.add(meal(24L, "Sweet Potato", 304L, "Cod", 404L, "Cucumber", 610, 36, 13, 74));
        rankedSolutions.add(meal(25L, "Buckwheat Noodle", 305L, "Tofu", 405L, "Mushroom", 600, 30, 12, 76));

        List<MealSolution> diversified = (List<MealSolution>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "diversifyMealSolutions",
                rankedSolutions,
                5
        );

        List<String> patterns = diversified.stream()
                .map(solution -> FoodRuleHelper.stapleGroup(solution.getStaple()) + "|"
                        + FoodRuleHelper.proteinSourceGroup(solution.getProtein()) + "|"
                        + FoodRuleHelper.vegGroup(solution.getVeggie()))
                .distinct()
                .toList();

        assertTrue(patterns.size() >= 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lunchStaplesShouldPreferNonBreakfastStyleWhenAlternativesExist() {
        List<Food> rankedStaples = new ArrayList<>();
        rankedStaples.add(groupedFood(11L, "oat_wheat", "Oatmeal"));
        rankedStaples.add(groupedFood(12L, "bakery", "Whole Wheat Bread"));
        rankedStaples.add(groupedFood(13L, "bakery", "Bagel"));
        rankedStaples.add(groupedFood(14L, "rice", "Brown Rice"));
        rankedStaples.add(groupedFood(15L, "potato", "Sweet Potato"));

        List<Food> filtered = (List<Food>) ReflectionTestUtils.invokeMethod(
                recommendService,
                "filterStaplesForMealType",
                rankedStaples,
                MealType.LUNCH
        );

        long restrictedCount = filtered.stream()
                .filter(food -> (boolean) ReflectionTestUtils.invokeMethod(recommendService, "isLunchDinnerRestrictedStaple", food))
                .count();

        assertTrue(filtered.stream().anyMatch(food -> "rice".equals(food.getFoodCategory())));
        assertTrue(filtered.stream().anyMatch(food -> "potato".equals(food.getFoodCategory())));
        assertTrue(restrictedCount <= 1);
    }

    @Test
    void selectDailyBestPlanShouldStillFindBestCombinationAfterBeamPruning() {
        DailyTargetProfile target = targetProfile();

        List<MealSolution> breakfasts = List.of(
                meal(1L, "Oatmeal", 2L, "Egg", 3L, "Broccoli", 520, 28, 16, 60),
                meal(4L, "Whole Wheat Bread", 5L, "Egg", 6L, "Cucumber", 430, 18, 12, 58)
        );
        List<MealSolution> lunches = List.of(
                meal(7L, "Brown Rice", 8L, "Chicken", 9L, "Lettuce", 620, 40, 16, 72),
                meal(10L, "Brown Rice", 11L, "Tofu", 12L, "Tomato", 540, 26, 14, 68)
        );
        List<MealSolution> dinners = List.of(
                meal(13L, "Sweet Potato", 14L, "Fish", 15L, "Spinach", 610, 38, 18, 70),
                meal(16L, "Buckwheat Noodle", 17L, "Egg", 18L, "Cucumber", 430, 18, 12, 65)
        );

        DayPlanCandidate bestPlan = (DayPlanCandidate) ReflectionTestUtils.invokeMethod(
                recommendService,
                "selectDailyBestPlan",
                breakfasts,
                lunches,
                dinners,
                target,
                GoalType.MAINTAIN
        );

        assertNotNull(bestPlan);
        assertEquals("Fish", bestPlan.getDinner().getProtein().getName());
        assertEquals("Chicken", bestPlan.getLunch().getProtein().getName());
    }

    @Test
    void selectDailyBestPlanShouldPreferDifferentLunchDinnerCoreTypes() {
        DailyTargetProfile target = targetProfile();

        List<MealSolution> breakfasts = List.of(
                meal(101L, "Oatmeal", 102L, "Egg", 103L, "Broccoli", 520, 28, 16, 60)
        );
        List<MealSolution> lunches = List.of(
                meal(104L, "Brown Rice", 105L, "Chicken Breast", 106L, "Spinach", 640, 42, 16, 74)
        );
        List<MealSolution> dinners = List.of(
                meal(107L, "Steamed Rice", 108L, "Chicken Leg", 109L, "Lettuce", 620, 40, 16, 72),
                meal(110L, "Sweet Potato", 111L, "Cod", 112L, "Cucumber", 610, 38, 18, 70)
        );

        DayPlanCandidate bestPlan = (DayPlanCandidate) ReflectionTestUtils.invokeMethod(
                recommendService,
                "selectDailyBestPlan",
                breakfasts,
                lunches,
                dinners,
                target,
                GoalType.MAINTAIN
        );

        assertNotNull(bestPlan);
        assertEquals("Cod", bestPlan.getDinner().getProtein().getName());
        assertEquals("Sweet Potato", bestPlan.getDinner().getStaple().getName());
    }

    @Test
    void diabetesStapleScoringShouldAvoidUsingOatWheatForAllMeals() {
        GoalRuleProfile profile = GoalRuleProfile.of(GoalType.DIABETES_CONTROL);
        Food oats = food(21L, "Oatmeal", "staple", 1, 1);
        oats.setGiLevel("low");
        oats.setFiber(BigDecimal.valueOf(8));

        Food rice = food(22L, "Brown Rice", "staple", 1, 1);
        rice.setGiLevel("low");
        rice.setFiber(BigDecimal.valueOf(3));

        double lunchOatScore = (double) ReflectionTestUtils.invokeMethod(
                recommendService, "scoreStapleCandidate", oats, profile, MealType.LUNCH);
        double lunchRiceScore = (double) ReflectionTestUtils.invokeMethod(
                recommendService, "scoreStapleCandidate", rice, profile, MealType.LUNCH);
        double dinnerOatScore = (double) ReflectionTestUtils.invokeMethod(
                recommendService, "scoreStapleCandidate", oats, profile, MealType.DINNER);

        assertTrue(lunchOatScore > lunchRiceScore);
        assertTrue(dinnerOatScore > lunchOatScore);
        assertFalse(dinnerOatScore < lunchRiceScore);
    }

    @Test
    void lunchAndDinnerShouldPenalizeOatWheatAcrossCoreGoals() {
        Food oats = food(31L, "Oatmeal", "staple", 1, 1);
        oats.setGiLevel("low");
        oats.setFiber(BigDecimal.valueOf(8));

        Food rice = food(32L, "Brown Rice", "staple", 1, 1);
        rice.setGiLevel("low");
        rice.setFiber(BigDecimal.valueOf(3));

        for (GoalType goalType : List.of(GoalType.MAINTAIN, GoalType.LOSE_FAT, GoalType.GAIN_MUSCLE, GoalType.DIABETES_CONTROL)) {
            GoalRuleProfile profile = GoalRuleProfile.of(goalType);
            double lunchOatScore = (double) ReflectionTestUtils.invokeMethod(
                    recommendService, "scoreStapleCandidate", oats, profile, MealType.LUNCH);
            double lunchRiceScore = (double) ReflectionTestUtils.invokeMethod(
                    recommendService, "scoreStapleCandidate", rice, profile, MealType.LUNCH);
            double dinnerOatScore = (double) ReflectionTestUtils.invokeMethod(
                    recommendService, "scoreStapleCandidate", oats, profile, MealType.DINNER);

            assertTrue(lunchOatScore > lunchRiceScore, "lunch oat score should be worse for " + goalType);
            assertTrue(dinnerOatScore > lunchOatScore, "dinner oat score should be worse for " + goalType);
        }
    }

    @Test
    void dailyPlanShouldRejectMultipleBreakfastStyleStaples() {
        MealSolution breakfast = meal(41L, "Oatmeal", 141L, "Egg", 241L, "Broccoli", 520, 28, 14, 62);
        MealSolution lunch = meal(42L, "Whole Wheat Bread", 142L, "Chicken", 242L, "Lettuce", 620, 40, 16, 72);
        MealSolution dinner = meal(43L, "Bagel", 143L, "Fish", 243L, "Cucumber", 610, 38, 18, 70);
        MealSolution mixedDinner = meal(44L, "Sweet Potato", 144L, "Fish", 244L, "Cucumber", 610, 38, 18, 70);

        boolean repeatedBreakfastStyle = (boolean) ReflectionTestUtils.invokeMethod(
                recommendService, "isAllowedDailyStaplePattern", breakfast, lunch, dinner);
        boolean mixedPattern = (boolean) ReflectionTestUtils.invokeMethod(
                recommendService, "isAllowedDailyStaplePattern", breakfast, meal(45L, "Brown Rice", 145L, "Chicken", 245L, "Lettuce", 620, 40, 16, 72), mixedDinner);

        assertFalse(repeatedBreakfastStyle);
        assertTrue(mixedPattern);
    }

    @Test
    void selectDailyBestPlanShouldFallbackWhenPatternFilterRemovesAllPlans() {
        DailyTargetProfile target = targetProfile();
        List<MealSolution> breakfasts = List.of(meal(51L, "Oatmeal", 151L, "Egg", 251L, "Broccoli", 520, 28, 14, 62));
        List<MealSolution> lunches = List.of(meal(52L, "Whole Wheat Bread", 152L, "Chicken", 252L, "Lettuce", 620, 40, 16, 72));
        List<MealSolution> dinners = List.of(meal(53L, "Bagel", 153L, "Fish", 253L, "Cucumber", 610, 38, 18, 70));

        DayPlanCandidate bestPlan = (DayPlanCandidate) ReflectionTestUtils.invokeMethod(
                recommendService,
                "selectDailyBestPlan",
                breakfasts,
                lunches,
                dinners,
                target,
                GoalType.GAIN_MUSCLE
        );

        assertNotNull(bestPlan);
        assertNotNull(bestPlan.getBreakfast());
        assertNotNull(bestPlan.getLunch());
        assertNotNull(bestPlan.getDinner());
    }

    @Test
    void lunchDinnerRestrictedStaplesShouldCatchBagelAndSodaCracker() {
        Food bagel = food(61L, "Bagel", "staple", 1, 1);
        bagel.setGiLevel("high");
        bagel.setSodiumMg(BigDecimal.valueOf(450));

        Food sodaCracker = food(62L, "Soda Cracker", "staple", 1, 1);
        sodaCracker.setGiLevel("high");
        sodaCracker.setSodiumMg(BigDecimal.valueOf(670));
        sodaCracker.setFiber(BigDecimal.valueOf(3));

        Food rice = food(63L, "Brown Rice", "staple", 1, 1);
        rice.setGiLevel("medium");
        rice.setSodiumMg(BigDecimal.valueOf(5));
        rice.setFiber(BigDecimal.valueOf(1.8));

        assertTrue((boolean) ReflectionTestUtils.invokeMethod(recommendService, "isLunchDinnerRestrictedStaple", bagel));
        assertTrue((boolean) ReflectionTestUtils.invokeMethod(recommendService, "isLunchDinnerRestrictedStaple", sodaCracker));
        assertFalse((boolean) ReflectionTestUtils.invokeMethod(recommendService, "isLunchDinnerRestrictedStaple", rice));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gainMusclePlanShouldAvoidRepeatingProcessedBreakfastStaplesWhenWholeFoodAlternativesExist() {
        ReflectionTestUtils.setField(recommendService, "allFoodsCache", muscleFoods());
        GoalRuleProfile profile = GoalRuleProfile.of(GoalType.GAIN_MUSCLE);
        DailyTargetProfile target = muscleTargetProfile();

        List<MealSolution> breakfasts = (List<MealSolution>) ReflectionTestUtils.invokeMethod(
                recommendService, "generateMealCandidates", MealType.BREAKFAST, 720, profile);
        List<MealSolution> lunches = (List<MealSolution>) ReflectionTestUtils.invokeMethod(
                recommendService, "generateMealCandidates", MealType.LUNCH, 960, profile);
        List<MealSolution> dinners = (List<MealSolution>) ReflectionTestUtils.invokeMethod(
                recommendService, "generateMealCandidates", MealType.DINNER, 720, profile);

        DayPlanCandidate bestPlan = (DayPlanCandidate) ReflectionTestUtils.invokeMethod(
                recommendService,
                "selectDailyBestPlan",
                breakfasts,
                lunches,
                dinners,
                target,
                GoalType.GAIN_MUSCLE
        );

        assertNotNull(bestPlan);
        assertFalse(FoodRuleHelper.isRestrictedMainMealStaple(bestPlan.getLunch().getStaple()));
        assertFalse(FoodRuleHelper.isRestrictedMainMealStaple(bestPlan.getDinner().getStaple()));
        assertFalse(bestPlan.getLunch().getStaple().getId().equals(bestPlan.getDinner().getStaple().getId()));
        assertTrue(List.of(
                bestPlan.getBreakfast().getStaple().getId(),
                bestPlan.getLunch().getStaple().getId(),
                bestPlan.getDinner().getStaple().getId()
        ).stream().distinct().count() >= 2);
    }

    @Test
    void buildMealShouldExpandBreakfastAndReplaceProcessedLunchStapleWhenRiceExists() {
        ReflectionTestUtils.setField(recommendService, "allFoodsCache", muscleFoods());

        Object breakfastResult = ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMeal",
                meal(3L, "燕麦片(生)", 49L, "鸡蛋清", 18L, "黄瓜", 520, 32, 10, 68),
                MealType.BREAKFAST,
                GoalRuleProfile.of(GoalType.GAIN_MUSCLE),
                720,
                false,
                null
        );
        RecommendVO.Meal breakfast = (RecommendVO.Meal) ReflectionTestUtils.invokeMethod(breakfastResult, "meal");
        assertNotNull(breakfast);
        assertTrue(breakfast.getMenu().split(" \\+ ").length <= 3);

        Object lunchResult = ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMeal",
                meal(4L, "全麦面包", 8L, "鸡胸肉(生)", 15L, "西蓝花", 760, 48, 14, 96),
                MealType.LUNCH,
                GoalRuleProfile.of(GoalType.GAIN_MUSCLE),
                960,
                false,
                null
        );
        RecommendVO.Meal lunch = (RecommendVO.Meal) ReflectionTestUtils.invokeMethod(lunchResult, "meal");
        assertNotNull(lunch);
        assertTrue(lunch.getMenu().contains("米饭") || lunch.getMenu().contains("糙米饭"));
        assertTrue(lunch.getMenu().split(" \\+ ").length <= 5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildExtraAdviceShouldReturnAtLeastFourTips() {
        List<String> tips = (List<String>) ReflectionTestUtils.invokeMethod(recommendService, "buildExtraAdvice");

        assertNotNull(tips);
        assertTrue(tips.size() >= 4);
    }

    @Test
    void buildSummaryShouldExposeActivityFactorAndTdee() {
        RecommendVO.Summary summary = (RecommendVO.Summary) ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildSummary",
                baseUser(),
                GoalType.DIABETES_CONTROL,
                targetProfile()
        );

        assertNotNull(summary);
        assertEquals(1.35, summary.getActivityFactor());
        assertEquals(2200.0, summary.getTdee());
    }

    @Test
    void buildMealShouldKeepDinnerDifferentFromLunchReference() {
        ReflectionTestUtils.setField(recommendService, "allFoodsCache", muscleFoods());

        Object lunchResult = ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMeal",
                meal(28L, "Brown Rice", 47L, "Tuna", 81L, "Wood Ear", 760, 52, 16, 88),
                MealType.LUNCH,
                GoalRuleProfile.of(GoalType.DIABETES_CONTROL),
                760,
                false,
                null
        );
        Object lunchReference = ReflectionTestUtils.invokeMethod(lunchResult, "reference");

        Object dinnerResult = ReflectionTestUtils.invokeMethod(
                recommendService,
                "buildMeal",
                meal(6L, "Sweet Potato", 45L, "Cod", 18L, "Cucumber", 620, 38, 12, 70),
                MealType.DINNER,
                GoalRuleProfile.of(GoalType.DIABETES_CONTROL),
                650,
                false,
                lunchReference
        );

        RecommendVO.Meal dinner = (RecommendVO.Meal) ReflectionTestUtils.invokeMethod(dinnerResult, "meal");
        assertNotNull(dinner);
        assertFalse(dinner.getMenu().contains("Brown Rice"));
        assertFalse(dinner.getMenu().contains("Tuna"));
    }

    private User baseUser() {
        User user = new User();
        user.setId(1L);
        user.setHeight(175.0);
        user.setWeight(70.0);
        user.setAge(25);
        user.setGender(1);
        user.setTarget(0);
        user.setActivityLevel(2);
        return user;
    }

    private DailyTargetProfile targetProfile() {
        DailyTargetProfile profile = new DailyTargetProfile();
        profile.setTargetCalories(1800);
        profile.setTargetProteinG(120);
        profile.setTargetFatG(55);
        profile.setTargetCarbG(210);
        profile.setActivityFactor(1.35);
        profile.setTdee(2200);
        return profile;
    }

    private DailyTargetProfile muscleTargetProfile() {
        DailyTargetProfile profile = new DailyTargetProfile();
        profile.setTargetCalories(2400);
        profile.setTargetProteinG(165);
        profile.setTargetFatG(65);
        profile.setTargetCarbG(300);
        profile.setActivityFactor(1.5);
        profile.setTdee(2600);
        return profile;
    }

    private List<Food> recommendationFoods() {
        return List.of(
                food(1L, "Brown Rice", "staple", 0, 1),
                food(2L, "Sweet Potato", "staple", 0, 1),
                food(3L, "Chicken", "protein", 1, 1),
                food(4L, "Tofu", "protein", 1, 1),
                food(5L, "Broccoli", "vegetable", 1, 1),
                food(6L, "Cucumber", "vegetable", 1, 1),
                food(7L, "Greek Yogurt", "dairy", 1, 1),
                food(8L, "Apple", "fruit", 1, 1)
        );
    }

    private List<Food> muscleFoods() {
        return List.of(
                detailedFood(1L, "米饭(蒸)", "staple", 116, 2.6, 0.3, 25.9, "high", 2.0, 0.1, 0.3, 0.1, 1, 1),
                detailedFood(3L, "燕麦片(生)", "staple", 377, 15, 6.7, 61.6, "low", 6.0, 1.2, 10.1, 0.9, 1, 1),
                detailedFood(4L, "全麦面包", "staple", 246, 8.5, 3.5, 46.5, "medium", 467, 0.8, 6.0, 5.7, 1, 1),
                detailedFood(5L, "煮玉米", "staple", 112, 4, 1.2, 22.8, "medium", 15, 0.2, 2.7, 4.5, 1, 1),
                detailedFood(6L, "红薯(蒸)", "staple", 102, 1.5, 0.2, 24.7, "medium", 36, 0.0, 3.0, 6.5, 1, 1),
                detailedFood(28L, "糙米饭", "staple", 111, 2.6, 0.9, 23.0, "medium", 5, 0.2, 1.8, 0.4, 1, 1),
                detailedFood(31L, "荞麦面(煮)", "staple", 99, 5.1, 0.1, 21.4, "low", 5, 0.0, 1.9, 0.3, 1, 1),
                detailedFood(32L, "藜麦(熟)", "staple", 120, 4.4, 1.9, 21.3, "low", 7, 0.2, 2.8, 0.9, 1, 1),
                detailedFood(41L, "贝果", "staple", 250, 10, 1.5, 49.0, "high", 450, 0.3, 2.3, 5.1, 1, 1),
                detailedFood(42L, "苏打饼干", "staple", 408, 8, 9, 72.0, "high", 670, 2.0, 3.0, 7.0, 1, 1),
                detailedFood(8L, "鸡胸肉(生)", "protein", 133, 19.4, 5.0, 2.5, "low", 45, 1.3, 0, 0, 1, 1),
                detailedFood(11L, "瘦牛肉", "protein", 106, 20.2, 2.3, 0.0, "low", 55, 0.9, 0, 0, 0, 1),
                detailedFood(13L, "三文鱼", "protein", 139, 19.8, 6.3, 0.0, "low", 44, 1.2, 0, 0, 0, 1),
                detailedFood(14L, "豆腐(北)", "protein", 98, 12.2, 4.8, 1.5, "low", 7, 0.7, 0.6, 0.7, 1, 1),
                detailedFood(15L, "西蓝花", "vegetable", 36, 4.1, 0.6, 4.3, "low", 33, 0.1, 2.6, 1.7, 1, 1),
                detailedFood(18L, "黄瓜", "vegetable", 16, 0.8, 0.2, 2.9, "low", 2, 0.0, 0.5, 1.7, 1, 1),
                detailedFood(61L, "菠菜", "vegetable", 23, 2.9, 0.4, 3.6, "low", 79, 0.1, 2.2, 0.4, 1, 1),
                detailedFood(78L, "秋葵", "vegetable", 33, 1.9, 0.2, 7.5, "low", 7, 0.0, 3.2, 1.5, 1, 1)
        );
    }

    private Food food(Long id, String name, String category, Integer breakfastFriendly, Integer dinnerFriendly) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setFoodCategory(category);
        food.setBreakfastFriendly(breakfastFriendly);
        food.setDinnerFriendly(dinnerFriendly);
        food.setCalories(120.0);
        food.setProtein(12.0);
        food.setFat(4.0);
        food.setCarb(12.0);
        food.setFiber(BigDecimal.valueOf(3));
        food.setSugar(BigDecimal.valueOf(3));
        food.setGiLevel("low");
        food.setSodiumMg(BigDecimal.valueOf(60));
        food.setSaturatedFat(BigDecimal.valueOf(1));
        return food;
    }

    private Food detailedFood(
            Long id,
            String name,
            String category,
            double calories,
            double protein,
            double fat,
            double carb,
            String gi,
            double sodium,
            double satFat,
            double fiber,
            double sugar,
            Integer breakfastFriendly,
            Integer dinnerFriendly
    ) {
        Food food = food(id, name, category, breakfastFriendly, dinnerFriendly);
        food.setCalories(calories);
        food.setProtein(protein);
        food.setFat(fat);
        food.setCarb(carb);
        food.setGiLevel(gi);
        food.setSodiumMg(BigDecimal.valueOf(sodium));
        food.setSaturatedFat(BigDecimal.valueOf(satFat));
        food.setFiber(BigDecimal.valueOf(fiber));
        food.setSugar(BigDecimal.valueOf(sugar));
        return food;
    }

    private Food groupedFood(Long id, String group, String name) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setFoodCategory(group);
        return food;
    }

    private RecommendVO.Meal lockedMeal(MealType mealType, int calories) {
        return new RecommendVO.Meal(
                mealType.getCode(),
                mealType.getTitle(),
                mealType.getTitle() + " locked",
                calories,
                new RecommendVO.Macros(0.0, 0.0, 0.0),
                "",
                List.of(),
                true
        );
    }

    private MealSolution meal(
            Long stapleId, String stapleName,
            Long proteinId, String proteinName,
            Long veggieId, String veggieName,
            int calories, double protein, double fat, double carb
    ) {
        MealSolution solution = new MealSolution();
        solution.setStaple(simpleFood(stapleId, stapleName));
        solution.setProtein(simpleFood(proteinId, proteinName));
        solution.setVeggie(simpleFood(veggieId, veggieName));
        solution.setStapleWeight(100);
        solution.setProteinWeight(100);
        solution.setVeggieWeight(150);
        solution.setActualCalories(calories);
        solution.setProteinG(protein);
        solution.setFatG(fat);
        solution.setCarbG(carb);
        return solution;
    }

    private Food simpleFood(Long id, String name) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setFoodCategory("staple");
        food.setFiber(BigDecimal.valueOf(3));
        food.setSodiumMg(BigDecimal.valueOf(50));
        food.setGiLevel("low");
        return food;
    }

    private static class TestableRecommendService extends RecommendServiceImpl {
        private Recommendation existingRecommendation;

        @Override
        public Recommendation getOne(com.baomidou.mybatisplus.core.conditions.Wrapper<Recommendation> queryWrapper) {
            return existingRecommendation;
        }

        @Override
        public boolean save(Recommendation entity) {
            existingRecommendation = entity;
            if (entity.getId() == null) {
                entity.setId(1000L);
            }
            return true;
        }

        @Override
        public boolean updateById(Recommendation entity) {
            existingRecommendation = entity;
            return true;
        }
    }
}
