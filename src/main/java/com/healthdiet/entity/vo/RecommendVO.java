package com.healthdiet.entity.vo;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 对应“行业先进标准”的推荐响应结构
 */
@Data
public class RecommendVO {
    private String date;           // "2026-02-10"
    private Summary summary;       // 概览
    private List<Meal> meals;      // 三餐明细
    private DailySummary dailySummary; // 每日汇总
    private List<String> extraAdvice;  // 额外建议

    // --- 内部嵌套类定义 ---

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Summary {
        private Double bmi;
        private String status;         // underweight, normal, overweight
        private Integer caloriesTarget;
        private String goal;           // lose_fat, maintain, gain_muscle
        private String keyMessage;     // 核心建议
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meal {
        private String type;      // breakfast, lunch...
        private String title;     // "早餐"
        private String menu;      // "全麦吐司..."
        private Integer calories;
        private Macros macros;    // 宏量营养素
        private String advice;    // 单餐建议
        // 暂略 foods 列表以简化逻辑，后续可扩展
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
        private Macros pfcRatio; // 供能比
        private String summaryText;
    }
}