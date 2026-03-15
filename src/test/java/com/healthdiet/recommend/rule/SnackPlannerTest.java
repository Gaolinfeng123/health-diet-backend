package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.NutritionSnapshot;
import com.healthdiet.recommend.model.SnackCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnackPlannerTest {

    private final SnackPlanner planner = new SnackPlanner();

    @Test
    void shouldReturnNullWhenThreeMealsAlreadyCloseToTarget() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(1800);
        target.setTargetProteinG(120);

        NutritionSnapshot current = new NutritionSnapshot(1720, 116, 52, 205, 22);

        SnackCandidate snack = planner.planSnack(List.of(proteinSnack(), fruitSnack()), GoalType.MAINTAIN, target, current);

        assertNull(snack);
    }

    @Test
    void shouldAddProteinSnackWhenProteinGapIsLarge() {
        DailyTargetProfile target = new DailyTargetProfile();
        target.setTargetCalories(2200);
        target.setTargetProteinG(150);

        NutritionSnapshot current = new NutritionSnapshot(1880, 124, 55, 220, 18);

        SnackCandidate snack = planner.planSnack(List.of(fruitSnack(), proteinSnack(), nutSnack()), GoalType.GAIN_MUSCLE, target, current);

        assertNotNull(snack);
        assertEquals("高蛋白酸奶", snack.getFood().getName());
        assertTrue(snack.getReasonTags().contains("SNACK_FOR_PROTEIN_GAP"));
    }

    private Food proteinSnack() {
        Food food = new Food();
        food.setId(1L);
        food.setName("高蛋白酸奶");
        food.setFoodCategory("dairy");
        food.setCalories(90.0);
        food.setProtein(9.0);
        food.setFat(2.0);
        food.setCarb(8.0);
        food.setFiber(BigDecimal.ZERO);
        food.setSugar(BigDecimal.valueOf(6));
        food.setGiLevel("low");
        return food;
    }

    private Food fruitSnack() {
        Food food = new Food();
        food.setId(2L);
        food.setName("苹果");
        food.setFoodCategory("fruit");
        food.setCalories(52.0);
        food.setProtein(0.3);
        food.setFat(0.2);
        food.setCarb(14.0);
        food.setFiber(BigDecimal.valueOf(2.4));
        food.setSugar(BigDecimal.valueOf(10));
        food.setGiLevel("low");
        return food;
    }

    private Food nutSnack() {
        Food food = new Food();
        food.setId(3L);
        food.setName("杏仁");
        food.setFoodCategory("nut");
        food.setCalories(580.0);
        food.setProtein(21.0);
        food.setFat(50.0);
        food.setCarb(22.0);
        food.setFiber(BigDecimal.valueOf(12));
        food.setSugar(BigDecimal.valueOf(4));
        food.setGiLevel("low");
        return food;
    }
}
