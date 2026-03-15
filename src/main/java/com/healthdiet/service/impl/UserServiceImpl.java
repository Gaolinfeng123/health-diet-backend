package com.healthdiet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.healthdiet.entity.User;
import com.healthdiet.entity.dto.LoginDTO;
import com.healthdiet.entity.dto.RegisterDTO;
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
        redisTemplate.delete(redisKey);
    }

    @Override
    public void register(RegisterDTO dto) {
        validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", dto.getUsername());
        if (this.getOne(query) != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setHeight(dto.getHeight());
        user.setWeight(dto.getWeight());
        user.setAge(dto.getAge());
        user.setGender(dto.getGender());
        user.setTarget(dto.getTarget());
        user.setActivityLevel(dto.getActivityLevel() == null ? 1 : dto.getActivityLevel());
        user.setRole(0);

        this.save(user);
    }

    @Override
    public String login(LoginDTO dto) {
        String username = dto.getUsername();
        String failKey = "login:fail:" + username;
        Boolean hasFailed = redisTemplate.hasKey(failKey);

        if (Boolean.TRUE.equals(hasFailed)) {
            validateCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());
        }

        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", username);
        User user = this.getOne(query);

        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            redisTemplate.opsForValue().set(failKey, "1", 10, TimeUnit.MINUTES);
            throw new RuntimeException("账号或密码错误");
        }

        redisTemplate.delete(failKey);

        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        String redisTokenKey = "token:" + user.getId();
        redisTemplate.opsForValue().set(redisTokenKey, token, 24, TimeUnit.HOURS);
        return token;
    }
}
