package com.healthdiet.entity.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String username;
    private String password;

    // 验证码 Key (UUID)
    private String captchaKey;
    // 用户输入的验证码内容
    private String captchaCode;
}
