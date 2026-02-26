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
    private Integer quantity; // 吃了多少份

    /**
     * 新增字段：餐点类型
     * 对应数据库的 meal_type 字段
     * 1=早餐, 2=午餐, 3=晚餐, 4=加餐
     */
    @NotNull(message = "餐点类型不能为空")
    @Min(value = 1, message = "餐点类型必须是 1~4")
    @Max(value = 4, message = "餐点类型必须是 1~4")

    private Integer mealType;



    // --- 下面这几个字段数据库里没有，是我们为了显示方便拼凑出来的 ---
    // 必须加 @TableField(exist = false)，否则 MyBatis 会报错说数据库找不到这列

    @TableField(exist = false)
    private String foodName;   // 食物名称

    @TableField(exist = false)
    private Double foodCalories; // 单份热量

    @TableField(exist = false)
    private Double totalCalories; // 总热量 (单份 * 数量)
}