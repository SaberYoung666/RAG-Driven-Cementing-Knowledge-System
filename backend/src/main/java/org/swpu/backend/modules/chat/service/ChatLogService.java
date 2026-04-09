package org.swpu.backend.modules.chat.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.modules.chat.dto.ChatDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class ChatLogService {
	private static final int MAX_LOG_SIZE = 5000;

	private final AtomicLong logIdGen = new AtomicLong(0);
	private final AtomicLong feedbackIdGen = new AtomicLong(0);
	private final ConcurrentLinkedDeque<LogEntry> logs = new ConcurrentLinkedDeque<>();
	private final ConcurrentHashMap<String, FeedbackEntry> feedbackStore = new ConcurrentHashMap<>();
	private final DataSource dataSource;
	private final CitationDisplayService citationDisplayService;

	public ChatLogService(DataSource dataSource, CitationDisplayService citationDisplayService) {
		this.dataSource = dataSource;
		this.citationDisplayService = citationDisplayService;
	}

	// 记录对话
	@Transactional
	public void recordChat(Long userId, ChatDto.ChatReq req, ChatDto.RagResp resp) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		String nowText = now.toString();
		String logId = String.valueOf(logIdGen.incrementAndGet());
		Integer topK = req.topR() != null ? req.topR() : req.topK();
		Long sessionId = parseSessionId(req.sessionId());
		String answer = resp == null ? "" : Objects.toString(resp.answer(), "");
		String answerPreview = answer.length() > 300 ? answer.substring(0, 300) + "..." : answer;

		LogEntry entry = new LogEntry(
				logId,
				sessionId == null ? null : String.valueOf(sessionId),
				req.query(),
				answerPreview,
				resp != null && Boolean.TRUE.equals(resp.refused()),
				topK,
				req.minScore(),
				req.mode(),
				req.alpha(),
				req.rerankOn(),
				req.candidateK(),
				Boolean.TRUE.equals(req.useLlm()),
				Boolean.TRUE.equals(req.useLlm()),
				resp == null ? List.of() : resp.retrieved(),
				nowText
		);
		logs.addFirst(entry);
		while (logs.size() > MAX_LOG_SIZE) {
			logs.pollLast();
		}

		if (sessionId == null || userId == null || !hasOwnedActiveSession(userId, sessionId)) {
			return;
		}
		persistConversation(sessionId, userId, req, resp, answer, now);
	}

	// 按页查询日志
	public PageResult<LogEntry> listLogs(int page, int pageSize, String sessionId, String keyword, Boolean refused) {
		List<LogEntry> filtered = new ArrayList<>();
		for (LogEntry row : logs) {
			if (StringUtils.hasText(sessionId) && !Objects.equals(sessionId.trim(), row.sessionId())) {
				continue;
			}
			if (refused != null && !Objects.equals(refused, row.refused())) {
				continue;
			}
			if (StringUtils.hasText(keyword)) {
				String k = keyword.trim().toLowerCase();
				String q = Objects.toString(row.question(), "").toLowerCase();
				String a = Objects.toString(row.answerPreview(), "").toLowerCase();
				if (!q.contains(k) && !a.contains(k)) {
					continue;
				}
			}
			filtered.add(row);
		}
		int safePage = Math.max(1, page);
		int safeSize = Math.max(1, pageSize);
		int from = (safePage - 1) * safeSize;
		int to = Math.min(filtered.size(), from + safeSize);
		List<LogEntry> pageItems = from >= filtered.size() ? List.of() : filtered.subList(from, to);
		return PageResult.of(pageItems, filtered.size(), safePage, safeSize);
	}

	public FeedbackResult saveFeedback(FeedbackRequest req) {
		String feedbackId = "fb-" + feedbackIdGen.incrementAndGet();
		feedbackStore.put(
				feedbackId,
				new FeedbackEntry(
						feedbackId,
						req.logId(),
						req.rating(),
						req.comment(),
						req.correctedAnswer(),
						req.tags() == null ? List.of() : req.tags(),
						OffsetDateTime.now(ZoneOffset.UTC).toString()
				)
		);
		return new FeedbackResult(feedbackId, true);
	}

	public List<SessionMessage> getSessionMessages(Long sessionId) {
		if (sessionId == null) {
			return List.of();
		}
		try (Connection connection = dataSource.getConnection()) {
			List<MessageRow> messages = loadMessages(connection, sessionId);
			if (messages.isEmpty()) {
				return List.of();
			}
			Map<Long, List<CitationRow>> citationMap = loadCitationMap(connection, messages);
			List<SessionMessage> result = new ArrayList<>(messages.size());
			for (MessageRow message : messages) {
				List<CitationRow> citationRows = citationMap.get(message.id());
				result.add(new SessionMessage(
						message.role(),
						message.content(),
						message.createdAt(),
						toCitationMapsFromRows(citationRows),
						toRetrievedListFromRows(citationRows)
				));
			}
			return List.copyOf(result);
		} catch (SQLException ex) {
			throw new IllegalStateException("load session messages failed", ex);
		}
	}

	private Long parseSessionId(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private boolean hasOwnedActiveSession(Long userId, Long sessionId) {
		String sql = "SELECT 1 FROM chat_session WHERE id = ? AND user_id = ? AND deleted = FALSE";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setLong(1, sessionId);
			ps.setLong(2, userId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException ex) {
			throw new IllegalStateException("check session ownership failed", ex);
		}
	}

	private void persistConversation(Long sessionId, Long userId, ChatDto.ChatReq req, ChatDto.RagResp resp, String answer, OffsetDateTime now) {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				insertMessage(connection, sessionId, "user", req.query(), false, 0, 0, 0, now);
				Long assistantMessageId = insertMessage(
						connection,
						sessionId,
						"assistant",
						answer,
						resp != null && Boolean.TRUE.equals(resp.refused()),
						toMillis(resp == null || resp.debug() == null ? null : resp.debug().retrievalMs()),
						toMillis(resp == null || resp.debug() == null ? null : resp.debug().rerankMs()),
						toMillis(resp == null || resp.debug() == null ? null : resp.debug().genMs()),
						now
				);
				persistCitations(
						connection,
						assistantMessageId,
						resp == null ? List.of() : resp.citations(),
						resp == null ? List.of() : resp.retrieved()
				);
				updateSessionTimestamp(connection, sessionId, userId, now);
				connection.commit();
			} catch (SQLException ex) {
				connection.rollback();
				throw ex;
			}
		} catch (SQLException ex) {
			throw new IllegalStateException("persist conversation failed", ex);
		}
	}

	private Long insertMessage(Connection connection, Long sessionId, String role, String content, boolean refused, int retrievalMs, int rerankMs, int genMs, OffsetDateTime createdAt) throws SQLException {
		String sql = "INSERT INTO chat_message (session_id, role, content, trace_id, refused, retrieval_ms, rerank_ms, gen_ms, created_at) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setLong(1, sessionId);
			ps.setString(2, role);
			ps.setString(3, content);
			ps.setString(4, TraceContext.getTraceId());
			ps.setBoolean(5, refused);
			ps.setInt(6, retrievalMs);
			ps.setInt(7, rerankMs);
			ps.setInt(8, genMs);
			ps.setObject(9, createdAt);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new SQLException("insert chat_message returned no id");
				}
				return rs.getLong(1);
			}
		}
	}

	private void persistCitations(
			Connection connection,
			Long messageId,
			List<ChatDto.Citation> citations,
			List<ChatDto.Retrieved> retrieved
	) throws SQLException {
		if (messageId == null || citations == null || citations.isEmpty()) {
			return;
		}
		Map<String, ChatDto.Retrieved> retrievedByChunkId = indexRetrievedByChunkId(retrieved);
		String sql = "INSERT INTO chat_citation (message_id, chunk_id, source, page, section, score, snippet) VALUES (?, ?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
			for (ChatDto.Citation citation : citations) {
				if (citation == null || !StringUtils.hasText(citation.chunkId()) || !StringUtils.hasText(citation.source())) {
					continue;
				}
				String chunkId = citation.chunkId().trim();
				ChatDto.Retrieved matchedRetrieved = retrievedByChunkId.get(chunkId);
				ps.setLong(1, messageId);
				ps.setString(2, chunkId);
				ps.setString(3, citation.source().trim());
				if (citation.page() == null) {
					ps.setNull(4, java.sql.Types.INTEGER);
				} else {
					ps.setInt(4, citation.page());
				}
				ps.setString(5, citation.section());
				if (citation.score() == null) {
					ps.setNull(6, java.sql.Types.DOUBLE);
				} else {
					ps.setDouble(6, citation.score());
				}
				ps.setString(7, matchedRetrieved == null ? null : matchedRetrieved.text());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	private void updateSessionTimestamp(Connection connection, Long sessionId, Long userId, OffsetDateTime now) throws SQLException {
		String sql = "UPDATE chat_session SET updated_at = ? WHERE id = ? AND user_id = ? AND deleted = FALSE";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setObject(1, now);
			ps.setLong(2, sessionId);
			ps.setLong(3, userId);
			ps.executeUpdate();
		}
	}

	private List<MessageRow> loadMessages(Connection connection, Long sessionId) throws SQLException {
		String sql = "SELECT id, role, content, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC, id ASC";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setLong(1, sessionId);
			try (ResultSet rs = ps.executeQuery()) {
				List<MessageRow> result = new ArrayList<>();
				while (rs.next()) {
					result.add(new MessageRow(
							rs.getLong("id"),
							rs.getString("role"),
							rs.getString("content"),
							Objects.toString(rs.getObject("created_at"), null)
					));
				}
				return result;
			}
		}
	}

	private Map<Long, List<CitationRow>> loadCitationMap(Connection connection, List<MessageRow> messages) throws SQLException {
		List<Long> messageIds = messages.stream().map(MessageRow::id).filter(Objects::nonNull).toList();
		if (messageIds.isEmpty()) {
			return Map.of();
		}
		StringBuilder sql = new StringBuilder("SELECT message_id, chunk_id, source, page, section, score, snippet FROM chat_citation WHERE message_id IN (");
		for (int i = 0; i < messageIds.size(); i++) {
			if (i > 0) {
				sql.append(", ");
			}
			sql.append("?");
		}
		sql.append(") ORDER BY id ASC");
		try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
			for (int i = 0; i < messageIds.size(); i++) {
				ps.setLong(i + 1, messageIds.get(i));
			}
			try (ResultSet rs = ps.executeQuery()) {
				Map<Long, List<CitationRow>> result = new LinkedHashMap<>();
				while (rs.next()) {
					Long messageId = rs.getLong("message_id");
					result.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(new CitationRow(
							messageId,
							rs.getString("chunk_id"),
							rs.getString("source"),
							(Integer) rs.getObject("page"),
							rs.getString("section"),
							(Double) rs.getObject("score"),
							rs.getString("snippet")
					));
				}
				return result;
			}
		}
	}

	private List<Map<String, Object>> toCitationMapsFromRows(List<CitationRow> citations) {
		if (citations == null || citations.isEmpty()) {
			return List.of();
		}
		List<Map<String, Object>> result = new ArrayList<>(citations.size());
		for (CitationRow citation : citations) {
			if (citation == null) {
				continue;
			}
			result.add(citationDisplayService.toCitationMap(
					citation.chunkId(),
					citation.source(),
					citation.page(),
					citation.section(),
					citation.score()
			));
		}
		return List.copyOf(result);
	}

	private List<ChatDto.Retrieved> toRetrievedListFromRows(List<CitationRow> citations) {
		if (citations == null || citations.isEmpty()) {
			return List.of();
		}
		List<ChatDto.Retrieved> result = new ArrayList<>(citations.size());
		for (CitationRow citation : citations) {
			if (citation == null || !StringUtils.hasText(citation.chunkId())) {
				continue;
			}
			result.add(new ChatDto.Retrieved(
					citation.chunkId(),
					citation.score(),
					citation.snippet(),
					toRetrievedMetadata(citation)
			));
		}
		return List.copyOf(result);
	}

	private Map<String, Object> toRetrievedMetadata(CitationRow citation) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		if (citation == null) {
			return metadata;
		}
		metadata.put("source", citation.source());
		metadata.put("page", citation.page());
		metadata.put("section", citation.section());
		return metadata;
	}

	private Map<String, ChatDto.Retrieved> indexRetrievedByChunkId(List<ChatDto.Retrieved> retrieved) {
		if (retrieved == null || retrieved.isEmpty()) {
			return Map.of();
		}
		Map<String, ChatDto.Retrieved> result = new LinkedHashMap<>();
		for (ChatDto.Retrieved item : retrieved) {
			if (item == null || !StringUtils.hasText(item.chunkId())) {
				continue;
			}
			result.put(item.chunkId().trim(), item);
		}
		return result;
	}

	private int toMillis(Double value) {
		if (value == null) {
			return 0;
		}
		return Math.max(0, (int) Math.round(value));
	}

	public record LogEntry(
			String logId,
			String sessionId,
			String question,
			String answerPreview,
			Boolean refused,
			Integer topK,
			Double minScore,
			String mode,
			Double alpha,
			Boolean rerankOn,
			Integer candidateK,
			Boolean llmAvailable,
			Boolean useLlm,
			List<ChatDto.Retrieved> retrieved,
			String createdAt
	) {
	}

	public record SessionMessage(
			String role,
			String content,
			String createdAt,
			List<Map<String, Object>> citations,
			List<ChatDto.Retrieved> retrieved
	) {
	}

	public record FeedbackRequest(
			String logId,
			String rating,
			String comment,
			String correctedAnswer,
			List<String> tags
	) {
	}

	public record FeedbackResult(String feedbackId, boolean saved) {
	}

	private record FeedbackEntry(
			String feedbackId,
			String logId,
			String rating,
			String comment,
			String correctedAnswer,
			List<String> tags,
			String createdAt
	) {
	}

	private record MessageRow(Long id, String role, String content, String createdAt) {
	}

	private record CitationRow(Long messageId, String chunkId, String source, Integer page, String section, Double score, String snippet) {
	}
}
