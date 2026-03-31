package org.swpu.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.modules.auth.entity.UserEntity;
import org.swpu.backend.modules.auth.mapper.dao.UserMapper;

@Service
public class AuthContextService {
    private static final String TOKEN_TYPE = "Bearer";

    private final TokenService tokenService;
    private final UserMapper userMapper;

    public AuthContextService(TokenService tokenService, UserMapper userMapper) {
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    public CurrentUser resolveOptional(String bearerToken) {
        TokenUser tokenUser = tokenService.verify(extractToken(bearerToken));
        if (tokenUser == null || tokenUser.getUserId() == null) {
            return null;
        }
        UserEntity user = userMapper.selectById(tokenUser.getUserId());
        if (user == null) {
            return null;
        }
        return new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    }

    public CurrentUser resolveRequired(String bearerToken) {
        CurrentUser currentUser = resolveOptional(bearerToken);
        if (currentUser == null || currentUser.userId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "invalid or expired token");
        }
        return currentUser;
    }

    public CurrentUser currentFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return null;
        }
        Object cached = request.getAttribute(CurrentUser.class.getName());
        if (cached instanceof CurrentUser currentUser) {
            return currentUser;
        }
        CurrentUser resolved = resolveOptional(request.getHeader("Authorization"));
        if (resolved != null) {
            request.setAttribute(CurrentUser.class.getName(), resolved);
        }
        return resolved;
    }

    public String extractToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }
        String value = bearerToken.trim();
        if (value.regionMatches(true, 0, TOKEN_TYPE + " ", 0, TOKEN_TYPE.length() + 1)) {
            return value.substring(TOKEN_TYPE.length() + 1).trim();
        }
        return value;
    }

    public record CurrentUser(Long userId, String username, String role) {
        public boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(role);
        }
    }
}
