package org.swpu.backend.modules.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.swpu.backend.modules.chat.dto.ChatDto;
import reactor.core.publisher.Mono;

@Component
public class RagClient {

	private final WebClient webClient;

	public RagClient(@Value("${rag.base-url:http://localhost:8000}") String baseUrl) {
		this.webClient = WebClient.builder().baseUrl(baseUrl).build();
	}

	// 向RAG微服务发送异步通信请求
	public Mono<ChatDto.RagResp> chat(ChatDto.RagChatReq req) {
		return webClient.post()
				.uri("/rag/v1/chat")
				.bodyValue(req)
				.retrieve()
				.bodyToMono(ChatDto.RagResp.class);
	}
}
