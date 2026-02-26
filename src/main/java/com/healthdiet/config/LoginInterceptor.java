package com.healthdiet.config;

import com.healthdiet.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 token
        String token = request.getHeader("Authorization");

        // 2. 判空
        if (!StringUtils.hasText(token)) {
            response.setStatus(401);
            response.getWriter().write("Unauth: No Token");
            return false;
        }

        // 3. 解析 Token
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            response.setStatus(401);
            response.getWriter().write("Unauth: Invalid Token");
            return false;
        }

        // 4. Redis 双重验证 (验证 Token 是否有效/未过期)
        // 注意：JWT解析出的 userId 可能是 Integer，需要根据实际情况转换
        Integer userIdInt = (Integer) claims.get("userId");
        if (userIdInt == null) {
            response.setStatus(401);
            response.getWriter().write("Unauth: Token Error (No UserId)");
            return false;
        }

        Long userId = Long.valueOf(userIdInt);
        String redisKey = "token:" + userId;
        String redisToken = redisTemplate.opsForValue().get(redisKey);

        if (redisToken == null || !redisToken.equals(token)) {
            response.setStatus(401);
            response.getWriter().write("Unauth: Token Expired or Logged out");
            return false;
        }

        // 5. 【严格模式】获取 Role 角色
        Integer role = (Integer) claims.get("role");

        // 如果 Token 里没有 role (说明是旧代码生成的 Token)，直接拒绝
        if (role == null) {
            response.setStatus(401);
            response.getWriter().write("Unauth: Token Version Mismatch (Please Login Again)");
            return false;
        }

        // 6. 将 userId 和 role 存入 request，供后面的 Controller 使用
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);

        return true; // 放行
    }
}