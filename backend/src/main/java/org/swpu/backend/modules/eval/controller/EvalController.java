package org.swpu.backend.modules.eval.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.eval.service.EvalRagClient;

@RestController
@RequestMapping("/api/v1/eval")
@Tag(name = "Eval", description = "评测触发接口")
public class EvalController {
	private static final Logger log = LoggerFactory.getLogger(EvalController.class);

	private final EvalRagClient evalRagClient;
	private final TaskExecutor taskExecutor;
	private final SystemLogService systemLogService;

	public EvalController(EvalRagClient evalRagClient, TaskExecutor taskExecutor, SystemLogService systemLogService) {
		this.evalRagClient = evalRagClient;
		this.taskExecutor = taskExecutor;
		this.systemLogService = systemLogService;
	}

	@PostMapping("/run")
	@Operation(summary = "触发离线评测")
	public ApiResponse<EvalRunResult> run(@RequestBody(required = false) EvalRunRequest request) {
		String jobId = UUID.randomUUID().toString();
		EvalRunRequest req = request == null ? new EvalRunRequest(null, List.of("dense", "hybrid"), List.of(false, true), 6, 20, 0.5) : request;
		systemLogService.record(new SystemLogCommand()
				.setTraceId(jobId)
				.setModule("EVAL")
				.setSource(LogConstants.SOURCE_ASYNC)
				.setAction("EVAL_RUN_ACCEPTED")
				.setLevel(LogConstants.LEVEL_INFO)
				.setSuccess(true)
				.setVisibilityScope(LogConstants.SCOPE_SYSTEM)
				.setMessage("Accepted evaluation task")
				.setResourceType("EVAL_JOB")
				.setResourceId(jobId)
				.setDetails(Map.of("datasetId", req.datasetId() == null ? "evaluation/questions.jsonl" : req.datasetId())));

		taskExecutor.execute(() -> {
			try {
				EvalRagClient.BatchRequest batchReq = new EvalRagClient.BatchRequest(
						req.datasetId() == null || req.datasetId().isBlank() ? "evaluation/questions.jsonl" : req.datasetId(),
						req.topK() == null ? 6 : req.topK(),
						req.candidateK() == null ? 20 : req.candidateK(),
						req.alpha() == null ? 0.5D : req.alpha()
				);
				EvalRagClient.BatchResponse batchResp = evalRagClient.batchEval(batchReq).block();
				if (batchResp != null && batchResp.resultCsv() != null) {
					evalRagClient.summarize(new EvalRagClient.SummarizeRequest(batchResp.resultCsv())).block();
				}
				systemLogService.record(new SystemLogCommand()
						.setTraceId(jobId)
						.setModule("EVAL")
						.setSource(LogConstants.SOURCE_ASYNC)
						.setAction("EVAL_RUN_COMPLETED")
						.setLevel(LogConstants.LEVEL_INFO)
						.setSuccess(true)
						.setVisibilityScope(LogConstants.SCOPE_SYSTEM)
						.setMessage("Evaluation task completed")
						.setResourceType("EVAL_JOB")
						.setResourceId(jobId)
						.setDetails(batchResp == null ? null : detailMap("rows", batchResp.rows(), "resultCsv", batchResp.resultCsv())));
			} catch (Exception ex) {
				log.warn("eval job failed: jobId={}", jobId, ex);
				systemLogService.record(new SystemLogCommand()
						.setTraceId(jobId)
						.setModule("EVAL")
						.setSource(LogConstants.SOURCE_ASYNC)
						.setAction("EVAL_RUN_FAILED")
						.setLevel(LogConstants.LEVEL_ERROR)
						.setSuccess(false)
						.setVisibilityScope(LogConstants.SCOPE_SYSTEM)
						.setMessage("Evaluation task failed")
						.setResourceType("EVAL_JOB")
						.setResourceId(jobId)
						.setExceptionClass(ex.getClass().getName())
						.setDetails(detailMap("error", ex.getMessage())));
			}
		});

		return ApiResponse.success(new EvalRunResult(jobId, true));
	}

	public record EvalRunRequest(
			String datasetId,
			List<String> modes,
			List<Boolean> rerankOptions,
			Integer topK,
			Integer candidateK,
			Double alpha
	) {
	}

	public record EvalRunResult(String jobId, boolean accepted) {
	}

	private Map<String, Object> detailMap(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] != null && pairs[i + 1] != null) {
				map.put(String.valueOf(pairs[i]), pairs[i + 1]);
			}
		}
		return map;
	}
}
