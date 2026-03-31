package org.swpu.backend.modules.session.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.TokenService;
import org.swpu.backend.common.security.TokenUser;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.session.dto.CreateSessionRequest;
import org.swpu.backend.modules.session.dto.SessionQuery;
import org.swpu.backend.modules.session.dto.UpdateSessionTitleRequest;
import org.swpu.backend.modules.session.entity.ChatSessionEntity;
import org.swpu.backend.modules.session.mapper.dao.ChatSessionMapper;
import org.swpu.backend.modules.session.service.SessionService;
import org.swpu.backend.modules.session.vo.ChatSessionVo;

@Service
public class SessionServiceImpl implements SessionService {
    private static final String TOKEN_TYPE = "Bearer";
    private static final String DEFAULT_TITLE = "New Session";

    private final ChatSessionMapper chatSessionMapper;
    private final TokenService tokenService;
    private final SystemLogService systemLogService;

    public SessionServiceImpl(ChatSessionMapper chatSessionMapper, TokenService tokenService, SystemLogService systemLogService) {
        this.chatSessionMapper = chatSessionMapper;
        this.tokenService = tokenService;
        this.systemLogService = systemLogService;
    }

    @Override
    public ChatSessionVo createSession(String bearerToken, CreateSessionRequest request) {
        Long userId = resolveCurrentUserId(bearerToken);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(normalizeTitle(request == null ? null : request.getTitle(), true));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(false);

        chatSessionMapper.insert(entity);
        recordSessionLog(userId, "CREATE_SESSION", "Created session", entity.getId(), Map.of("title", entity.getTitle()));
        return toVo(entity);
    }

    @Override
    public PageResult<ChatSessionVo> listSessions(String bearerToken, SessionQuery query) {
        Long userId = resolveCurrentUserId(bearerToken);
        int page = query == null || query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query == null || query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();

        QueryWrapper<ChatSessionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("deleted", false);
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            wrapper.like("title", query.getKeyword().trim());
        }
        wrapper.orderByDesc("updated_at");

        Page<ChatSessionEntity> pageResult = chatSessionMapper.selectPage(new Page<>(page, size), wrapper);
        List<ChatSessionVo> items = pageResult.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(items, pageResult.getTotal(), page, size);
    }

    @Override
    public ChatSessionVo getSession(String bearerToken, Long sessionId) {
        Long userId = resolveCurrentUserId(bearerToken);
        return toVo(loadOwnedSession(userId, sessionId));
    }

    @Override
    public ChatSessionVo updateSessionTitle(String bearerToken, Long sessionId, UpdateSessionTitleRequest request) {
        Long userId = resolveCurrentUserId(bearerToken);
        ChatSessionEntity entity = loadOwnedSession(userId, sessionId);

        entity.setTitle(normalizeTitle(request == null ? null : request.getTitle(), false));
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        chatSessionMapper.updateById(entity);
        recordSessionLog(userId, "UPDATE_SESSION_TITLE", "Updated session title", entity.getId(), Map.of("title", entity.getTitle()));
        return toVo(entity);
    }

    @Override
    public boolean deleteSession(String bearerToken, Long sessionId) {
        Long userId = resolveCurrentUserId(bearerToken);
        validateSessionId(sessionId);
        UpdateWrapper<ChatSessionEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", sessionId)
                .eq("user_id", userId)
                .eq("deleted", false)
                .set("deleted", true)
                .set("updated_at", OffsetDateTime.now(ZoneOffset.UTC));
        boolean deleted = chatSessionMapper.update(null, wrapper) > 0;
        recordSessionLog(userId, "DELETE_SESSION", deleted ? "Deleted session" : "Delete session skipped", sessionId, Map.of("deleted", deleted));
        return deleted;
    }

    private Long resolveCurrentUserId(String bearerToken) {
        TokenUser tokenUser = tokenService.verify(extractToken(bearerToken));
        if (tokenUser == null || tokenUser.getUserId() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "invalid or expired token");
        }
        return tokenUser.getUserId();
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

    private ChatSessionEntity loadOwnedSession(Long userId, Long sessionId) {
        validateSessionId(sessionId);
        QueryWrapper<ChatSessionEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("id", sessionId).eq("user_id", userId).eq("deleted", false);
        ChatSessionEntity entity = chatSessionMapper.selectOne(wrapper);
        if (entity == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "session not found");
        }
        return entity;
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId < 1) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "sessionId is invalid");
        }
    }

    private String normalizeTitle(String title, boolean allowBlank) {
        if (!StringUtils.hasText(title)) {
            if (allowBlank) {
                return DEFAULT_TITLE;
            }
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "title is required");
        }
        String normalized = title.trim();
        if (normalized.length() > 200) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "title length must be less than or equal to 200");
        }
        return normalized;
    }

    private ChatSessionVo toVo(ChatSessionEntity entity) {
        return new ChatSessionVo(
                entity.getId(),
                entity.getTitle(),
                Objects.toString(entity.getCreatedAt(), null),
                Objects.toString(entity.getUpdatedAt(), null));
    }

    private void recordSessionLog(Long userId, String action, String message, Long sessionId, Map<String, Object> details) {
        systemLogService.record(new SystemLogCommand()
                .setTraceId(TraceContext.getTraceId())
                .setModule("SESSION")
                .setSource(LogConstants.SOURCE_BUSINESS)
                .setAction(action)
                .setLevel(LogConstants.LEVEL_INFO)
                .setSuccess(true)
                .setMessage(message)
                .setUserId(userId)
                .setVisibilityScope(LogConstants.SCOPE_PRIVATE)
                .setResourceType("SESSION")
                .setResourceId(sessionId == null ? null : String.valueOf(sessionId))
                .setDetails(details));
    }
}
