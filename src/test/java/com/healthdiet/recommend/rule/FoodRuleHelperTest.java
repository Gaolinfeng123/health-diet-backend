package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.enums.VeggieKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodRuleHelperTest {

    @Test
    void shouldPreferStructuredFoodCategoryBeforeNameGuessing() {
        Food food = baseFood();
        food.setName("Chicken Rice Bowl");
        food.setFoodCategory("vegetable");
        food.setProtein(20.0);
        food.setCarb(35.0);

        assertEquals("vegetable", FoodRuleHelper.normalizeCategory(food));
    }

    @Test
    void shouldDeriveVeggieKindFromStructuredNutritionBeforeName() {
        Food food = baseFood();
        food.setName("Green Daily Pick");
        food.setFoodCategory("vegetable");
        food.setCalories(88.0);
        food.setCarb(16.0);

        assertEquals(VeggieKind.STARCHY, FoodRuleHelper.getVeggieKind(food));
    }

    @Test
    void shouldUseStructuredCategoryForProteinSourceGroup() {
        Food food = baseFood();
        food.setName("Brand X Plain Cup");
        food.setFoodCategory("dairy");

        assertEquals("dairy", FoodRuleHelper.proteinSourceGroup(food));
    }

    @Test
    void shouldUseGiFieldToExcludeHighGiStapleForDiabetes() {
        Food food = baseFood();
        food.setName("Neutral Product");
        food.setFoodCategory("staple");
        food.setGiLevel("high");

        assertTrue(FoodRuleHelper.shouldExclude(food, GoalType.DIABETES_CONTROL));
    }

    @Test
    void shouldRespectStructuredMealFriendlyFlags() {
        Food food = baseFood();
        food.setBreakfastFriendly(0);
        food.setDinnerFriendly(1);

        assertFalse(FoodRuleHelper.isMealFriendly(food, MealType.BREAKFAST));
        assertTrue(FoodRuleHelper.isMealFriendly(food, MealType.DINNER));
    }

    @Test
    void shouldRecognizeChineseStapleGroups() {
        Food oats = baseFood();
        oats.setName("燕麦片(生)");
        oats.setFoodCategory("staple");
        oats.setFiber(BigDecimal.valueOf(10.1));

        Food bagel = baseFood();
        bagel.setName("贝果");
        bagel.setFoodCategory("staple");
        bagel.setSodiumMg(BigDecimal.valueOf(450));

        Food rice = baseFood();
        rice.setName("糙米饭");
        rice.setFoodCategory("staple");

        assertEquals("oat_wheat", FoodRuleHelper.stapleGroup(oats));
        assertEquals("bakery", FoodRuleHelper.stapleGroup(bagel));
        assertEquals("rice", FoodRuleHelper.stapleGroup(rice));
    }

    @Test
    void shouldTreatWholeWheatBreadAsBakeryAndRestrictItForMainMeals() {
        Food bread = baseFood();
        bread.setName("全麦面包");
        bread.setFoodCategory("staple");
        bread.setGiLevel("medium");
        bread.setSodiumMg(BigDecimal.valueOf(467));
        bread.setFiber(BigDecimal.valueOf(6));

        assertEquals("bakery", FoodRuleHelper.stapleGroup(bread));
        assertTrue(FoodRuleHelper.isBreakfastStyleStaple(bread));
        assertTrue(FoodRuleHelper.isRestrictedMainMealStaple(bread));
    }

    private Food baseFood() {
        Food food = new Food();
        food.setCalories(50.0);
        food.setProtein(3.0);
        food.setFat(1.0);
        food.setCarb(8.0);
        food.setFiber(BigDecimal.valueOf(2.0));
        food.setSugar(BigDecimal.valueOf(2.0));
        food.setSodiumMg(BigDecimal.valueOf(50));
        food.setSaturatedFat(BigDecimal.valueOf(1));
        food.setBreakfastFriendly(1);
        food.setDinnerFriendly(1);
        return food;
    }
}
