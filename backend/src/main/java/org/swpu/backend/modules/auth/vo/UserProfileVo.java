package org.swpu.backend.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

// 用户信息
@Schema(description = "当前登录用户信息")
public class UserProfileVo {
    @Schema(description = "用户 ID", example = "1")
    private Long id;
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "角色", example = "ADMIN")
    private String role;
    @Schema(description = "创建时间(ISO-8601)", example = "2026-03-03T10:00:00Z")
    private String createdAt;

    public UserProfileVo(Long id, String username, String role, String createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
