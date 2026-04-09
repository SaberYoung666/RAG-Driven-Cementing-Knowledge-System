package org.swpu.backend.modules.chat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.modules.chat.service.ChatLogService;
import org.swpu.backend.modules.session.service.SessionService;
import org.swpu.backend.modules.session.vo.ChatSessionVo;

class ChatSessionCompatControllerTest {

	@Test
	void shouldExposeRetrievedEvidenceInSessionDetail() {
		SessionService sessionService = Mockito.mock(SessionService.class);
		ChatLogService chatLogService = Mockito.mock(ChatLogService.class);
		ChatSessionCompatController controller = new ChatSessionCompatController(sessionService, chatLogService);

		ChatSessionVo session = new ChatSessionVo(7L, "title", "2026-04-09T10:00:00Z", "2026-04-09T10:05:00Z");
		ChatDto.Retrieved retrieved = new ChatDto.Retrieved(
				"doc-1::p1::c1",
				0.93,
				"证据正文",
				Map.of("source", "source.pdf", "page", 1, "section", "sec")
		);
		ChatLogService.SessionMessage message = new ChatLogService.SessionMessage(
				"assistant",
				"answer",
				"2026-04-09T10:05:00Z",
				List.of(Map.of("evidence_id", "p1::c1", "chunk_id", "p1::c1")),
				List.of(retrieved)
		);
		when(sessionService.getSession(null, 7L)).thenReturn(session);
		when(chatLogService.getSessionMessages(7L)).thenReturn(List.of(message));

		ApiResponse<ChatSessionCompatController.SessionDetail> response = controller.getSession(null, 7L);

		assertEquals(0, response.getCode());
		assertEquals("7", response.getData().sessionId());
		assertEquals(1, response.getData().messages().size());
		assertEquals(1, response.getData().messages().getFirst().retrieved().size());
		assertEquals("证据正文", response.getData().messages().getFirst().retrieved().getFirst().text());
	}
}
