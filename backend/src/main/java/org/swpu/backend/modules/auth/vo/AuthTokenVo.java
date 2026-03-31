package org.swpu.backend.modules.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

// 登录/注册返回的 Token 结果
@Schema(description = "登录或注册成功后的 token 返回")
public class AuthTokenVo {
    @Schema(description = "访问令牌")
    private String token;
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;
    @Schema(description = "过期秒数", example = "86400")
    private long expiresIn;

    public AuthTokenVo(String token, String tokenType, long expiresIn) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
