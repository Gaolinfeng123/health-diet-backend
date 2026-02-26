package com.healthdiet.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.healthdiet.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 获取图形验证码
     * 返回: image(Base64字符串), key(本次验证码的唯一标识)
     */
    @GetMapping("/captcha")
    public Result<Map<String, String>> getCaptcha() {
        // 1. 生成验证码 (宽, 高, 位数, 干扰线数量)
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);

        // 2. 生成一个唯一 Key (UUID)
        String key = UUID.randomUUID().toString();
        String code = captcha.getCode(); // 真实答案

        // 3. 存入 Redis，有效期 2 分钟
        // Key: captcha:uuid, Value: 答案
        redisTemplate.opsForValue().set("captcha:" + key, code, 2, TimeUnit.MINUTES);

        // 4. 返回给前端
        Map<String, String> map = new HashMap<>();
        map.put("key", key);
        map.put("image", captcha.getImageBase64Data()); // 返回 Base64 图片数据

        return Result.success(map);
    }
}
