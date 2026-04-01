package org.swpu.backend.modules.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.swpu.backend.modules.chat.dto.ChatDto;
import org.swpu.backend.common.exception.BusinessException;
import reactor.core.publisher.Mono;

@Component
public class RagClient {

	private final WebClient webClient;
	private final ObjectMapper objectMapper;

	public RagClient(@Value("${rag.base-url:http://localhost:8000}") String baseUrl, ObjectMapper objectMapper) {
		this.webClient = WebClient.builder().baseUrl(baseUrl).build();
		this.objectMapper = objectMapper;
	}

	// 向RAG微服务发送异步通信请求
	public Mono<ChatDto.RagResp> chat(ChatDto.RagChatReq req) {
		return webClient.post()
				.uri("/rag/v1/chat")
				.bodyValue(req)
				.exchangeToMono(response -> {
					if (response.statusCode().is2xxSuccessful()) {
						return response.bodyToMono(ChatDto.RagResp.class);
					}
					return response.bodyToMono(String.class)
							.defaultIfEmpty("")
							.flatMap(body -> Mono.error(toBusinessException(response.statusCode(), body)));
				})
				.onErrorMap(WebClientRequestException.class, ex -> new BusinessException(502, "RAG service unavailable: " + rootCauseMessage(ex)));
	}

	private BusinessException toBusinessException(HttpStatusCode statusCode, String body) {
		int code = statusCode.value();
		String message = extractMessage(body);
		return new BusinessException(code, message);
	}

	private String extractMessage(String body) {
		if (body == null || body.isBlank()) {
			return "RAG service returned an empty error response";
		}
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode errorNode = root.path("error");
			if (!errorNode.isMissingNode() && !errorNode.isNull()) {
				String code = textOrNull(errorNode.get("code"));
				String message = textOrNull(errorNode.get("message"));
				String details = formatDetails(errorNode.get("details"));
				return joinParts("RAG chat failed", code, message, details);
			}
			String detail = textOrNull(root.get("detail"));
			if (detail != null) {
				return "RAG chat failed: " + detail;
			}
		} catch (Exception ignored) {
			// Fall back to raw body when the upstream response is not JSON.
		}
		return "RAG chat failed: " + body;
	}

	private String joinParts(String prefix, String code, String message, String details) {
		StringBuilder builder = new StringBuilder(prefix);
		if (code != null && !code.isBlank()) {
			builder.append(" [").append(code).append(']');
		}
		if (message != null && !message.isBlank()) {
			builder.append(": ").append(message);
		}
		if (details != null && !details.isBlank()) {
			builder.append(" (").append(details).append(')');
		}
		return builder.toString();
	}

	private String formatDetails(JsonNode detailsNode) {
		if (detailsNode == null || detailsNode.isNull() || detailsNode.isMissingNode()) {
			return null;
		}
		if (detailsNode.isTextual()) {
			return detailsNode.asText();
		}
		if (detailsNode.isObject()) {
			StringBuilder builder = new StringBuilder();
			Iterator<Map.Entry<String, JsonNode>> fields = detailsNode.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				if (builder.length() > 0) {
					builder.append(", ");
				}
				builder.append(field.getKey()).append('=').append(textOrNull(field.getValue()));
			}
			return builder.isEmpty() ? null : builder.toString();
		}
		if (detailsNode.isArray()) {
			StringBuilder builder = new StringBuilder();
			for (JsonNode item : detailsNode) {
				if (builder.length() > 0) {
					builder.append(", ");
				}
				builder.append(textOrNull(item));
			}
			return builder.isEmpty() ? null : builder.toString();
		}
		return detailsNode.toString();
	}

	private String textOrNull(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.isValueNode()) {
			return node.asText();
		}
		return node.toString();
	}

	private String rootCauseMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null || current.getMessage().isBlank()
				? current.getClass().getSimpleName()
				: current.getMessage();
	}
}
