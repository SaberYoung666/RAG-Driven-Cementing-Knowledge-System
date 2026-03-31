package org.swpu.backend.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 登录请求
@Schema(description = "登录请求参数")
public class LoginRequest {
    @Schema(description = "用户名", example = "admin")
    @NotBlank(message = "username is required")
    @Size(min = 3, max = 32, message = "username length must be between 3 and 32")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "password is required")
    @Size(min = 6, max = 64, message = "password length must be between 6 and 64")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
