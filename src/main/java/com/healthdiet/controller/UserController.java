package com.healthdiet.controller;

import com.healthdiet.common.Result;
import com.healthdiet.entity.dto.LoginDTO;
import com.healthdiet.entity.dto.RegisterDTO;
import com.healthdiet.entity.dto.UpdatePasswordDTO;
import com.healthdiet.entity.User;
import com.healthdiet.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 1. 用户注册
     * 使用 RegisterDTO 接收参数，包含验证码字段
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Valid RegisterDTO dto) {
        try {
            // 这里的安全校验逻辑（如强制role=0）已经下沉到 ServiceImpl 里了
            userService.register(dto);
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 2. 用户登录
     * 使用 LoginDTO 接收参数，支持验证码
     */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody LoginDTO dto) {
        try {
            String token = userService.login(dto);
            return Result.success(token);
        } catch (Exception e) {
            // --- 登录失败反馈逻辑 ---
            // 检查 Redis 是否有失败记录，如果有，告诉前端下次要弹验证码
            String failKey = "login:fail:" + dto.getUsername();
            boolean needCaptcha = Boolean.TRUE.equals(redisTemplate.hasKey(failKey));

            // 构建返回数据
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("needCaptcha", needCaptcha);

            Result<Object> result = Result.error(e.getMessage());
            result.setData(errorData); // 将标记放在 data 里
            return result;
        }
    }

    /**
     * 3. 获取个人信息
     */
    @GetMapping("/info")
    public Result<User> getInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userService.getById(userId);
        if (user != null) {
            user.setPassword(null); // 脱敏
        }
        return Result.success(user);
    }

    /**
     * 4. 修改个人基础信息
     */
    @PostMapping("/update")
    public Result<String> updateInfo(@RequestBody @Valid User user, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 强制覆盖 ID，防止修改他人
        user.setId(userId);
        // 敏感字段置空，防止通过此接口修改
        user.setUsername(null);
        user.setRole(null);
        user.setPassword(null); // 修改密码请走专用接口

        boolean success = userService.updateById(user);
        return success ? Result.success("信息修改成功") : Result.error("修改失败");
    }

    /**
     * 5. 修改密码专用接口
     */
    @PostMapping("/updatePassword")
    public Result<String> updatePassword(@RequestBody UpdatePasswordDTO params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        String oldPassword = params.getOldPassword();
        String newPassword = params.getNewPassword();

        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return Result.error("参数错误：密码不能为空且长度不能少于6位");
        }

        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (!user.getPassword().equals(oldPassword)) {
            return Result.error("旧密码错误，请重新输入");
        }

        // 更新密码
        user.setPassword(newPassword);
        userService.updateById(user);

        // 强制下线
        redisTemplate.delete("token:" + userId);

        return Result.success("密码修改成功，请重新登录");
    }
}