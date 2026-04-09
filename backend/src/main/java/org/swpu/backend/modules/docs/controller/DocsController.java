package org.swpu.backend.modules.docs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.modules.docs.dto.DocQuery;
import org.swpu.backend.modules.docs.dto.ProcessDocsRequest;
import org.swpu.backend.modules.docs.dto.ReindexRequest;
import org.swpu.backend.modules.docs.service.DocsService;
import org.swpu.backend.modules.docs.vo.DeleteResult;
import org.swpu.backend.modules.docs.vo.DocItem;
import org.swpu.backend.modules.docs.vo.DocProcessInfo;
import org.swpu.backend.modules.docs.vo.IngestResult;
import org.swpu.backend.modules.docs.vo.ProcessStartResult;
import org.swpu.backend.modules.docs.vo.ReindexResult;

// 文档管理接口
@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Docs", description = "文档管理接口")
public class DocsController {
	private final DocsService docsService;

	public DocsController(DocsService docsService) {
		this.docsService = docsService;
	}

	// GET /api/v1/docs
	@GetMapping("/docs")
	@Operation(summary = "分页查询文档", description = "按关键字和状态过滤文档列表", security = {@SecurityRequirement(name = "bearerAuth")})
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功",
					content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权", content = @Content)
	})
	public ApiResponse<PageResult<DocItem>> listDocs(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@ParameterObject @ModelAttribute DocQuery query) {
		return ApiResponse.success(docsService.listDocs(authorization, query));
	}

	// DELETE /api/v1/docs/{docId}
	@DeleteMapping("/docs/{docId}")
	@Operation(summary = "删除文档", description = "按文档 ID 删除文档及关联索引", security = {@SecurityRequirement(name = "bearerAuth")})
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除结果",
					content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文档不存在", content = @Content)
	})
	public ApiResponse<DeleteResult> deleteDoc(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable String docId) {
		boolean deleted = docsService.deleteDoc(authorization, docId);
		return ApiResponse.success(new DeleteResult(deleted));
	}

	// POST /api/v1/ingest
	@PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "上传并入库文档", description = "上传文件并创建入库任务", security = {@SecurityRequirement(name = "bearerAuth")})
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "受理成功",
					content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "文件缺失或格式错误", content = @Content)
	})
	public ApiResponse<IngestResult> ingestFile(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@Parameter(description = "待入库文件", required = true)
			@RequestPart("file") MultipartFile file,
			@Parameter(description = "是否覆盖同名文档", example = "false")
			@RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite,
			@Parameter(description = "文档分类，可重名", example = "固井基础")
			@RequestParam(name = "category", required = false) String category) {
		return ApiResponse.success(docsService.ingestFile(authorization, file, overwrite, category));
	}

	@PostMapping("/docs/{docId}/process")
	@Operation(summary = "开始处理单个文档", description = "异步转发到 RAG 微服务进行处理", security = {@SecurityRequirement(name = "bearerAuth")})
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "受理成功",
					content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class)))
	})
	public ApiResponse<ProcessStartResult> startProcessSingle(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable String docId) {
		return ApiResponse.success(docsService.startProcessSingle(authorization, docId));
	}

	@GetMapping("/docs/{docId}/process")
	@Operation(summary = "查询文档处理进度", description = "查询单个文档最近处理状态", security = {@SecurityRequirement(name = "bearerAuth")})
	public ApiResponse<DocProcessInfo> getProcessInfo(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable String docId) {
		return ApiResponse.success(docsService.getProcessInfo(authorization, docId));
	}

	@PostMapping("/docs/process")
	@Operation(summary = "批量开始处理文档", description = "异步转发到 RAG 微服务进行处理", security = {@SecurityRequirement(name = "bearerAuth")})
	@ApiResponses(value = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "受理成功",
					content = @Content(schema = @Schema(implementation = org.swpu.backend.common.api.ApiResponse.class)))
	})
	public ApiResponse<ProcessStartResult> startProcessBatch(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@Valid @RequestBody ProcessDocsRequest request) {
		return ApiResponse.success(docsService.startProcessBatch(authorization, request));
	}

	@PostMapping("/docs/reindex")
	@Operation(summary = "重建知识库索引", description = "按需重建 FAISS 与 BM25 索引", security = {@SecurityRequirement(name = "bearerAuth")})
	public ApiResponse<ReindexResult> rebuildIndex(
			@Parameter(description = "Bearer Token，格式：Bearer {token}")
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestBody(required = false) ReindexRequest request) {
		return ApiResponse.success(docsService.rebuildIndex(authorization, request));
	}
}
