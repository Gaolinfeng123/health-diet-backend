package com.healthdiet.entity;

import lombok.Data;

@Data
public class AnalysisReport {
    // --- 1. 热量概览 ---
    private Double totalCalories;     // 实际摄入总热量
    private Double recommendCalories; // 推荐摄入量
    private Double diff;              // 热量差值

    // --- 2. 三大营养素 (核心升级) ---
    private Double totalProtein;      // 总蛋白质 (克)
    private Double totalFat;          // 总脂肪 (克)
    private Double totalCarb;         // 总碳水 (克)

    // --- 3. 三餐热量分布 (核心升级) ---
    private Double breakfastCal;      // 早餐摄入热量
    private Double lunchCal;          // 午餐摄入热量
    private Double dinnerCal;         // 晚餐摄入热量
    private Double snackCal;          // 加餐摄入热量

    // --- 4. 智能建议 ---
    private String advice;            // 综合建议
}
