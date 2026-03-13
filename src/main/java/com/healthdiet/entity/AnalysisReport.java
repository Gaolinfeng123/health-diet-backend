package com.healthdiet.entity;

import lombok.Data;

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
}