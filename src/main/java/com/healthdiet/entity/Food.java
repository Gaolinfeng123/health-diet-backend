package com.healthdiet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("foods") // 告诉代码，这个类对应数据库的 "foods" 表
public class Food {
    @TableId(type = IdType.AUTO) // 告诉代码，id 是自增的主键
    private Long id;
    private String name;
    private Double calories; // 热量
    private Double protein;  // 蛋白质
    private Double fat;      // 脂肪
    private Double carb;     // 碳水
}