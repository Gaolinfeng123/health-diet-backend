package com.healthdiet.recommend.rule;

import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.model.DayPlanCandidate;
import com.healthdiet.recommend.model.DailyTargetProfile;
import com.healthdiet.recommend.model.MealSolution;

import java.util.HashMap;
import java.util.Map;

public class DayPlanScorer {

    public double evaluate(DailyTargetProfile targetProfile, GoalType goalType, DayPlanCandidate candidate) {
        MealSolution breakfast = candidate.getBreakfast();
        MealSolution lunch = candidate.getLunch();
        MealSolution dinner = candidate.getDinner();

        double totalCalories = calories(breakfast) + calories(lunch) + calories(dinner);
        double totalProtein = protein(breakfast) + protein(lunch) + protein(dinner);
        double totalFat = fat(breakfast) + fat(lunch) + fat(dinner);
        double totalCarb = carb(breakfast) + carb(lunch) + carb(dinner);
        double totalFiber = fiber(breakfast) + fiber(lunch) + fiber(dinner);

        double score = 0.0;
        score += Math.abs(totalCalories - targetProfile.getTargetCalories()) * 1.8;
        score += Math.abs(totalProtein - targetProfile.getTargetProteinG()) * 3.8;
        score += Math.abs(totalFat - targetProfile.getTargetFatG()) * 2.6;
        score += Math.abs(totalCarb - targetProfile.getTargetCarbG()) * 3.1;
        score -= totalFiber * 1.4;

        score += repeatedFoodPenalty(candidate);
        score += repeatedStapleFoodPenalty(candidate);
        score += repeatedTypePenalty(candidate);
        score += lunchDinnerSimilarityPenalty(candidate);

        if (goalType == GoalType.LOSE_FAT) {
            score += Math.max(0, dinner.getStapleWeight() - 100) * 1.4;
        }
        if (goalType == GoalType.DIABETES_CONTROL) {
            score += highGiPenalty(breakfast, lunch, dinner);
        }
        if (goalType == GoalType.HYPERTENSION_CONTROL) {
            score += sodiumSensitivePenalty(breakfast, lunch, dinner);
        }

        return score;
    }

    private double repeatedFoodPenalty(DayPlanCandidate candidate) {
        double score = 0.0;
        Map<Long, Integer> countMap = new HashMap<>();
        countFoodIds(countMap, candidate.getBreakfast());
        countFoodIds(countMap, candidate.getLunch());
        countFoodIds(countMap, candidate.getDinner());

        for (Integer count : countMap.values()) {
            if (count > 1) {
                score += (count - 1) * 120;
            }
        }
        return score;
    }

    private double repeatedTypePenalty(DayPlanCandidate candidate) {
        double score = 0.0;
        score += duplicateGroupPenalty(
                FoodRuleHelper.proteinSourceGroup(candidate.getBreakfast().getProtein()),
                FoodRuleHelper.proteinSourceGroup(candidate.getLunch().getProtein()),
                FoodRuleHelper.proteinSourceGroup(candidate.getDinner().getProtein()),
                45
        );
        score += stapleRepeatPenalty(candidate);
        score += duplicateGroupPenalty(
                FoodRuleHelper.vegGroup(candidate.getBreakfast().getVeggie()),
                FoodRuleHelper.vegGroup(candidate.getLunch().getVeggie()),
                FoodRuleHelper.vegGroup(candidate.getDinner().getVeggie()),
                18
        );
        return score;
    }

    private double lunchDinnerSimilarityPenalty(DayPlanCandidate candidate) {
        MealSolution lunch = candidate.getLunch();
        MealSolution dinner = candidate.getDinner();
        if (lunch == null || dinner == null || lunch.getStaple() == null || dinner.getStaple() == null) {
            return 0.0;
        }

        double score = 0.0;
        String lunchStapleGroup = FoodRuleHelper.stapleGroup(lunch.getStaple());
        String dinnerStapleGroup = FoodRuleHelper.stapleGroup(dinner.getStaple());
        String lunchProteinGroup = FoodRuleHelper.proteinSourceGroup(lunch.getProtein());
        String dinnerProteinGroup = FoodRuleHelper.proteinSourceGroup(dinner.getProtein());
        String lunchVegGroup = FoodRuleHelper.vegGroup(lunch.getVeggie());
        String dinnerVegGroup = FoodRuleHelper.vegGroup(dinner.getVeggie());

        if (!lunchStapleGroup.isEmpty() && lunchStapleGroup.equals(dinnerStapleGroup)) {
            score += 72;
        }
        if (!lunchProteinGroup.isEmpty() && lunchProteinGroup.equals(dinnerProteinGroup)) {
            score += 88;
        }
        if (!lunchVegGroup.isEmpty() && lunchVegGroup.equals(dinnerVegGroup)) {
            score += 28;
        }
        if (sameFoodId(lunch.getProtein(), dinner.getProtein())) {
            score += 140;
        }
        if (sameFoodId(lunch.getVeggie(), dinner.getVeggie())) {
            score += 80;
        }
        if (sameFoodId(lunch.getStaple(), dinner.getStaple())) {
            score += 150;
        }
        if (mealTypeSignature(lunch).equals(mealTypeSignature(dinner))) {
            score += 170;
        }
        return score;
    }

    private double repeatedStapleFoodPenalty(DayPlanCandidate candidate) {
        double score = 0.0;
        Map<Long, Integer> countMap = new HashMap<>();
        countStapleId(countMap, candidate.getBreakfast());
        countStapleId(countMap, candidate.getLunch());
        countStapleId(countMap, candidate.getDinner());

        for (Map.Entry<Long, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() > 1) {
                score += (entry.getValue() - 1) * 180;
                if (entry.getValue() >= 3) {
                    score += 180;
                }
            }
        }
        return score;
    }

    private double stapleRepeatPenalty(DayPlanCandidate candidate) {
        String breakfastGroup = FoodRuleHelper.stapleGroup(candidate.getBreakfast().getStaple());
        String lunchGroup = FoodRuleHelper.stapleGroup(candidate.getLunch().getStaple());
        String dinnerGroup = FoodRuleHelper.stapleGroup(candidate.getDinner().getStaple());

        double score = duplicateGroupPenalty(breakfastGroup, lunchGroup, dinnerGroup, 60);
        if (!breakfastGroup.isEmpty() && breakfastGroup.equals(lunchGroup) && lunchGroup.equals(dinnerGroup)) {
            score += 180;
            if ("oat_wheat".equals(breakfastGroup) || "bakery".equals(breakfastGroup)) {
                score += 120;
            }
        }
        return score;
    }

    private double duplicateGroupPenalty(String first, String second, String third, double weight) {
        Map<String, Integer> countMap = new HashMap<>();
        countGroup(countMap, first);
        countGroup(countMap, second);
        countGroup(countMap, third);

        double score = 0.0;
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            if (entry.getKey().isEmpty()) {
                continue;
            }
            if (entry.getValue() > 1) {
                score += (entry.getValue() - 1) * weight;
                if (entry.getValue() >= 3) {
                    score += weight * 3.5;
                }
            }
        }
        return score;
    }

    private void countGroup(Map<String, Integer> countMap, String group) {
        if (group == null) {
            return;
        }
        countMap.merge(group, 1, Integer::sum);
    }

    private double highGiPenalty(MealSolution... meals) {
        double score = 0.0;
        for (MealSolution meal : meals) {
            String gi = FoodRuleHelper.nullToDefault(meal.getStaple().getGiLevel(), "medium");
            if ("high".equalsIgnoreCase(gi)) {
                score += 80;
            } else if ("medium".equalsIgnoreCase(gi)) {
                score += 12;
            }
        }
        return score;
    }

    private double sodiumSensitivePenalty(MealSolution... meals) {
        double score = 0.0;
        for (MealSolution meal : meals) {
            score += FoodRuleHelper.decimalValue(meal.getStaple().getSodiumMg()) * meal.getStapleWeight() / 100.0 * 0.015;
            score += FoodRuleHelper.decimalValue(meal.getProtein().getSodiumMg()) * meal.getProteinWeight() / 100.0 * 0.02;
            score += FoodRuleHelper.decimalValue(meal.getVeggie().getSodiumMg()) * meal.getVeggieWeight() / 100.0 * 0.01;
        }
        return score;
    }

    private void countFoodIds(Map<Long, Integer> countMap, MealSolution meal) {
        countMap.merge(meal.getStaple().getId(), 1, Integer::sum);
        countMap.merge(meal.getProtein().getId(), 1, Integer::sum);
        countMap.merge(meal.getVeggie().getId(), 1, Integer::sum);
    }

    private void countStapleId(Map<Long, Integer> countMap, MealSolution meal) {
        if (meal == null || meal.getStaple() == null || meal.getStaple().getId() == null) {
            return;
        }
        Long stapleId = meal.getStaple().getId();
        if (stapleId <= 0) {
            return;
        }
        countMap.merge(stapleId, 1, Integer::sum);
    }

    private boolean sameFoodId(com.healthdiet.entity.Food first, com.healthdiet.entity.Food second) {
        if (first == null || second == null || first.getId() == null || second.getId() == null) {
            return false;
        }
        return first.getId().equals(second.getId()) && first.getId() > 0;
    }

    private String mealTypeSignature(MealSolution meal) {
        return FoodRuleHelper.stapleGroup(meal.getStaple()) + "|"
                + FoodRuleHelper.proteinSourceGroup(meal.getProtein()) + "|"
                + FoodRuleHelper.vegGroup(meal.getVeggie());
    }

    private double calories(MealSolution meal) {
        return meal.getActualCalories();
    }

    private double protein(MealSolution meal) {
        return meal.getProteinG();
    }

    private double fat(MealSolution meal) {
        return meal.getFatG();
    }

    private double carb(MealSolution meal) {
        return meal.getCarbG();
    }

    private double fiber(MealSolution meal) {
        return FoodRuleHelper.decimalValue(meal.getStaple().getFiber()) * meal.getStapleWeight() / 100.0
                + FoodRuleHelper.decimalValue(meal.getProtein().getFiber()) * meal.getProteinWeight() / 100.0
                + FoodRuleHelper.decimalValue(meal.getVeggie().getFiber()) * meal.getVeggieWeight() / 100.0;
    }
}
