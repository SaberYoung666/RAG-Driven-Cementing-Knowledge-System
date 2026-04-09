package org.swpu.backend.modules.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.swpu.backend.modules.chat.dto.ChatDto;

class ChatLogServiceTest {

	private static final String OWNERSHIP_SQL = "SELECT 1 FROM chat_session WHERE id = ? AND user_id = ? AND deleted = FALSE";
	private static final String INSERT_MESSAGE_SQL = "INSERT INTO chat_message (session_id, role, content, trace_id, refused, retrieval_ms, rerank_ms, gen_ms, created_at) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
	private static final String INSERT_CITATION_SQL = "INSERT INTO chat_citation (message_id, chunk_id, source, page, section, score, snippet) VALUES (?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_SESSION_SQL = "UPDATE chat_session SET updated_at = ? WHERE id = ? AND user_id = ? AND deleted = FALSE";
	private static final String LOAD_MESSAGES_SQL = "SELECT id, role, content, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC, id ASC";
	private static final String LOAD_CITATIONS_SQL = "SELECT message_id, chunk_id, source, page, section, score, snippet FROM chat_citation WHERE message_id IN (?) ORDER BY id ASC";

	@Test
	void shouldPersistRetrievedSnippetAlongsideCitation() throws Exception {
		DataSource dataSource = Mockito.mock(DataSource.class);
		CitationDisplayService citationDisplayService = Mockito.mock(CitationDisplayService.class);
		ChatLogService service = new ChatLogService(dataSource, citationDisplayService);

		Connection ownershipConnection = Mockito.mock(Connection.class);
		Connection persistenceConnection = Mockito.mock(Connection.class);
		PreparedStatement ownershipPs = Mockito.mock(PreparedStatement.class);
		ResultSet ownershipRs = Mockito.mock(ResultSet.class);
		PreparedStatement userInsertPs = Mockito.mock(PreparedStatement.class);
		ResultSet userInsertRs = Mockito.mock(ResultSet.class);
		PreparedStatement assistantInsertPs = Mockito.mock(PreparedStatement.class);
		ResultSet assistantInsertRs = Mockito.mock(ResultSet.class);
		PreparedStatement citationInsertPs = Mockito.mock(PreparedStatement.class);
		PreparedStatement updateSessionPs = Mockito.mock(PreparedStatement.class);

		when(dataSource.getConnection()).thenReturn(ownershipConnection, persistenceConnection);
		when(ownershipConnection.prepareStatement(OWNERSHIP_SQL)).thenReturn(ownershipPs);
		when(ownershipPs.executeQuery()).thenReturn(ownershipRs);
		when(ownershipRs.next()).thenReturn(true);

		when(persistenceConnection.prepareStatement(INSERT_MESSAGE_SQL)).thenReturn(userInsertPs, assistantInsertPs);
		when(userInsertPs.executeQuery()).thenReturn(userInsertRs);
		when(userInsertRs.next()).thenReturn(true);
		when(userInsertRs.getLong(1)).thenReturn(101L);
		when(assistantInsertPs.executeQuery()).thenReturn(assistantInsertRs);
		when(assistantInsertRs.next()).thenReturn(true);
		when(assistantInsertRs.getLong(1)).thenReturn(102L);
		when(persistenceConnection.prepareStatement(INSERT_CITATION_SQL, Statement.NO_GENERATED_KEYS)).thenReturn(citationInsertPs);
		when(persistenceConnection.prepareStatement(UPDATE_SESSION_SQL)).thenReturn(updateSessionPs);

		ChatDto.ChatReq req = new ChatDto.ChatReq(
				"query",
				"7",
				6,
				null,
				null,
				true,
				"hybrid",
				0.5,
				true,
				20,
				true
		);
		ChatDto.Citation citation = new ChatDto.Citation("证据1", 0.93, "doc-1", "doc-1::p1::c1", "source.pdf", 1, "sec");
		ChatDto.Retrieved retrieved = new ChatDto.Retrieved("doc-1::p1::c1", 0.93, "证据正文", Map.of("source", "source.pdf"));
		ChatDto.RagResp resp = new ChatDto.RagResp("answer", false, List.of(citation), List.of(retrieved), null, 0);

		service.recordChat(1L, req, resp);

		verify(citationInsertPs).setString(2, "doc-1::p1::c1");
		verify(citationInsertPs).setString(3, "source.pdf");
		verify(citationInsertPs).setString(5, "sec");
		verify(citationInsertPs).setString(7, "证据正文");
		verify(citationInsertPs).executeBatch();
		verify(persistenceConnection).commit();
	}

	@Test
	void shouldReturnRetrievedSnippetInSessionMessages() throws Exception {
		DataSource dataSource = Mockito.mock(DataSource.class);
		CitationDisplayService citationDisplayService = Mockito.mock(CitationDisplayService.class);
		ChatLogService service = new ChatLogService(dataSource, citationDisplayService);

		Connection connection = Mockito.mock(Connection.class);
		PreparedStatement messagesPs = Mockito.mock(PreparedStatement.class);
		ResultSet messagesRs = Mockito.mock(ResultSet.class);
		PreparedStatement citationsPs = Mockito.mock(PreparedStatement.class);
		ResultSet citationsRs = Mockito.mock(ResultSet.class);

		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(LOAD_MESSAGES_SQL)).thenReturn(messagesPs);
		when(messagesPs.executeQuery()).thenReturn(messagesRs);
		when(messagesRs.next()).thenReturn(true, false);
		when(messagesRs.getLong("id")).thenReturn(102L);
		when(messagesRs.getString("role")).thenReturn("assistant");
		when(messagesRs.getString("content")).thenReturn("answer");
		when(messagesRs.getObject("created_at")).thenReturn("2026-04-09T12:00:00Z");

		when(connection.prepareStatement(LOAD_CITATIONS_SQL)).thenReturn(citationsPs);
		when(citationsPs.executeQuery()).thenReturn(citationsRs);
		when(citationsRs.next()).thenReturn(true, false);
		when(citationsRs.getLong("message_id")).thenReturn(102L);
		when(citationsRs.getString("chunk_id")).thenReturn("doc-1::p1::c1");
		when(citationsRs.getString("source")).thenReturn("source.pdf");
		when(citationsRs.getObject("page")).thenReturn(1);
		when(citationsRs.getString("section")).thenReturn("sec");
		when(citationsRs.getObject("score")).thenReturn(0.93D);
		when(citationsRs.getString("snippet")).thenReturn("证据正文");
		when(citationDisplayService.toCitationMap("doc-1::p1::c1", "source.pdf", 1, "sec", 0.93D))
				.thenReturn(Map.of("evidence_id", "p1::c1", "chunk_id", "p1::c1", "source", "source.pdf", "page", 1, "section", "sec", "score", 0.93D));

		List<ChatLogService.SessionMessage> messages = service.getSessionMessages(7L);

		assertEquals(1, messages.size());
		ChatLogService.SessionMessage message = messages.getFirst();
		assertEquals("assistant", message.role());
		assertEquals(1, message.citations().size());
		assertEquals(1, message.retrieved().size());
		assertEquals("doc-1::p1::c1", message.retrieved().getFirst().chunkId());
		assertEquals("证据正文", message.retrieved().getFirst().text());
		assertEquals(0.93D, message.retrieved().getFirst().score());
		assertNotNull(message.retrieved().getFirst().metadata());
		assertEquals("source.pdf", message.retrieved().getFirst().metadata().get("source"));
		assertEquals(1, message.retrieved().getFirst().metadata().get("page"));
		assertEquals("sec", message.retrieved().getFirst().metadata().get("section"));
	}
}
