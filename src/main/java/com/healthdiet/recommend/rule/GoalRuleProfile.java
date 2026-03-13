package com.healthdiet.recommend.rule;

import com.healthdiet.recommend.enums.GoalType;
import com.healthdiet.recommend.enums.MealType;
import com.healthdiet.recommend.model.MealTarget;
import lombok.Getter;

@Getter
public class GoalRuleProfile {

    private final GoalType goalType;

    private final double breakfastProteinRatio;
    private final double breakfastFatRatio;
    private final double breakfastCarbRatio;

    private final double lunchProteinRatio;
    private final double lunchFatRatio;
    private final double lunchCarbRatio;

    private final double dinnerProteinRatio;
    private final double dinnerFatRatio;
    private final double dinnerCarbRatio;

    private final boolean preferLowGi;
    private final boolean avoidHighSodium;
    private final boolean avoidHighSatFat;
    private final boolean restrictDinnerCarb;
    private final boolean restrictStarchyVegAtDinner;

    private GoalRuleProfile(
            GoalType goalType,
            double breakfastProteinRatio, double breakfastFatRatio, double breakfastCarbRatio,
            double lunchProteinRatio, double lunchFatRatio, double lunchCarbRatio,
            double dinnerProteinRatio, double dinnerFatRatio, double dinnerCarbRatio,
            boolean preferLowGi,
            boolean avoidHighSodium,
            boolean avoidHighSatFat,
            boolean restrictDinnerCarb,
            boolean restrictStarchyVegAtDinner
    ) {
        this.goalType = goalType;
        this.breakfastProteinRatio = breakfastProteinRatio;
        this.breakfastFatRatio = breakfastFatRatio;
        this.breakfastCarbRatio = breakfastCarbRatio;
        this.lunchProteinRatio = lunchProteinRatio;
        this.lunchFatRatio = lunchFatRatio;
        this.lunchCarbRatio = lunchCarbRatio;
        this.dinnerProteinRatio = dinnerProteinRatio;
        this.dinnerFatRatio = dinnerFatRatio;
        this.dinnerCarbRatio = dinnerCarbRatio;
        this.preferLowGi = preferLowGi;
        this.avoidHighSodium = avoidHighSodium;
        this.avoidHighSatFat = avoidHighSatFat;
        this.restrictDinnerCarb = restrictDinnerCarb;
        this.restrictStarchyVegAtDinner = restrictStarchyVegAtDinner;
    }

    public static GoalRuleProfile of(GoalType goalType) {
        return switch (goalType) {
            case LOSE_FAT -> new GoalRuleProfile(
                    goalType,
                    0.35, 0.27, 0.38,
                    0.35, 0.27, 0.38,
                    0.38, 0.27, 0.35,
                    false, false, false, true, true
            );
            case GAIN_MUSCLE -> new GoalRuleProfile(
                    goalType,
                    0.28, 0.22, 0.50,
                    0.28, 0.22, 0.50,
                    0.28, 0.22, 0.50,
                    false, false, false, false, false
            );
            case DIABETES_CONTROL -> new GoalRuleProfile(
                    goalType,
                    0.25, 0.30, 0.45,
                    0.25, 0.30, 0.45,
                    0.25, 0.30, 0.45,
                    true, false, false, true, true
            );
            case HYPERTENSION_CONTROL -> new GoalRuleProfile(
                    goalType,
                    0.25, 0.25, 0.50,
                    0.25, 0.25, 0.50,
                    0.25, 0.25, 0.50,
                    false, true, false, false, false
            );
            case HYPERLIPIDEMIA_CONTROL -> new GoalRuleProfile(
                    goalType,
                    0.24, 0.23, 0.53,
                    0.24, 0.23, 0.53,
                    0.24, 0.23, 0.53,
                    false, false, true, false, false
            );
            case MAINTAIN -> new GoalRuleProfile(
                    goalType,
                    0.25, 0.25, 0.50,
                    0.25, 0.25, 0.50,
                    0.25, 0.25, 0.50,
                    false, false, false, false, false
            );
        };
    }

    public MealTarget buildMealTarget(MealType mealType, int mealCalories) {
        double pRatio;
        double fRatio;
        double cRatio;

        switch (mealType) {
            case BREAKFAST -> {
                pRatio = breakfastProteinRatio;
                fRatio = breakfastFatRatio;
                cRatio = breakfastCarbRatio;
            }
            case LUNCH -> {
                pRatio = lunchProteinRatio;
                fRatio = lunchFatRatio;
                cRatio = lunchCarbRatio;
            }
            case DINNER -> {
                pRatio = dinnerProteinRatio;
                fRatio = dinnerFatRatio;
                cRatio = dinnerCarbRatio;
            }
            default -> throw new IllegalStateException("Unexpected value: " + mealType);
        }

        MealTarget target = new MealTarget();
        target.setMealCalories(mealCalories);
        target.setProteinG(mealCalories * pRatio / 4.0);
        target.setFatG(mealCalories * fRatio / 9.0);
        target.setCarbG(mealCalories * cRatio / 4.0);
        return target;
    }
}