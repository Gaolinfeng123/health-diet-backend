package com.healthdiet.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度必须在4-20位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")

    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;
    private Double height;
    private Double weight;
    private Integer age;
    private Integer gender;
    private Integer target; // -1=减脂, 0=维持, 1=增肌, 2=糖尿病控糖, 3=高血压低盐, 4=高血脂低脂

    // 验证码字段
    private String captchaKey;
    private String captchaCode;
}


