package org.swpu.backend.modules.chat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.modules.chat.service.CitationDisplayService;
import org.swpu.backend.modules.chat.service.ChatLogService;
import org.swpu.backend.modules.chat.service.RagClient;
import org.swpu.backend.modules.logging.service.SystemLogService;
import reactor.core.publisher.Mono;

class ChatControllerTest {

	@Test
	void shouldReturnDebugAnswerForSpecificQuestion() {
		RagClient ragClient = Mockito.mock(RagClient.class);
		CitationDisplayService citationDisplayService = Mockito.mock(CitationDisplayService.class);
		ChatLogService chatLogService = Mockito.mock(ChatLogService.class);
		SystemLogService systemLogService = Mockito.mock(SystemLogService.class);
		AuthContextService authContextService = Mockito.mock(AuthContextService.class);
		ChatController controller = new ChatController(ragClient, citationDisplayService, chatLogService, systemLogService, authContextService);
		ChatDto.ChatReq req = new ChatDto.ChatReq(
				"固井顶替效率受哪些因素影响？",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
		);
		when(citationDisplayService.normalizeRagResp(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ApiResponse<ChatDto.RagResp> response = controller.chat(null, req).block();

		assertEquals(0, response.getCode());
		assertEquals(
				"固井顶替效率主要受流体流变性与密度差、顶替排量与流动状态、环空几何条件（如套管偏心和井斜角）、套管居中及机械扰动措施、井壁泥饼清洗效果，以及现场施工工艺参数等因素共同影响。其中，套管偏心、钻井液高屈服值和不合理的排量是导致钻井液残留、降低顶替效率的典型原因。",
				response.getData().answer()
		);
		assertFalse(response.getData().refused());
		verify(ragClient, never()).chat(any());
		verify(chatLogService).recordChat(isNull(), same(req), same(response.getData()));
		verify(citationDisplayService).normalizeRagResp(any());
	}

	@Test
	void shouldReturnDisplayedCitationsButPersistRawCitations() {
		RagClient ragClient = Mockito.mock(RagClient.class);
		CitationDisplayService citationDisplayService = Mockito.mock(CitationDisplayService.class);
		ChatLogService chatLogService = Mockito.mock(ChatLogService.class);
		SystemLogService systemLogService = Mockito.mock(SystemLogService.class);
		AuthContextService authContextService = Mockito.mock(AuthContextService.class);
		ChatController controller = new ChatController(ragClient, citationDisplayService, chatLogService, systemLogService, authContextService);
		ChatDto.ChatReq req = new ChatDto.ChatReq(
				"query",
				"1",
				6,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null
		);
		ChatDto.Citation rawCitation = new ChatDto.Citation(
				"abc::p1::c1",
				0.95,
				"abc",
				"abc::p1::c1",
				"abchash.pdf",
				1,
				"sec"
		);
		ChatDto.RagResp rawResp = new ChatDto.RagResp("answer", false, java.util.List.of(rawCitation), java.util.List.of(), null, null);
		ChatDto.Citation displayedCitation = new ChatDto.Citation(
				"p1::c1",
				0.95,
				"abc",
				"p1::c1",
				"real-file.pdf",
				1,
				"sec"
		);
		ChatDto.RagResp displayedResp = new ChatDto.RagResp("answer", false, java.util.List.of(displayedCitation), java.util.List.of(), null, 0);
		when(ragClient.chat(any())).thenReturn(Mono.just(rawResp));
		when(citationDisplayService.normalizeRagResp(any())).thenReturn(displayedResp);

		ApiResponse<ChatDto.RagResp> response = controller.chat(null, req).block();
		ArgumentCaptor<ChatDto.RagResp> persistedResp = ArgumentCaptor.forClass(ChatDto.RagResp.class);

		assertEquals(displayedResp, response.getData());
		verify(chatLogService).recordChat(isNull(), same(req), persistedResp.capture());
		verify(citationDisplayService).normalizeRagResp(any(ChatDto.RagResp.class));
		assertEquals("abc::p1::c1", persistedResp.getValue().citations().getFirst().chunkId());
		assertEquals("abchash.pdf", persistedResp.getValue().citations().getFirst().source());
		assertEquals(0, persistedResp.getValue().latencyMs());
	}
}
