package org.swpu.backend.modules.session.service;

import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.session.dto.CreateSessionRequest;
import org.swpu.backend.modules.session.dto.SessionQuery;
import org.swpu.backend.modules.session.dto.UpdateSessionTitleRequest;
import org.swpu.backend.modules.session.vo.ChatSessionVo;

public interface SessionService {
    ChatSessionVo createSession(String bearerToken, CreateSessionRequest request);

    PageResult<ChatSessionVo> listSessions(String bearerToken, SessionQuery query);

    ChatSessionVo getSession(String bearerToken, Long sessionId);

    ChatSessionVo updateSessionTitle(String bearerToken, Long sessionId, UpdateSessionTitleRequest request);

    boolean deleteSession(String bearerToken, Long sessionId);
}
