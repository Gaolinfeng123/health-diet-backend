package com.healthdiet.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.healthdiet.common.Result;
import com.healthdiet.entity.User;
import com.healthdiet.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

@RestController
@RequestMapping("/api/admin/user")
public class AdminController {

    @Autowired
    private IUserService userService;

    // --- 辅助方法：检查是不是管理员 ---
    private void checkAdmin(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            throw new RuntimeException("无权操作：需要管理员权限");
        }
    }

    /**
     * 1. 获取用户列表 (分页版)
     * URL: /api/admin/user/list?pageNum=1&pageSize=10&username=张
     */
    @GetMapping("/list")
    public Result<IPage<User>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            HttpServletRequest request) {

        checkAdmin(request); // 鉴权

        // 1. 定义分页对象
        Page<User> page = new Page<>(pageNum, pageSize);

        // 2. 定义查询条件
        QueryWrapper<User> query = new QueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            query.like("username", username);
        }

        // 3. 执行分页查询
        IPage<User> result = userService.page(page, query);

        return Result.success(result);
    }

    // 2. 新增用户 (管理员专用接口)
    // 改动点：不再调用 userService.register，而是直接 save，绕过验证码检查
    @PostMapping("/add")
    public Result<String> add(@RequestBody User user, HttpServletRequest request) {
        checkAdmin(request);

        // 1. 手动检查用户名是否存在
        QueryWrapper<User> query = new QueryWrapper<>();
        query.eq("username", user.getUsername());
        if (userService.getOne(query) != null) {
            return Result.error("创建失败：用户名已存在");
        }

        // 2. 直接保存 (允许管理员指定 role, target 等任意字段)
        // 管理员创建的用户不需要经过那些 "强制设为0" 的安全逻辑
        boolean success = userService.save(user);

        return success ? Result.success("用户创建成功") : Result.error("创建失败");
    }

    // 3. 修改用户信息
    @PostMapping("/update")
    public Result<String> update(@RequestBody User user, HttpServletRequest request) {
        checkAdmin(request);

        if (user.getId() == null) {
            return Result.error("用户ID不能为空");
        }

        // 禁止修改用户名 (因为是唯一键)
        user.setUsername(null);

        boolean update = userService.updateById(user);
        return update ? Result.success("修改成功") : Result.error("修改失败");
    }

    // 4. 删除用户
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        checkAdmin(request);
        boolean remove = userService.removeById(id);
        return remove ? Result.success("删除成功") : Result.error("删除失败");
    }
}