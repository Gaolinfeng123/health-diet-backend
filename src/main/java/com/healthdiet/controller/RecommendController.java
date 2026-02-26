package com.healthdiet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthdiet.common.Result;
import com.healthdiet.entity.Recommendation;
import com.healthdiet.entity.vo.RecommendVO;
import com.healthdiet.service.IRecommendService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 1. 必须有这个注解，Swagger 才能发现它
@RestController
// 2. 必须有这个注解，定义接口前缀
@RequestMapping("/api/recommend")
public class RecommendController {

    @Autowired
    private IRecommendService recommendService;

    /**
     * 获取今日推荐食谱
     * URL: /api/recommend/today?userId=1
     */
    @GetMapping("/today")
    public Result<Object> getRecommend(@RequestParam Long userId, HttpServletRequest request) {
        // 1. 获取当前登录身份
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        // 2. 安全校验 (防止越权)
        // 如果 (我不是管理员) 且 (我想查的人不是我自己) -> 拦截
        if (role != 1 && !userId.equals(tokenUserId)) {
            return Result.error("严重警告：无权查看他人的推荐方案！");
        }

        try {
            Recommendation rec = recommendService.getTodayRecommend(userId);

            // 【关键】把数据库里的 JSON 字符串，转回 Java 对象发给前端
            // 这样前端收到的就是标准的 JSON 结构，而不是一个长字符串
            ObjectMapper mapper = new ObjectMapper();
            RecommendVO vo = mapper.readValue(rec.getResultJson(), RecommendVO.class);

            return Result.success(vo);
        } catch (Exception e) {
            return Result.error("推荐生成失败：" + e.getMessage());
        }
    }
}