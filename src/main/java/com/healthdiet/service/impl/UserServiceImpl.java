package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.healthdiet.entity.dto.LoginDTO;
import com.healthdiet.entity.dto.RegisterDTO;
import com.healthdiet.entity.User;
import com.healthdiet.mapper.UserMapper;
import com.healthdiet.service.IUserService;
import com.healthdiet.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate redisTemplate;

    // 辅助方法：校验验证码
    private void validateCaptcha(String key, String code) {
        if (key == null || code == null || key.isEmpty() || code.isEmpty()) {
            throw new RuntimeException("请输入验证码");
        }
        String redisKey = "captcha:" + key;
        String realCode = redisTemplate.opsForValue().get(redisKey);

        if (realCode == null) {
            throw new RuntimeException("验证码已过期，请刷新");
        }
        if (!realCode.equalsIgnoreCase(code)) {
            throw new RuntimeException("验证码错误");
        }
        // 验证成功一次后立即删除，防止重复利用
        redisTemplate.delete(redisKey);
    }

    @Override
    public void register(RegisterDTO dto) {
        // 1. 校验验证码 (注册必须校验)
        validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        // 2. 检查用户名是否存在
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", dto.getUsername());
        if (this.getOne(query) != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 3. DTO 转 Entity (这里手动转，保证安全性，role强制为0)
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setHeight(dto.getHeight());
        user.setWeight(dto.getWeight());
        user.setAge(dto.getAge());
        user.setGender(dto.getGender());
        user.setTarget(dto.getTarget());
        user.setRole(0); // 核心安全：强制设为普通用户

        // 4. 保存
        this.save(user);
    }

    @Override
    public String login(LoginDTO dto) {
        String username = dto.getUsername();

        // 1. 检查是否需要验证码 (之前失败过)
        String failKey = "login:fail:" + username;
        Boolean hasFailed = redisTemplate.hasKey(failKey);

        if (Boolean.TRUE.equals(hasFailed)) {
            // 如果有失败记录，必须校验验证码
            validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());
        }

        // 2. 查用户
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", username);
        User user = this.getOne(query);

        // 3. 比对密码
        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            // --- 失败逻辑 ---
            // 记录失败标记，10分钟有效
            redisTemplate.opsForValue().set(failKey, "1", 10, TimeUnit.MINUTES);
            throw new RuntimeException("账号或密码错误");
        }

        // 4. 成功逻辑
        // 清除失败记录
        redisTemplate.delete(failKey);

        // 生成 Token
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        // 存入 Redis (单点登录)
        String redisTokenKey = "token:" + user.getId();
        redisTemplate.opsForValue().set(redisTokenKey, token, 24, TimeUnit.HOURS);

        return token;
    }
}