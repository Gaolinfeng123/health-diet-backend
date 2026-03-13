package com.healthdiet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("foods")
public class Food {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 统一口径：每100g
     */
    private Double calories;
    private Double protein;
    private Double fat;
    private Double carb;

    @TableField("food_category")
    private String foodCategory;

    @TableField("gi_level")
    private String giLevel;

    @TableField("sodium_mg")
    private BigDecimal sodiumMg;

    @TableField("saturated_fat")
    private BigDecimal saturatedFat;

    private BigDecimal fiber;
    private BigDecimal sugar;

    @TableField("breakfast_friendly")
    private Integer breakfastFriendly;

    @TableField("dinner_friendly")
    private Integer dinnerFriendly;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}