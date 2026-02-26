package com.healthdiet.service;

import com.healthdiet.entity.AnalysisReport;

public interface IAnalysisService {
    // 生成某天的分析报告
    AnalysisReport analyze(Long userId, String date);
}
