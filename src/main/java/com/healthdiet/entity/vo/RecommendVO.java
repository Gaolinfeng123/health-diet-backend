package com.healthdiet.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class RecommendVO {
    private String date;
    private Summary summary;
    private List<Meal> meals;
    private DailySummary dailySummary;
    private List<String> extraAdvice;
    private RefreshInfo refreshInfo;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Summary {
        private Double bmi;
        private String status;
        private Integer caloriesTarget;
        private Double activityFactor;
        private Double tdee;
        private String goal;
        private String keyMessage;
        private List<String> reasons;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meal {
        private String type;
        private String title;
        private String menu;
        private Integer calories;
        private Macros macros;
        private String advice;
        private List<String> reasons;
        private Boolean locked;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Macros {
        private Double protein;
        private Double fat;
        private Double carbs;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailySummary {
        private Integer totalCalories;
        private Macros totalMacros;
        private Macros pfcRatio;
        private String summaryText;
        private List<String> reasons;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RefreshInfo {
        private Boolean refreshed;
        private List<String> lockedMeals;
        private String message;
    }
}
