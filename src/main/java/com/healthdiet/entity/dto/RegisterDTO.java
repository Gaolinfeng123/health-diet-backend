package com.healthdiet.entity.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String username;
    private String password;
    private Double height;
    private Double weight;
    private Integer age;
    private Integer gender;
    private Integer target; // -1, 0, 1

    // 验证码字段
    private String captchaKey;
    private String captchaCode;
}


