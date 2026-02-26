package com.healthdiet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;

    private Double height;
    private Double weight;

    @Min(value = 0, message = "年龄必须大于0")
    @Max(value = 120, message = "年龄不能超过120岁")
    private Integer age;

    @NotNull(message = "性别不能为空")
    @Min(value = 0, message = "性别只能是 0(女) 或 1(男)") // 假设你的定义
    @Max(value = 1, message = "性别只能是 0(女) 或 1(男)")
    private Integer gender;

    // 重点：限制 target 只能是 -1, 0, 1
    @Min(value = -1, message = "目标只能是 -1(减脂), 0(维持), 1(增肌)")
    @Max(value = 1, message = "目标只能是 -1(减脂), 0(维持), 1(增肌)")
    private Integer target;

    @Min(value = 0, message = "角色只能是 0(普通) 或 1(管理员)")
    @Max(value = 1, message = "角色只能是 0(普通) 或 1(管理员)")
    private Integer role;

    private String nickname; // 昵称
    private String avatar;   // 头像 (存的是 /images/xxx.jpg 这样的路径)
}
