package org.swpu.backend.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("chat_message")
public class ChatMessageEntity {
	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@TableField("session_id")
	private Long sessionId;

	@TableField("role")
	private String role;

	@TableField("content")
	private String content;

	@TableField("trace_id")
	private String traceId;

	@TableField("refused")
	private Boolean refused;

	@TableField("kb_index_version_id")
	private Long kbIndexVersionId;

	@TableField("retrieval_ms")
	private Integer retrievalMs;

	@TableField("rerank_ms")
	private Integer rerankMs;

	@TableField("gen_ms")
	private Integer genMs;

	@TableField("created_at")
	private OffsetDateTime createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public void setSessionId(Long sessionId) {
		this.sessionId = sessionId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public Boolean getRefused() {
		return refused;
	}

	public void setRefused(Boolean refused) {
		this.refused = refused;
	}

	public Long getKbIndexVersionId() {
		return kbIndexVersionId;
	}

	public void setKbIndexVersionId(Long kbIndexVersionId) {
		this.kbIndexVersionId = kbIndexVersionId;
	}

	public Integer getRetrievalMs() {
		return retrievalMs;
	}

	public void setRetrievalMs(Integer retrievalMs) {
		this.retrievalMs = retrievalMs;
	}

	public Integer getRerankMs() {
		return rerankMs;
	}

	public void setRerankMs(Integer rerankMs) {
		this.rerankMs = rerankMs;
	}

	public Integer getGenMs() {
		return genMs;
	}

	public void setGenMs(Integer genMs) {
		this.genMs = genMs;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}
}
