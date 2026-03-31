package org.swpu.backend.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Map;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.TokenService;
import org.swpu.backend.common.security.TokenUser;
import org.swpu.backend.modules.auth.dto.LoginRequest;
import org.swpu.backend.modules.auth.dto.RegisterRequest;
import org.swpu.backend.modules.auth.entity.UserEntity;
import org.swpu.backend.modules.auth.mapper.dao.UserMapper;
import org.swpu.backend.modules.auth.service.AuthService;
import org.swpu.backend.modules.auth.vo.AuthTokenVo;
import org.swpu.backend.modules.auth.vo.UsernameCheckVo;
import org.swpu.backend.modules.auth.vo.UserProfileVo;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String TOKEN_TYPE = "Bearer";
    private static final String ROLE_TYPE = "USER";

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final SystemLogService systemLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserMapper userMapper, TokenService tokenService, SystemLogService systemLogService) {
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.systemLogService = systemLogService;
    }

    @Override
    public AuthTokenVo register(RegisterRequest request) {
        // 提取用户名
        String username = normalizeUsername(request.getUsername());
        ensureUsernameNotExists(username);

        // 构建用户实体
        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        // 使用bcrypt算法计算密码哈希值
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setRole(ROLE_TYPE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);

        // 生成并返回token
        String token = tokenService.generateToken(entity.getId(), entity.getUsername());
        systemLogService.record(new SystemLogCommand()
                .setTraceId(TraceContext.getTraceId())
                .setModule("AUTH")
                .setSource(LogConstants.SOURCE_BUSINESS)
                .setAction("REGISTER")
                .setLevel(LogConstants.LEVEL_INFO)
                .setSuccess(true)
                .setMessage("User registered")
                .setUserId(entity.getId())
                .setUsername(entity.getUsername())
                .setUserRole(entity.getRole())
                .setVisibilityScope(LogConstants.SCOPE_PRIVATE)
                .setDetails(Map.of("username", entity.getUsername())));
        return new AuthTokenVo(token, TOKEN_TYPE, tokenService.getExpireSeconds());
    }

    @Override
    public AuthTokenVo login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        UserEntity user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "username or password is incorrect");
        }

        String token = tokenService.generateToken(user.getId(), user.getUsername());
        systemLogService.record(new SystemLogCommand()
                .setTraceId(TraceContext.getTraceId())
                .setModule("AUTH")
                .setSource(LogConstants.SOURCE_BUSINESS)
                .setAction("LOGIN")
                .setLevel(LogConstants.LEVEL_INFO)
                .setSuccess(true)
                .setMessage("User login succeeded")
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setUserRole(user.getRole())
                .setVisibilityScope(LogConstants.SCOPE_PRIVATE));
        return new AuthTokenVo(token, TOKEN_TYPE, tokenService.getExpireSeconds());
    }

    @Override
    public UserProfileVo me(String bearerToken) {
        TokenUser tokenUser = tokenService.verify(extractToken(bearerToken));
        if (tokenUser == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "invalid or expired token");
        }

        UserEntity user = userMapper.selectById(tokenUser.getUserId());
        if (user == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "user not found");
        }

        systemLogService.record(new SystemLogCommand()
                .setTraceId(TraceContext.getTraceId())
                .setModule("AUTH")
                .setSource(LogConstants.SOURCE_BUSINESS)
                .setAction("ME")
                .setLevel(LogConstants.LEVEL_INFO)
                .setSuccess(true)
                .setMessage("Fetched current user profile")
                .setUserId(user.getId())
                .setUsername(user.getUsername())
                .setUserRole(user.getRole())
                .setVisibilityScope(LogConstants.SCOPE_PRIVATE));

        return new UserProfileVo(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
    }

    @Override
    public UsernameCheckVo checkUsernameAvailability(String username) {
        String normalized = normalizeUsername(username);
        return new UsernameCheckVo(normalized, !usernameExists(normalized));
    }

    // 检查用户名是否存在
    private void ensureUsernameNotExists(String username) {
        if (usernameExists(username)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "username already exists");
        }
    }

    private boolean usernameExists(String username) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return userMapper.selectCount(wrapper) > 0;
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "username is required");
        }
        return username.trim();
    }

    private String extractToken(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }
        String value = bearerToken.trim();
        if (value.regionMatches(true, 0, TOKEN_TYPE + " ", 0, TOKEN_TYPE.length() + 1)) {
            return value.substring(TOKEN_TYPE.length() + 1).trim();
        }
        return value;
    }
}
