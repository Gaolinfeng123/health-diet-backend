package com.healthdiet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.common.Result;
import com.healthdiet.entity.Recommendation;
import com.healthdiet.entity.vo.RecommendVO;
import com.healthdiet.service.IRecommendService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    @Autowired
    private IRecommendService recommendService;

    @GetMapping("/today")
    public Result<Object> getRecommend(@RequestParam Long userId, HttpServletRequest request) {
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        if (role != 1 && !userId.equals(tokenUserId)) {
            return Result.error("无权查看其他用户的推荐结果");
        }

        try {
            Recommendation rec = recommendService.getTodayRecommend(userId);
            ObjectMapper mapper = new ObjectMapper();
            RecommendVO vo = mapper.readValue(rec.getResultJson(), RecommendVO.class);
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("生成推荐失败：" + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public Result<Object> refreshRecommend(@RequestParam Long userId, HttpServletRequest request) {
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        if (role != 1 && !userId.equals(tokenUserId)) {
            return Result.error("无权刷新其他用户的推荐结果");
        }

        try {
            Recommendation rec = recommendService.refreshTodayRecommend(userId);
            ObjectMapper mapper = new ObjectMapper();
            RecommendVO vo = mapper.readValue(rec.getResultJson(), RecommendVO.class);
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("刷新推荐失败：" + e.getMessage());
        }
    }
}
