package com.healthdiet.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    // 密钥，绝对不能泄露给前端
    private static final String SECRET_KEY = "health_diet_secret_key_888";
    // 过期时间 24小时
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    // 生成 Token
    public String createToken(Long userId, String username, Integer role) { // 加了 role 参数
        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("role", role) // 把角色存进 Token
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
                .compact();
    }

    // 解析 Token 获取 Claims (包含userId等信息)
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null; // 解析失败（比如过期、被篡改）
        }
    }
}
