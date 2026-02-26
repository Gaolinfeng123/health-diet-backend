package com.healthdiet.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // 记得导入这个
import com.healthdiet.common.Result;
import com.healthdiet.entity.Food;
import com.healthdiet.service.IFoodService;
import jakarta.servlet.http.HttpServletRequest; // 导入 request
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    @Autowired
    private IFoodService foodService;

    /**
     * 1. 获取食物列表 (分页版)
     * URL: /api/food/list?pageNum=1&pageSize=10&keyword=米饭
     */
    @GetMapping("/list")
    public Result<IPage<Food>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {

        // 1. 定义分页对象
        Page<Food> page = new Page<>(pageNum, pageSize);

        // 2. 定义查询条件
        QueryWrapper<Food> query = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            query.like("name", keyword); // 模糊查询
        }

        // 3. 执行分页查询 (MyBatis-Plus 自带的 page 方法)
        IPage<Food> result = foodService.page(page, query);

        return Result.success(result);
    }

    // --- 2. 新增食物 (必须是管理员！) ---
    @PostMapping("/add")
    public Result<String> add(@RequestBody Food food, HttpServletRequest request) {
        // 【关键步骤】从 request 里取出拦截器存进去的 role
        Integer role = (Integer) request.getAttribute("role");

        // 判断：如果 role 不是 1，直接拒绝
        if (role == null || role != 1) {
            return Result.error("无权操作：只有管理员可以添加食物");
        }

        boolean save = foodService.save(food);
        return save ? Result.success("添加成功") : Result.error("添加失败");
    }

    // --- 3. 删除食物 (必须是管理员！) ---
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        // 【关键步骤】权限判断
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 1) {
            return Result.error("无权操作：只有管理员可以删除食物");
        }

        boolean remove = foodService.removeById(id);
        return remove ? Result.success("删除成功") : Result.error("删除失败");
    }
}