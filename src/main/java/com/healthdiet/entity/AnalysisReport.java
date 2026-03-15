package com.healthdiet.entity;

import lombok.Data;

import java.util.List;

@Data
public class AnalysisReport {

    /**
     * 分析对应日期
     */
    private String analysisDate;

    /**
     * 用户当前BMI和状态（用于页面展示）
     */
    private Double bmi;
    private String status;
    private Double activityFactor;
    private Double tdee;

    /**
     * 实际总热量、目标总热量、差值
     */
    private Double totalCalories;
    private Double recommendCalories;
    private Double diff;

    /**
     * 实际宏量营养素（g）
     */
    private Double totalProtein;
    private Double totalFat;
    private Double totalCarb;

    /**
     * 目标宏量营养素（g）
     */
    private Double targetProtein;
    private Double targetFat;
    private Double targetCarb;

    /**
     * 实际PFC占比
     */
    private MacrosRatio actualPfcRatio;

    /**
     * 目标PFC占比
     */
    private MacrosRatio targetPfcRatio;

    /**
     * 三餐/加餐热量分布
     */
    private Double breakfastCal;
    private Double lunchCal;
    private Double dinnerCal;
    private Double snackCal;

    /**
     * 分析建议
     */
    private String advice;
    private String reportTitle;
    private String overview;
    private List<String> highlights;
    private EnergyAssessment energyAssessment;
    private List<NutrientAssessment> nutrientAssessments;
    private List<MealAssessment> mealAssessments;
    private List<String> suggestions;
    private List<String> quickQuestions;

    @Data
    public static class MacrosRatio {
        private Double protein;
        private Double fat;
        private Double carbs;

        public MacrosRatio() {}

        public MacrosRatio(Double protein, Double fat, Double carbs) {
            this.protein = protein;
            this.fat = fat;
            this.carbs = carbs;
        }
    }
    @Data
    public static class EnergyAssessment {
        private Double actual;
        private Double target;
        private Double diff;
        private String status;
        private String comment;
    }

    @Data
    public static class NutrientAssessment {
        private String nutrient;
        private Double actual;
        private Double target;
        private Double diff;
        private String status;
        private String comment;
    }

    @Data
    public static class MealAssessment {
        private String type;
        private String title;
        private Double calories;
        private Double targetCalories;
        private Double share;
        private String status;
        private String comment;
    }
}
