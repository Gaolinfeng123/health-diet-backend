package com.healthdiet.controller;

import com.healthdiet.common.Result;
import com.healthdiet.entity.AnalysisReport;
import com.healthdiet.service.IAnalysisService;
import jakarta.servlet.http.HttpServletRequest; // 1. 记得导入这个包
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private IAnalysisService analysisService;

    // 获取分析报告
    // URL: /api/analysis/report?userId=1&date=2023-10-27
    @GetMapping("/report")
    public Result<AnalysisReport> getReport(
            @RequestParam Long userId,
            @RequestParam String date,
            HttpServletRequest request) { // 2. 增加 request 参数

        // --- 安全校验逻辑 (核心) ---

        // 1. 获取当前登录人的身份
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        // 2. 权限判断
        // 如果 (我不是管理员) 并且 (我想查的人不是我自己) -> 报警
        if (role != 1 && !userId.equals(tokenUserId)) {
            return Result.error("严重警告：无权查看他人的健康报告！");
        }

        // --- 业务逻辑 ---

        try {
            AnalysisReport report = analysisService.analyze(userId, date);
            return Result.success(report);
        } catch (Exception e) {
            // 比如查不到用户，或者日期格式写错
            return Result.error("分析失败：" + e.getMessage());
        }
    }
}