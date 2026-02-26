package com.healthdiet.controller;

import com.healthdiet.common.Result;
import com.healthdiet.entity.dto.ChatRequest;
import com.healthdiet.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired private AiService aiService;

    // 1. 进页面先调用：获取或生成今日那份唯一的“精简评估报告”
    @GetMapping("/daily-report")
    public Result<String> getReport(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(aiService.getOrGenerateDailyReport(userId));
    }

    // 2. 提问调用：基于报告上下文进行聊天
    @PostMapping("/stream")
    public SseEmitter chat(@RequestBody ChatRequest chatRequest, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return aiService.streamChat(userId, chatRequest.getMessage());
    }
}