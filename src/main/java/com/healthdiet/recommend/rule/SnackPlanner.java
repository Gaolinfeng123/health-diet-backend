package com.healthdiet.recommend.rule;

import com.healthdiet.entity.Food;
import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.NutritionSnapshot;
import com.healthdiet.recommend.model.SnackCandidate;

import java.util.List;

public class SnackPlanner {

    public SnackCandidate planSnack(List<Food> foods, GoalType goalType, DailyTargetProfile targetProfile, NutritionSnapshot current) {
        if (foods == null || foods.isEmpty()) {
            return null;
        }

        double calorieGap = targetProfile.getTargetCalories() - current.getCalories();
        double proteinGap = targetProfile.getTargetProteinG() - current.getProteinG();
        double fiberGap = Math.max(0.0, 25.0 - current.getFiberG());
        if (!shouldAddSnack(goalType, calorieGap, proteinGap, fiberGap)) {
            return null;
        }

        SnackCandidate best = null;
        for (Food food : foods) {
            if (!isSnackFriendly(food, goalType)) {
                continue;
            }
            int maxWeight = maxSnackWeight(food, goalType);
            int minWeight = minSnackWeight(food);
            for (int weight = minWeight; weight <= maxWeight; weight += 20) {
                SnackCandidate candidate = evaluate(food, weight, goalType, calorieGap, proteinGap, fiberGap);
                if (best == null || candidate.getScore() < best.getScore()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    public boolean shouldAddSnack(GoalType goalType, double calorieGap, double proteinGap, double fiberGap) {
        if (calorieGap >= 220) {
            return true;
        }
        if (proteinGap >= 18) {
            return true;
        }
        if (fiberGap >= 8) {
            return true;
        }
        if (goalType == GoalType.GAIN_MUSCLE && (calorieGap >= 140 || proteinGap >= 12)) {
            return true;
        }
        return goalType == GoalType.DIABETES_CONTROL && calorieGap >= 160;
    }

    private SnackCandidate evaluate(Food food, int weight, GoalType goalType, double calorieGap, double proteinGap, double fiberGap) {
        SnackCandidate candidate = new SnackCandidate();
        candidate.setFood(food);
        candidate.setWeightG(weight);
        candidate.setCalories((int) Math.round(per100(food.getCalories()) * weight / 100.0));
        candidate.setProteinG(round1(per100(food.getProtein()) * weight / 100.0));
        candidate.setFatG(round1(per100(food.getFat()) * weight / 100.0));
        candidate.setCarbG(round1(per100(food.getCarb()) * weight / 100.0));
        candidate.setFiberG(round1(FoodRuleHelper.decimalValue(food.getFiber()) * weight / 100.0));

        double score = 0.0;
        score += Math.abs(candidate.getCalories() - clamp(calorieGap, 100, 220)) * 1.4;
        score += Math.abs(candidate.getProteinG() - clamp(proteinGap, 0, 18)) * 2.2;
        score -= Math.min(candidate.getFiberG(), fiberGap) * 2.0;

        String category = FoodRuleHelper.normalizeCategory(food);
        if ("nut".equals(category) && candidate.getCalories() > 180) {
            score += 35;
        }
        if ("fruit".equals(category) && candidate.getProteinG() < 2 && proteinGap >= 15) {
            score += 28;
        }

        if (goalType == GoalType.DIABETES_CONTROL) {
            String gi = FoodRuleHelper.nullToDefault(food.getGiLevel(), "medium");
            if ("high".equalsIgnoreCase(gi)) {
                score += 100;
            } else if ("medium".equalsIgnoreCase(gi)) {
                score += 15;
            }
            score += FoodRuleHelper.decimalValue(food.getSugar()) * weight / 100.0 * 1.6;
        }
        if (goalType == GoalType.HYPERTENSION_CONTROL) {
            score += FoodRuleHelper.decimalValue(food.getSodiumMg()) * weight / 100.0 * 0.05;
        }
        if (goalType == GoalType.HYPERLIPIDEMIA_CONTROL) {
            score += FoodRuleHelper.decimalValue(food.getSaturatedFat()) * weight / 100.0 * 8.0;
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            score -= candidate.getProteinG() * 0.8;
        }

        addReasonTags(candidate, goalType, calorieGap, proteinGap, fiberGap);
        candidate.setScore(score);
        return candidate;
    }

    private void addReasonTags(SnackCandidate candidate, GoalType goalType, double calorieGap, double proteinGap, double fiberGap) {
        if (proteinGap >= 12 && candidate.getProteinG() >= 8) {
            candidate.addReasonTag("SNACK_FOR_PROTEIN_GAP");
        }
        if (calorieGap >= 180) {
            candidate.addReasonTag("SNACK_FOR_ENERGY_GAP");
        }
        if (fiberGap >= 6 && candidate.getFiberG() >= 3) {
            candidate.addReasonTag("SNACK_FOR_FIBER_GAP");
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            candidate.addReasonTag("SNACK_LOW_GI_PRIORITY");
        }
        if (goalType == GoalType.GAIN_MUSCLE) {
            candidate.addReasonTag("SNACK_GAIN_MUSCLE_SUPPORT");
        }
    }

    private boolean isSnackFriendly(Food food, GoalType goalType) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if (!"fruit".equals(category) && !"nut".equals(category) && !"protein".equals(category) && !"dairy".equals(category)) {
            return false;
        }
        if ("nut".equals(category) && goalType == GoalType.LOSE_FAT && per100(food.getCalories()) > 650) {
            return false;
        }
        if (goalType == GoalType.DIABETES_CONTROL && "high".equalsIgnoreCase(FoodRuleHelper.nullToDefault(food.getGiLevel(), "medium"))) {
            return false;
        }
        return true;
    }

    private int maxSnackWeight(Food food, GoalType goalType) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if ("nut".equals(category)) {
            return 40;
        }
        if ("dairy".equals(category)) {
            return 250;
        }
        if ("protein".equals(category)) {
            return goalType == GoalType.GAIN_MUSCLE ? 180 : 140;
        }
        return 220;
    }

    private int minSnackWeight(Food food) {
        String category = FoodRuleHelper.normalizeCategory(food);
        if ("nut".equals(category)) {
            return 20;
        }
        if ("protein".equals(category)) {
            return 60;
        }
        return 80;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double per100(Double value) {
        return value == null ? 0.0 : value;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
