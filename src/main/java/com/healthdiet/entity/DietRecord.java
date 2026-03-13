package com.healthdiet.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("diet_records")
public class DietRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;    // 谁吃的
    private Long foodId;    // 吃了哪个食物ID
    private LocalDate date; // 哪天吃的

    /**
     * 当前后端统一约定：
     * quantity 表示“100g 单位数”
     * 1 = 100g, 2 = 200g, 3 = 300g ...
     *
     * 这是为了与 foods 表里的每100g营养字段保持一致
     */
    @NotNull(message = "摄入数量不能为空")
    @Min(value = 1, message = "摄入数量至少为 1（即100g）")
    @Max(value = 20, message = "摄入数量不能超过 20（即2000g）")
    private Integer quantity;

    /**
     * 1=早餐, 2=午餐, 3=晚餐, 4=加餐
     */
    @NotNull(message = "餐点类型不能为空")
    @Min(value = 1, message = "餐点类型必须是 1~4")
    @Max(value = 4, message = "餐点类型必须是 1~4")
    private Integer mealType;

    // --- 展示辅助字段，不落库 ---
    @TableField(exist = false)
    private String foodName;

    /**
     * 每100g热量
     */
    @TableField(exist = false)
    private Double foodCalories;

    /**
     * 当前记录总热量
     * foodCalories * quantity
     * 因为 quantity=100g单位数，所以这就是总摄入热量
     */
    @TableField(exist = false)
    private Double totalCalories;
}