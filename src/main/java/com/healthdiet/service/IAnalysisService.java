package com.healthdiet.service;

import com.healthdiet.entity.AnalysisReport;
import com.healthdiet.entity.vo.TrendPointVO;

import java.util.List;

public interface IAnalysisService {

    AnalysisReport analyze(Long userId, String date);

    /**
     * 获取近N天热量摄入趋势
     * @param userId 用户ID
     * @param days 天数，例如 7
     * @return 按日期升序返回趋势点
     */
    List<TrendPointVO> getCalorieTrend(Long userId, int days);
}
