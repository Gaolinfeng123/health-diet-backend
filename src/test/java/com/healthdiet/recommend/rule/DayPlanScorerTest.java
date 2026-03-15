package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DayPlanCandidate;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.MealSolution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DayPlanScorerTest {

    private final DayPlanScorer scorer = new DayPlanScorer();

    @Test
    void shouldPreferDailyCombinationCloserToWholeDayTarget() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(1800);
        target.setTargetProteinG(120);
        target.setTargetFatG(55);
        target.setTargetCarbG(210);

        DayPlanCandidate better = new DayPlanCandidate();
        better.setBreakfast(meal(1L, "燕麦", 2L, "鸡蛋", 3L, "西蓝花", 500, 32, 16, 55));
        better.setLunch(meal(4L, "米饭", 5L, "鸡胸肉", 6L, "生菜", 650, 46, 18, 70));
        better.setDinner(meal(7L, "红薯", 8L, "鳕鱼", 9L, "黄瓜", 620, 40, 19, 78));

        DayPlanCandidate worse = new DayPlanCandidate();
        worse.setBreakfast(meal(10L, "面包", 11L, "鸡蛋", 12L, "黄瓜", 420, 20, 14, 58));
        worse.setLunch(meal(13L, "米饭", 14L, "鸡胸肉", 15L, "白菜", 540, 28, 10, 82));
        worse.setDinner(meal(16L, "面条", 17L, "鸡蛋", 18L, "番茄", 460, 18, 12, 65));

        double betterScore = scorer.evaluate(target, GoalType.MAINTAIN, better);
        double worseScore = scorer.evaluate(target, GoalType.MAINTAIN, worse);

        assertTrue(betterScore < worseScore);
    }

    @Test
    void shouldPenalizeRepeatedProteinSourceGroups() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(1800);
        target.setTargetProteinG(120);
        target.setTargetFatG(55);
        target.setTargetCarbG(210);

        DayPlanCandidate repeatedProtein = new DayPlanCandidate();
        repeatedProtein.setBreakfast(meal(1L, "燕麦", 2L, "鸡蛋", 3L, "西蓝花", 520, 28, 16, 60));
        repeatedProtein.setLunch(meal(4L, "米饭", 5L, "鸡胸肉", 6L, "生菜", 620, 40, 16, 72));
        repeatedProtein.setDinner(meal(7L, "土豆", 8L, "鸡腿", 9L, "黄瓜", 610, 38, 18, 70));

        DayPlanCandidate diverseProtein = new DayPlanCandidate();
        diverseProtein.setBreakfast(meal(10L, "燕麦", 11L, "鸡蛋", 12L, "西蓝花", 520, 28, 16, 60));
        diverseProtein.setLunch(meal(13L, "米饭", 14L, "鸡胸肉", 15L, "生菜", 620, 40, 16, 72));
        diverseProtein.setDinner(meal(16L, "土豆", 17L, "鳕鱼", 18L, "黄瓜", 610, 38, 18, 70));

        double repeatedScore = scorer.evaluate(target, GoalType.MAINTAIN, repeatedProtein);
        double diverseScore = scorer.evaluate(target, GoalType.MAINTAIN, diverseProtein);

        assertTrue(repeatedScore > diverseScore);
    }

    @Test
    void shouldPenalizeThreeMealsUsingSameStapleGroup() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(1800);
        target.setTargetProteinG(120);
        target.setTargetFatG(55);
        target.setTargetCarbG(210);

        DayPlanCandidate repeatedStaple = new DayPlanCandidate();
        repeatedStaple.setBreakfast(meal(1L, "燕麦片", 2L, "鸡蛋", 3L, "西蓝花", 520, 30, 16, 60));
        repeatedStaple.setLunch(meal(4L, "燕麦饭", 5L, "鸡胸肉", 6L, "生菜", 620, 42, 16, 72));
        repeatedStaple.setDinner(meal(7L, "全麦燕麦粥", 8L, "鱼肉", 9L, "黄瓜", 610, 38, 18, 70));

        DayPlanCandidate mixedStaple = new DayPlanCandidate();
        mixedStaple.setBreakfast(meal(10L, "燕麦片", 11L, "鸡蛋", 12L, "西蓝花", 520, 30, 16, 60));
        mixedStaple.setLunch(meal(13L, "米饭", 14L, "鸡胸肉", 15L, "生菜", 620, 42, 16, 72));
        mixedStaple.setDinner(meal(16L, "红薯", 17L, "鱼肉", 18L, "黄瓜", 610, 38, 18, 70));

        double repeatedScore = scorer.evaluate(target, GoalType.MAINTAIN, repeatedStaple);
        double mixedScore = scorer.evaluate(target, GoalType.MAINTAIN, mixedStaple);

        assertTrue(repeatedScore > mixedScore);
    }

    @Test
    void shouldPenalizeRepeatingSameStapleFoodAcrossMeals() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(2400);
        target.setTargetProteinG(165);
        target.setTargetFatG(65);
        target.setTargetCarbG(300);

        DayPlanCandidate repeatedStapleFood = new DayPlanCandidate();
        repeatedStapleFood.setBreakfast(meal(41L, "全麦面包", 42L, "鸡蛋", 43L, "西蓝花", 620, 32, 16, 82));
        repeatedStapleFood.setLunch(meal(41L, "全麦面包", 44L, "鸡胸肉", 45L, "黄瓜", 880, 48, 18, 118));
        repeatedStapleFood.setDinner(meal(41L, "全麦面包", 46L, "瘦牛肉", 47L, "菠菜", 760, 42, 17, 96));

        DayPlanCandidate mixedStapleFood = new DayPlanCandidate();
        mixedStapleFood.setBreakfast(meal(51L, "燕麦片(生)", 52L, "鸡蛋", 53L, "西蓝花", 620, 32, 16, 82));
        mixedStapleFood.setLunch(meal(54L, "糙米饭", 55L, "鸡胸肉", 56L, "黄瓜", 880, 48, 18, 118));
        mixedStapleFood.setDinner(meal(57L, "红薯(蒸)", 58L, "瘦牛肉", 59L, "菠菜", 760, 42, 17, 96));

        double repeatedScore = scorer.evaluate(target, GoalType.GAIN_MUSCLE, repeatedStapleFood);
        double mixedScore = scorer.evaluate(target, GoalType.GAIN_MUSCLE, mixedStapleFood);

        assertTrue(repeatedScore > mixedScore);
    }

    @Test
    void shouldPreferDifferentLunchDinnerMealTypes() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(2200);
        target.setTargetProteinG(150);
        target.setTargetFatG(65);
        target.setTargetCarbG(260);

        DayPlanCandidate repeatedLunchDinnerType = new DayPlanCandidate();
        repeatedLunchDinnerType.setBreakfast(meal(61L, "Oatmeal", 62L, "Egg", 63L, "Broccoli", 520, 30, 16, 60));
        repeatedLunchDinnerType.setLunch(meal(64L, "Brown Rice", 65L, "Chicken Breast", 66L, "Spinach", 760, 48, 18, 92));
        repeatedLunchDinnerType.setDinner(meal(67L, "Steamed Rice", 68L, "Chicken Leg", 69L, "Lettuce", 760, 48, 18, 92));

        DayPlanCandidate diverseLunchDinnerType = new DayPlanCandidate();
        diverseLunchDinnerType.setBreakfast(meal(71L, "Oatmeal", 72L, "Egg", 73L, "Broccoli", 520, 30, 16, 60));
        diverseLunchDinnerType.setLunch(meal(74L, "Brown Rice", 75L, "Chicken Breast", 76L, "Spinach", 760, 48, 18, 92));
        diverseLunchDinnerType.setDinner(meal(77L, "Sweet Potato", 78L, "Cod", 79L, "Cucumber", 760, 48, 18, 92));

        double repeatedScore = scorer.evaluate(target, GoalType.GAIN_MUSCLE, repeatedLunchDinnerType);
        double diverseScore = scorer.evaluate(target, GoalType.GAIN_MUSCLE, diverseLunchDinnerType);

        assertTrue(repeatedScore > diverseScore);
    }

    private MealSolution meal(
            Long stapleId, String stapleName,
            Long proteinId, String proteinName,
            Long veggieId, String veggieName,
            int calories, double protein, double fat, double carb
    ) {
        MealSolution solution = new MealSolution();
        solution.setStaple(food(stapleId, stapleName));
        solution.setProtein(food(proteinId, proteinName));
        solution.setVeggie(food(veggieId, veggieName));
        solution.setStapleWeight(100);
        solution.setProteinWeight(100);
        solution.setVeggieWeight(150);
        solution.setActualCalories(calories);
        solution.setProteinG(protein);
        solution.setFatG(fat);
        solution.setCarbG(carb);
        return solution;
    }

    private Food food(Long id, String name) {
        Food food = new Food();
        food.setId(id);
        food.setName(name);
        food.setFiber(BigDecimal.valueOf(3));
        food.setSodiumMg(BigDecimal.valueOf(50));
        food.setGiLevel("low");
        return food;
    }
}
