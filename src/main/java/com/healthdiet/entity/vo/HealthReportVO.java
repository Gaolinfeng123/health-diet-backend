package com.healthdiet.entity.vo;

import lombok.Data;
import java.util.Map;

/**
 * 推荐算法流水线的数据载体
 * 用于在 MetricsCalc -> Analysis -> Plan -> Meals 之间传递中间结果
 */
@Data
public class HealthReportVO {
    // --- 1. 身体指标 (Metrics) ---
    private Double bmi;          // 身体质量指数
    private Double idealWeight;  // 理想体重 (kg)
    private Double bmr;          // 基础代谢 (kcal)
    private Double tdee;         // 每日总消耗 (kcal)

    // --- 2. 健康分析 (Analysis) ---
    private String status;       // 状态 (偏瘦/标准/超重/肥胖)
    private String risk;         // 健康风险提示

    // --- 3. 营养方案 (Plan) ---
    private Double targetCalories; // 每日建议摄入热量
    private Double proteinGram;    // 蛋白质 (g)
    private Double fatGram;        // 脂肪 (g)
    private Double carbGram;       // 碳水 (g)
    private Map<String, Double> macroRatio; // 供能比 (例如 protein=0.3)

    // --- 4. 推荐结果 (Meals - 最终要存入数据库的字段) ---
    private String breakfast;
    private String lunch;
    private String dinner;
    private String summary;        // 综合建议文案
}