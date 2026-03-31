package org.swpu.backend.modules.eval.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class EvalRagClient {
	private final WebClient webClient;

	public EvalRagClient(@Value("${rag.base-url:http://localhost:8000}") String baseUrl) {
		this.webClient = WebClient.builder().baseUrl(baseUrl).build();
	}

	public Mono<BatchResponse> batchEval(BatchRequest req) {
		return webClient.post()
				.uri("/rag/v1/eval/batch")
				.bodyValue(req)
				.retrieve()
				.bodyToMono(BatchResponse.class);
	}

	public Mono<SummarizeResponse> summarize(SummarizeRequest req) {
		return webClient.post()
				.uri("/rag/v1/eval/summarize")
				.bodyValue(req)
				.retrieve()
				.bodyToMono(SummarizeResponse.class);
	}

	public record BatchRequest(String questionsPath, Integer topK, Integer candidateK, Double alpha) {
	}

	public record BatchResponse(@JsonAlias({"result_csv"}) String resultCsv, Integer rows) {
	}

	public record SummarizeRequest(String resultsPath) {
	}

	public record SummarizeResponse(
			@JsonAlias({"overall_csv"}) String overallCsv,
			@JsonAlias({"by_type_csv"}) String byTypeCsv,
			@JsonAlias({"summary_md"}) String summaryMd
	) {
	}
}
