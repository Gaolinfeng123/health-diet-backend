package com.healthdiet.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty; // 导入这个包
import io.swagger.v3.oas.annotations.media.Schema;

public class UpdatePasswordDTO {

    @Schema(description = "旧密码", example = "123456")
    @JsonProperty("oldPassword") // 强制指定 JSON 字段名
    private String oldPassword;

    @Schema(description = "新密码", example = "888888")
    @JsonProperty("newPassword") // 强制指定 JSON 字段名
    private String newPassword;

    // --- Getter 和 Setter ---
    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}