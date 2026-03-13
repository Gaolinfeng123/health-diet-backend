package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.enums.VeggieKind;
import com.healthdiet.recommend.model.MealSolution;
import com.healthdiet.recommend.model.MealTarget;

import java.util.Set;

public class RecommendationScorer {

    public MealSolution evaluate(
            GoalRuleProfile profile,
            MealType mealType,
            Food staple, int stapleWeight,
            Food protein, int proteinWeight,
            Food veggie, int veggieWeight,
            MealTarget target,
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

        double score = 0.0;

        // 基础误差
        score += Math.abs(calories - target.getMealCalories()) * 1.5;
        score += Math.abs(proteinG - target.getProteinG()) * 3.5;
        score += Math.abs(fatG - target.getFatG()) * 2.3;
        score += Math.abs(carbG - target.getCarbG()) * 3.2;

        // 通用现实性约束
        if (mealType == MealType.DINNER && calories > target.getMealCalories() + 80) {
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

        // 多样性惩罚
        if (usedFoodIds.contains(staple.getId())) {
            score += 90;
        }
        if (usedFoodIds.contains(protein.getId())) {
            score += 140;
        }
        if (usedFoodIds.contains(veggie.getId())) {
            score += 60;
        }

        // 根茎/淀粉蔬菜惩罚
        VeggieKind veggieKind = FoodRuleHelper.getVeggieKind(veggie);
        if (veggieKind == VeggieKind.STARCHY) {
            score += veggieWeight * 0.18;
            if (mealType == MealType.DINNER) {
                score += 25;
            }
            if (profile.getGoalType() == GoalType.LOSE_FAT || profile.getGoalType() == GoalType.DIABETES_CONTROL) {
                score += 35;
            }
        }

        // 早餐风格
        if (mealType == MealType.BREAKFAST) {
            if (FoodRuleHelper.isGoodBreakfastProtein(protein)) {
                score -= 26;
            }
            if (FoodRuleHelper.isHeavyBreakfastProtein(protein)) {
                score += 35;
            }
            if (FoodRuleHelper.safeName(protein).contains("毛豆")) {
                score += 18;
            }
            if (FoodRuleHelper.isLightLeafyVeg(veggie)) {
                score -= 6;
            }
            if (veggieKind == VeggieKind.STARCHY) {
                score += 12;
            }
        }

        // 目标差异化
        switch (profile.getGoalType()) {
            case LOSE_FAT -> score += scoreLoseFat(mealType, calories, proteinG, fiber, stapleWeight, target);
            case GAIN_MUSCLE -> score += scoreGainMuscle(calories, proteinG, stapleWeight, target);
            case DIABETES_CONTROL -> score += scoreDiabetes(mealType, staple, carbG, fiber, sugar, target);
            case HYPERTENSION_CONTROL -> score += scoreHypertension(protein, sodium, fiber);
            case HYPERLIPIDEMIA_CONTROL -> score += scoreHyperlipidemia(mealType, protein, fatG, satFat, fiber, target);
            case MAINTAIN -> score -= fiber * 0.6;
        }

        MealSolution result = new MealSolution();
        result.setStaple(staple);
        result.setProtein(protein);
        result.setVeggie(veggie);
        result.setStapleWeight(stapleWeight);
        result.setProteinWeight(proteinWeight);
        result.setVeggieWeight(veggieWeight);
        result.setActualCalories((int) Math.round(calories));
        result.setProteinG(proteinG);
        result.setFatG(fatG);
        result.setCarbG(carbG);
        result.setScore(score);
        return result;
    }

    private double scoreLoseFat(
            MealType mealType, double calories, double proteinG, double fiber,
            int stapleWeight, MealTarget target
    ) {
        double score = 0.0;
        if (calories > target.getMealCalories()) {
            score += (calories - target.getMealCalories()) * 1.2;
        }
        if (mealType == MealType.DINNER && stapleWeight > 100) {
            score += (stapleWeight - 100) * 1.2;
        }
        score -= proteinG * 0.4;
        score -= fiber * 1.2;
        return score;
    }

    private double scoreGainMuscle(double calories, double proteinG, int stapleWeight, MealTarget target) {
        double score = 0.0;
        if (calories < target.getMealCalories() - 80) {
            score += (target.getMealCalories() - calories) * 0.8;
        }
        if (proteinG < target.getProteinG()) {
            score += (target.getProteinG() - proteinG) * 4.0;
        }
        if (stapleWeight < 100) {
            score += 20;
        }
        return score;
    }

    private double scoreDiabetes(
            MealType mealType, Food staple, double carbG, double fiber, double sugar, MealTarget target
    ) {
        double score = 0.0;
        String gi = FoodRuleHelper.nullToDefault(staple.getGiLevel(), "medium");

        if ("high".equalsIgnoreCase(gi)) {
            score += 120;
        } else if ("medium".equalsIgnoreCase(gi)) {
            score += 20;
        }

        score += sugar * 1.6;
        score -= fiber * 2.2;

        if (carbG > target.getCarbG() + 12) {
            score += (carbG - target.getCarbG()) * 2.5;
        }

        if ((mealType == MealType.LUNCH || mealType == MealType.DINNER) && carbG > 75) {
            score += (carbG - 75) * 2.0;
        }

        return score;
    }

    private double scoreHypertension(Food protein, double sodium, double fiber) {
        double score = 0.0;
        score += sodium * 0.06;
        if (sodium > 600) {
            score += (sodium - 600) * 0.08;
        }
        score -= fiber * 1.0;

        if (FoodRuleHelper.isNotIdealForHypertension(protein)) {
            score += 25;
        }

        String name = FoodRuleHelper.safeName(protein);
        if (name.contains("鳕鱼") || name.contains("虾仁") || name.contains("豆腐")
                || name.contains("鸡胸") || name.contains("火鸡胸")) {
            score -= 8;
        }

        return score;
    }

    private double scoreHyperlipidemia(
            MealType mealType, Food protein, double fatG, double satFat, double fiber, MealTarget target
    ) {
        double score = 0.0;
        score += satFat * 12.0;
        if (fatG > target.getFatG() + 8) {
            score += (fatG - target.getFatG()) * 3.0;
        }
        score -= fiber * 1.5;

        if (mealType == MealType.BREAKFAST && FoodRuleHelper.isHeavyBreakfastProtein(protein)) {
            score += 20;
        }

        return score;
    }

    private double nutrient(Double per100g, int weightG) {
        return FoodRuleHelper.safe(per100g) * weightG / 100.0;
    }

    private double decimalNutrient(java.math.BigDecimal per100g, int weightG) {
        return FoodRuleHelper.decimalValue(per100g) * weightG / 100.0;
    }
}
