package com.healthdiet.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.healthdiet.common.Result;
import com.healthdiet.entity.DietRecord;
import com.healthdiet.service.IDietRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/diet")
public class DietRecordController {

    @Autowired
    private IDietRecordService dietRecordService;

    // 1. 添加记录
    @PostMapping("/add")
    public Result<String> addRecord(@RequestBody @Valid DietRecord record, HttpServletRequest request) {

        // 1. 获取身份信息
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        // 2. 【核心修改】区分 管理员 和 普通用户 的逻辑
        if (role == 1) {
            // --- 管理员逻辑 ---
            // 管理员必须指定 userId，否则不知道是帮谁记
            if (record.getUserId() == null) {
                return Result.error("管理员操作必须指定目标用户 ID");
            }
            // 管理员允许 userId != tokenUserId，不做拦截，直接信任前端传的 record.getUserId()

        } else {
            // --- 普通用户逻辑 ---
            // 严防死守：如果试图帮别人记，直接报错
            if (record.getUserId() != null && !record.getUserId().equals(tokenUserId)) {
                return Result.error("严重警告：非法操作！禁止使用他人 ID 添加记录");
            }
            // 强制设置为自己的 ID (防止前端传 null)
            record.setUserId(tokenUserId);
        }

        // 3. 日期检查 (所有人都要遵守，包括管理员，不能记录未来的饮食)
        if (record.getDate().isAfter(java.time.LocalDate.now())) {
            return Result.error("非法操作：不能记录未来的饮食");
        }

        // 4. 执行保存
        boolean save = dietRecordService.save(record);
        return save ? Result.success("记录成功") : Result.error("记录失败");
    }

    // 2. 查询某人的记录
    /**
     * 分页查询饮食记录
     * URL: /api/diet/list?pageNum=1&pageSize=10  (基础用法)
     * URL: /api/diet/list?pageNum=1&userId=2     (查特定用户)
     * URL: /api/diet/list?date=2023-10-27        (查特定日期)
     */
    @GetMapping("/list")
    public Result<IPage<DietRecord>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long userId, // 变为非必填
            @RequestParam(required = false) String date,
            HttpServletRequest request) {

        // 1. 获取当前登录身份
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        // 2. 确定最终要查询的 queryUserId
        Long queryUserId = null;

        if (role == 1) {
            // --- 管理员逻辑 ---
            // 如果前端传了 userId，就查那个人的；如果不传，queryUserId 保持为 null (代表查所有人)
            queryUserId = userId;
        } else {
            // --- 普通用户逻辑 ---
            // 无论前端传没传，或者传了什么，强制查自己
            // 这样既支持不传 ID，也防止了水平越权
            queryUserId = tokenUserId;

            // 如果普通用户非要查别人 (参数里带了 userId 且不等于自己)，给个警告日志或直接无视
            // 这里我们选择逻辑严谨：如果显式传了别人的 ID，报错提示
            if (userId != null && !userId.equals(tokenUserId)) {
                return Result.error("严重警告：无权查看他人数据");
            }
        }

        // 3. 调用 Service 分页查询
        IPage<DietRecord> pageResult = dietRecordService.listRecordsByPage(pageNum, pageSize, queryUserId, date);

        return Result.success(pageResult);
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        // 1. 获取当前操作人的身份信息
        Long tokenUserId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");

        // 2. 查出记录详情 (必须先查出来，否则不知道这记录是谁的)
        DietRecord record = dietRecordService.getById(id);
        if (record == null) {
            return Result.error("记录不存在");
        }

        // 3. 【安全校验】如果是普通用户 (非管理员)
        if (role != 1) {
            // A. 查户口：这记录是不是你写的？
            if (!record.getUserId().equals(tokenUserId)) {
                return Result.error("严重警告：无权删除他人的记录！");
            }

            // B. 查时间：不允许删除历史记录 (保持数据完整性)
            // 逻辑：如果记录日期 < 今天
            if (record.getDate().isBefore(java.time.LocalDate.now())) {
                return Result.error("非法操作：历史记录已归档，无法删除");
            }
        }

        // 4. 执行删除
        dietRecordService.removeById(id);
        return Result.success("删除成功");
    }

    // --- 新增：修改记录 ---
    @PostMapping("/update")
    public Result<String> update(@RequestBody DietRecord record, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");

        // 1. 检查参数
        if (record.getId() == null) {
            return Result.error("记录ID不能为空");
        }

        // 2. 查出数据库里原始的记录
        DietRecord oldRecord = dietRecordService.getById(record.getId());
        if (oldRecord == null) {
            return Result.error("记录不存在");
        }

        // 3. 权限检查：只能改自己的
        if (!oldRecord.getUserId().equals(currentUserId)) {
            return Result.error("无权修改他人的记录");
        }

        // 4. 时间检查：只能改今天的 (历史记录不能改)
        // 逻辑：如果"那条记录的日期" 不是 "今天"，就报错
        if (!oldRecord.getDate().equals(java.time.LocalDate.now())) {
            return Result.error("历史记录已锁定，无法修改");
        }

        // 5. 执行修改
        // 这里的 record 是前端传来的新数据，我们只允许修改 quantity, mealType, foodId
        oldRecord.setQuantity(record.getQuantity());
        oldRecord.setMealType(record.getMealType());
        oldRecord.setFoodId(record.getFoodId()); // 万一用户手滑选错食物了也能改

        // 注意：不要让用户改 userId 或 date，保持原样

        dietRecordService.updateById(oldRecord);
        return Result.success("修改成功");
    }
}
