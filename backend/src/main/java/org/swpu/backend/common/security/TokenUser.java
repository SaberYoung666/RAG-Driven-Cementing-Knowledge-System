package org.swpu.backend.common.security;

// Token 解析后的用户信息
public class TokenUser {
    private final Long userId;
    private final String username;

    public TokenUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}
