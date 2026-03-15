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
    @Min(value = 0, message = "性别只能是0(女)或1(男)")
    @Max(value = 1, message = "性别只能是0(女)或1(男)")
    private Integer gender;

    @Min(value = -1, message = "目标只能是-1到4之间的预设值")
    @Max(value = 4, message = "目标只能是-1到4之间的预设值")
    private Integer target;

    @Min(value = 1, message = "活动档位只能是1到4")
    @Max(value = 4, message = "活动档位只能是1到4")
    private Integer activityLevel;

    @Min(value = 0, message = "角色只能是0(普通用户)或1(管理员)")
    @Max(value = 1, message = "角色只能是0(普通用户)或1(管理员)")
    private Integer role;

    private String nickname;
    private String avatar;
}
