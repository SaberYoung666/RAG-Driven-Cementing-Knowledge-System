package org.swpu.backend.modules.docs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.TokenService;
import org.swpu.backend.common.security.TokenUser;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.rag.service.RagAvailabilityService;
import org.swpu.backend.modules.docs.converter.DocConverter;
import org.swpu.backend.modules.docs.dto.DocQuery;
import org.swpu.backend.modules.docs.dto.ProcessDocsRequest;
import org.swpu.backend.modules.docs.entity.DocEntity;
import org.swpu.backend.modules.docs.mapper.dao.DocMapper;
import org.swpu.backend.modules.docs.service.DocsRagClient;
import org.swpu.backend.modules.docs.service.DocsService;
import org.swpu.backend.modules.docs.vo.DocItem;
import org.swpu.backend.modules.docs.vo.DocProcessInfo;
import org.swpu.backend.modules.docs.vo.IngestResult;
import org.swpu.backend.modules.docs.vo.ProcessStartResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

// 文档服务实现
@Service
public class DocsServiceImpl implements DocsService {
	private static final String DISK_SOURCE_PREFIX = "disk:";
	private static final String TOKEN_TYPE = "Bearer";
	private static final String STATUS_UNPROCESSED = "未处理";
	private static final String STATUS_PROCESSING = "处理中";
	private static final String STATUS_PROCESS_FAILED = "处理失败";
	private static final String STATUS_PROCESSED = "已处理";
	private static final String STATUS_DELETED = "已删除";
	private static final Logger log = LoggerFactory.getLogger(DocsServiceImpl.class);

	private final DocMapper docMapper;
	private final TokenService tokenService;
	private final DocsRagClient docsRagClient;
	private final TaskExecutor taskExecutor;
	private final SystemLogService systemLogService;
	private final RagAvailabilityService ragAvailabilityService;
	private final Path storageRoot;
	private final Path tempRoot;

	public DocsServiceImpl(DocMapper docMapper, TokenService tokenService, DocsRagClient docsRagClient, TaskExecutor taskExecutor, SystemLogService systemLogService, RagAvailabilityService ragAvailabilityService, @Value("${docs.storage-dir:data/uploads/docs}") String storageDir) {
		this.docMapper = docMapper;
		this.tokenService = tokenService;
		this.docsRagClient = docsRagClient;
		this.taskExecutor = taskExecutor;
		this.systemLogService = systemLogService;
		this.ragAvailabilityService = ragAvailabilityService;
		this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
		this.tempRoot = this.storageRoot.resolve("_tmp");
	}

	@Override
	public PageResult<DocItem> listDocs(String bearerToken, DocQuery query) {
		Long userId = resolveCurrentUserId(bearerToken);
		int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
		int size = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

		QueryWrapper<DocEntity> wrapper = new QueryWrapper<>();
		wrapper.eq("deleted", false);
		wrapper.and(w -> w.eq("user_id", userId).or().eq("is_default", true));
		if (StringUtils.hasText(query.getKeyword())) {
			String keyword = query.getKeyword().trim();
			wrapper.and(w -> w.like("title", keyword).or().like("doc_id", keyword));
		}
		if (StringUtils.hasText(query.getStatus())) {
			wrapper.eq("status", query.getStatus().trim());
		}
		if (StringUtils.hasText(query.getCategory())) {
			wrapper.eq("category", query.getCategory().trim());
		}
		wrapper.orderByDesc("upload_time");

		Page<DocEntity> pageResult = docMapper.selectPage(new Page<>(page, size), wrapper);
		refreshProcessingStatuses(pageResult.getRecords());
		List<DocItem> items = DocConverter.toItems(pageResult.getRecords());
		return PageResult.of(items, pageResult.getTotal(), page, size);
	}

	// 删除文档
	@Override
	public boolean deleteDoc(String bearerToken, String docId) {
		Long userId = resolveCurrentUserId(bearerToken);
		// 若传入空文档编号参数则返回失败
		if (!StringUtils.hasText(docId)) {
			return false;
		}
		QueryWrapper<DocEntity> wrapper = new QueryWrapper<>();
		wrapper.eq("doc_id", docId.trim())
				.eq("deleted", false)
				.eq("is_default", false)
				.eq("user_id", userId);
		DocEntity entity = docMapper.selectOne(wrapper);
		// 若文档已删除或属于默认文档或不属于当前用户则返回失败
		if (entity == null) {
			return false;
		}
		entity.setDeleted(true);
		entity.setStatus(STATUS_DELETED);
		boolean deleted = docMapper.updateById(entity) > 0;
		recordDocLog(userId, "DELETE_DOC", deleted ? "Deleted document" : "Delete document skipped", docId, mapOf("deleted", deleted, "docName", entity.getTitle(), "status", entity.getStatus(), "chunkCount", entity.getChunkCount()));
		return deleted;
	}

	@Override
	public IngestResult ingestFile(String bearerToken, MultipartFile file, boolean overwrite, String category) {
		Long userId = resolveCurrentUserId(bearerToken);
		if (file == null || file.isEmpty()) {
			return new IngestResult(null, false, "file is empty");
		}
		String normalizedCategory = normalizeCategory(category);
		String originalFileName = file.getOriginalFilename();
		Path tempFile = createTempFilePath(originalFileName);
		try {
			Files.createDirectories(tempRoot);
			file.transferTo(tempFile);
		} catch (IOException ex) {
			throw new IllegalStateException("store temp file failed", ex);
		}
		String jobId = UUID.randomUUID().toString();
		recordDocLog(userId, "INGEST_ACCEPTED", "Accepted document ingest request", jobId, mapOf("fileName", originalFileName, "docName", originalFileName, "category", normalizedCategory, "overwrite", overwrite, "status", STATUS_UNPROCESSED));
		taskExecutor.execute(() -> processIngestAsync(jobId, userId, tempFile, originalFileName, overwrite, normalizedCategory));
		return new IngestResult(jobId, true, "accepted");
	}

	// 单个处理文档
	@Override
	public ProcessStartResult startProcessSingle(String bearerToken, String docId) {
		ProcessDocsRequest request = new ProcessDocsRequest();
		request.setDocIds(List.of(docId));
		return startProcessBatch(bearerToken, request);
	}

	// 批量处理文档
	@Override
	public ProcessStartResult startProcessBatch(String bearerToken, ProcessDocsRequest request) {
		ragAvailabilityService.requireProcessingAvailable();
		Long userId = resolveCurrentUserId(bearerToken);
		List<String> docIds = normalizeDocIds(request == null ? null : request.getDocIds());
		if (docIds.isEmpty()) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST, "未选择想处理的文档");
		}

		QueryWrapper<DocEntity> wrapper = new QueryWrapper<>();
		wrapper.in("doc_id", docIds)
				.eq("deleted", false)
				.eq("is_default", false)
				.eq("user_id", userId)
				.and(w -> w.eq("status", STATUS_UNPROCESSED)
						.or().eq("status", STATUS_PROCESS_FAILED));
		List<DocEntity> docs = docMapper.selectList(wrapper);
		if (docs.isEmpty()) {
			return new ProcessStartResult(UUID.randomUUID().toString(), List.of(), 0);
		}

		List<String> acceptedIds = new ArrayList<>();
		List<DocEntity> acceptedDocs = new ArrayList<>();
		// 修改文档处于修改中的状态
		for (DocEntity doc : docs) {
			doc.setStatus(STATUS_PROCESSING);
			docMapper.updateById(doc);
			acceptedIds.add(doc.getDocId());
			acceptedDocs.add(doc);
		}

		String requestId = UUID.randomUUID().toString();
		List<DocsRagClient.RagDocItem> ragDocs = acceptedDocs.stream()
				.map(doc -> new DocsRagClient.RagDocItem(
						doc.getDocId(),
						resolveAbsolutePath(doc.getSource()),
						toSourceFileName(doc.getSource())
				))
				.toList();

		docsRagClient.processDocs(userId, ragDocs)
				.doOnSuccess(resp -> {
					log.info("文档处理任务已提交至 RAG 微服务: requestId={}, docCount={}", requestId, acceptedIds.size());
					recordDocLog(userId, "PROCESS_DOCS", "Submitted docs processing request", requestId, mapOf("docIds", acceptedIds, "docCount", acceptedIds.size(), "status", STATUS_PROCESSING));
				})
				.doOnError(ex -> {
					markProcessFailed(acceptedIds);
					recordAsyncDocLog(userId, requestId, "PROCESS_DOCS_FAILED", "Docs processing request failed", (Exception) ex, mapOf("docIds", acceptedIds, "status", STATUS_PROCESS_FAILED));
					log.warn("先前的文档处理请求失败: requestId={}, userId={}, docIds={}", requestId, userId, acceptedIds, ex);
				})
				.subscribe();

		return new ProcessStartResult(requestId, acceptedIds, acceptedIds.size());
	}

	// 获取处理进度信息
	@Override
	public DocProcessInfo getProcessInfo(String bearerToken, String docId) {
		Long userId = resolveCurrentUserId(bearerToken);
		if (!StringUtils.hasText(docId)) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST, "docId为空");
		}

		QueryWrapper<DocEntity> wrapper = new QueryWrapper<>();
		wrapper.eq("doc_id", docId.trim())
				.eq("deleted", false);
		wrapper.and(w -> w.eq("user_id", userId).or().eq("is_default", true));
		DocEntity entity = docMapper.selectOne(wrapper);
		if (entity == null) {
			throw new BusinessException(CommonErrorCode.NOT_FOUND, "未找到文档");
		}

		DocsRagClient.RagIngestStatus ragStatus = syncStatusFromRag(entity);

		String rawStatus = ragStatus != null && StringUtils.hasText(ragStatus.status())
				? ragStatus.status()
				: Objects.toString(entity.getStatus(), STATUS_UNPROCESSED);
		String status = normalizeDocStatus(rawStatus);
		int progress = toProgress(rawStatus);
		String stage = resolveStage(rawStatus);
		String message = resolveProcessMessage(rawStatus, ragStatus);
		String updatedAt = firstNonBlank(
				ragStatus == null ? null : ragStatus.updatedAt(),
				ragStatus == null ? null : ragStatus.finishedAt(),
				entity.getUploadTime()
		);
		String detail = buildProcessDetail(ragStatus, message);
		return new DocProcessInfo(
				entity.getDocId(),
				status,
				progress,
				stage,
				message,
				entity.getChunkCount(),
				updatedAt,
				detail
		);
	}

	private void markProcessFailed(List<String> docIds) {
		if (docIds == null || docIds.isEmpty()) {
			return;
		}
		UpdateWrapper<DocEntity> wrapper = new UpdateWrapper<>();
		wrapper.in("doc_id", docIds).set("status", STATUS_PROCESS_FAILED);
		docMapper.update(null, wrapper);
	}

	// 使用哈希链表去重
	private List<String> normalizeDocIds(List<String> docIds) {
		if (docIds == null || docIds.isEmpty()) {
			return List.of();
		}
		LinkedHashSet<String> set = new LinkedHashSet<>();
		for (String raw : docIds) {
			if (StringUtils.hasText(raw)) {
				set.add(raw.trim());
			}
		}
		return new ArrayList<>(set);
	}

	private String normalizeCategory(String category) {
		if (!StringUtils.hasText(category)) {
			return "default";
		}
		String normalized = category.trim();
		if (normalized.length() > 100) {
			throw new BusinessException(CommonErrorCode.BAD_REQUEST, "category length must be less than or equal to 100");
		}
		return normalized;
	}

	private int toProgress(String status) {
		if ("queued".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
			return 10;
		}
		if (STATUS_PROCESSING.equals(status)
				|| "PROCESSING".equalsIgnoreCase(status)
				|| "RUNNING".equalsIgnoreCase(status)
				|| "ocr_processing".equalsIgnoreCase(status)
				|| "cleaning".equalsIgnoreCase(status)
				|| "splitting".equalsIgnoreCase(status)) {
			return 50;
		}
		if ("indexing".equalsIgnoreCase(status)) {
			return 80;
		}
		if (STATUS_PROCESSED.equals(status) || "READY".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
			return 100;
		}
		if (STATUS_PROCESS_FAILED.equals(status) || "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
			return 100;
		}
		return 0;
	}

	private String toSourceFileName(String source) {
		if (!StringUtils.hasText(source)) {
			return "";
		}
		String value = source.trim();
		if (value.startsWith(DISK_SOURCE_PREFIX)) {
			return value.substring(DISK_SOURCE_PREFIX.length());
		}
		return value;
	}

	private String resolveAbsolutePath(String source) {
		String relative = toSourceFileName(source);
		if (!StringUtils.hasText(relative)) {
			return "";
		}
		Path path = storageRoot.resolve(relative).normalize();
		return path.toString();
	}

	private void refreshProcessingStatuses(List<DocEntity> docs) {
		if (docs == null || docs.isEmpty()) {
			return;
		}
		for (DocEntity doc : docs) {
			if (doc == null || !isProcessingStatus(doc.getStatus())) {
				continue;
			}
			syncStatusFromRag(doc);
		}
	}

	// 异步从RAG获取文档处理进度
	private DocsRagClient.RagIngestStatus syncStatusFromRag(DocEntity entity) {
		if (entity == null || !StringUtils.hasText(entity.getDocId())) {
			return null;
		}
		try {
			DocsRagClient.RagIngestStatus rag = docsRagClient.getIngestStatus(entity.getDocId()).block(Duration.ofSeconds(5));
			if (rag == null || !StringUtils.hasText(rag.status())) {
				return rag;
			}
			String mapped = normalizeDocStatus(rag.status());
			entity.setStatus(mapped);
			if (rag.chunkCount() != null && rag.chunkCount() >= 0) {
				entity.setChunkCount(rag.chunkCount());
			}
			docMapper.updateById(entity);
			recordDocLog(entity.getUserId(), "SYNC_DOC_STATUS", "Synchronized document status from RAG", entity.getDocId(), mapOf("docId", entity.getDocId(), "docName", entity.getTitle(), "status", mapped, "chunkCount", entity.getChunkCount()));
			return rag;
		} catch (Exception ex) {
			log.debug("获取RAG文档处理进度失败: docId={}", entity.getDocId(), ex);
			recordAsyncDocLog(entity.getUserId(), entity.getDocId(), "SYNC_DOC_STATUS_FAILED", "Failed to sync document status", ex, null);
			return null;
		}
	}

	private String normalizeDocStatus(String raw) {
		String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "done", "success", "ready" -> STATUS_PROCESSED;
			case "failed", "error" -> STATUS_PROCESS_FAILED;
			case "queued", "processing", "indexing", "ocr_processing", "cleaning", "splitting", "running", "pending" -> STATUS_PROCESSING;
			default -> raw;
		};
	}

	private boolean isProcessingStatus(String status) {
		String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
		return List.of(
				STATUS_PROCESSING.toLowerCase(Locale.ROOT),
				"queued",
				"processing",
				"running",
				"pending",
				"ocr_processing",
				"cleaning",
				"splitting",
				"indexing"
		).contains(normalized);
	}

	private String resolveStage(String rawStatus) {
		String normalized = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "queued" -> "排队中";
			case "processing", "running", "pending", "处理中" -> "处理中";
			case "ocr_processing" -> "OCR处理中";
			case "cleaning" -> "文本清洗中";
			case "splitting" -> "切分中";
			case "indexing" -> "索引处理中";
			case "done", "success", "ready", "已处理" -> "处理完成";
			case "failed", "error", "处理失败" -> "处理失败";
			default -> "待处理";
		};
	}

	private String resolveProcessMessage(String rawStatus, DocsRagClient.RagIngestStatus ragStatus) {
		if (ragStatus != null) {
			if (StringUtils.hasText(ragStatus.error())) {
				return ragStatus.error().trim();
			}
			if (StringUtils.hasText(ragStatus.message())) {
				return ragStatus.message().trim();
			}
		}
		String normalized = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "queued" -> "任务已入队，等待执行";
			case "processing", "running", "pending", "处理中" -> "已提交 RAG 微服务处理";
			case "ocr_processing" -> "正在执行 OCR";
			case "cleaning" -> "正在清洗文本";
			case "splitting" -> "正在切分文档";
			case "indexing" -> "正在构建索引";
			case "done", "success", "ready", "已处理" -> "文档已完成切分与索引";
			case "failed", "error", "处理失败" -> "处理失败，请重试";
			default -> "未开始处理";
		};
	}

	private String buildProcessDetail(DocsRagClient.RagIngestStatus ragStatus, String fallbackMessage) {
		if (ragStatus == null) {
			return fallbackMessage;
		}
		List<String> parts = new ArrayList<>();
		if (StringUtils.hasText(ragStatus.message())) {
			parts.add(ragStatus.message().trim());
		}
		if (StringUtils.hasText(ragStatus.error())) {
			parts.add("错误: " + ragStatus.error().trim());
		}
		if (ragStatus.pagesProcessed() != null) {
			parts.add("已处理页数: " + ragStatus.pagesProcessed());
		}
		if (ragStatus.ocrPages() != null) {
			parts.add("OCR页数: " + ragStatus.ocrPages());
		}
		if (ragStatus.elapsedMs() != null) {
			parts.add("耗时: " + ragStatus.elapsedMs() + "ms");
		}
		if (ragStatus.failedPages() != null && !ragStatus.failedPages().isEmpty()) {
			parts.add("失败页: " + ragStatus.failedPages());
		}
		return parts.isEmpty() ? fallbackMessage : String.join(" | ", parts);
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private Long resolveCurrentUserId(String bearerToken) {
		TokenUser tokenUser = tokenService.verify(extractToken(bearerToken));
		if (tokenUser == null || tokenUser.getUserId() == null) {
			throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "invalid or expired token");
		}
		return tokenUser.getUserId();
	}

	private String extractToken(String bearerToken) {
		if (!StringUtils.hasText(bearerToken)) {
			return null;
		}
		String value = bearerToken.trim();
		if (value.regionMatches(true, 0, TOKEN_TYPE + " ", 0, TOKEN_TYPE.length() + 1)) {
			return value.substring(TOKEN_TYPE.length() + 1).trim();
		}
		return value;
	}

	private void processIngestAsync(String jobId, Long userId, Path tempFile, String originalFileName, boolean overwrite, String category) {
		try {
			String fileHash = computeSha256(tempFile);

			QueryWrapper<DocEntity> existsWrapper = new QueryWrapper<>();
			existsWrapper.eq("hash", fileHash).eq("user_id", userId).eq("is_default", false);
			DocEntity existing = docMapper.selectOne(existsWrapper);
			if (existing != null && !overwrite && !Boolean.TRUE.equals(existing.getDeleted())) {
				log.info("Skip ingest job because file already exists: jobId={}, userId={}, hash={}", jobId, userId, fileHash);
				recordAsyncDocLog(userId, jobId, "INGEST_SKIPPED", "Skipped ingest because file already exists", null, mapOf("hash", fileHash, "status", STATUS_UNPROCESSED));
				return;
			}

			DocEntity entity = existing != null ? existing : new DocEntity();
			if (existing == null) {
				entity.setDocId(UUID.randomUUID().toString());
			}

			entity.setTitle(StringUtils.hasText(originalFileName) ? originalFileName : "untitled");
			String storedRelativePath = moveTempFileToStorage(tempFile, fileHash, originalFileName);
			entity.setSource(DISK_SOURCE_PREFIX + storedRelativePath);
			entity.setUploadTime(Instant.now().toString());
			entity.setVersion("v1");
			entity.setHash(fileHash);
			entity.setCategory(category);
			entity.setUserId(userId);
			entity.setIsDefault(false);
			entity.setStatus(STATUS_UNPROCESSED);
			entity.setChunkCount(0);
			entity.setDeleted(false);

			if (existing == null) {
				docMapper.insert(entity);
			} else {
				docMapper.updateById(entity);
			}
			recordAsyncDocLog(userId, entity.getDocId(), "INGEST_STORED", "Stored uploaded document", null, mapOf("jobId", jobId, "docId", entity.getDocId(), "docName", entity.getTitle(), "hash", fileHash, "source", entity.getSource(), "status", entity.getStatus(), "chunkCount", entity.getChunkCount()));
		} catch (Exception ex) {
			log.error("Async ingest job failed: jobId={}, userId={}, file={}", jobId, userId, originalFileName, ex);
			recordAsyncDocLog(userId, jobId, "INGEST_FAILED", "Async ingest job failed", ex, mapOf("fileName", originalFileName, "docName", originalFileName, "status", STATUS_PROCESS_FAILED));
		} finally {
			try {
				Files.deleteIfExists(tempFile);
			} catch (IOException ex) {
				log.warn("Delete temp file failed: file={}", tempFile, ex);
			}
		}
	}

	private Path createTempFilePath(String originalFileName) {
		String ext = safeExtension(originalFileName);
		String tempName = UUID.randomUUID() + ext + ".tmp";
		return tempRoot.resolve(tempName).normalize();
	}

	private String moveTempFileToStorage(Path tempFile, String fileHash, String originalFileName) {
		String ext = safeExtension(originalFileName);
		String fileName = fileHash + ext;
		Path target = storageRoot.resolve(fileName).normalize();
		if (!target.startsWith(storageRoot)) {
			throw new IllegalStateException("invalid storage path");
		}
		try {
			Files.createDirectories(storageRoot);
			Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			try {
				Files.copy(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(tempFile);
			} catch (IOException copyEx) {
				throw new IllegalStateException("store file failed", copyEx);
			}
		}
		return fileName;
	}

	private String safeExtension(String fileName) {
		if (!StringUtils.hasText(fileName)) {
			return "";
		}
		String trimmed = fileName.trim();
		int dot = trimmed.lastIndexOf('.');
		if (dot < 0 || dot == trimmed.length() - 1) {
			return "";
		}
		String ext = trimmed.substring(dot + 1).toLowerCase(Locale.ROOT);
		if (!ext.matches("[a-z0-9]{1,16}")) {
			return "";
		}
		return "." + ext;
	}

	private void recordDocLog(Long userId, String action, String message, String resourceId, Map<String, Object> details) {
		systemLogService.record(new SystemLogCommand()
				.setTraceId(TraceContext.getTraceId())
				.setModule("DOCS")
				.setSource(LogConstants.SOURCE_BUSINESS)
				.setAction(action)
				.setLevel(LogConstants.LEVEL_INFO)
				.setSuccess(true)
				.setMessage(message)
				.setUserId(userId)
				.setVisibilityScope(LogConstants.SCOPE_PRIVATE)
				.setResourceType("DOC")
				.setResourceId(resourceId)
				.setDetails(details));
	}

	private void recordAsyncDocLog(Long userId, String resourceId, String action, String message, Exception ex, Map<String, Object> details) {
		systemLogService.record(new SystemLogCommand()
				.setTraceId(resourceId)
				.setModule("DOCS")
				.setSource(LogConstants.SOURCE_ASYNC)
				.setAction(action)
				.setLevel(ex == null ? LogConstants.LEVEL_INFO : LogConstants.LEVEL_ERROR)
				.setSuccess(ex == null)
				.setMessage(message)
				.setUserId(userId)
				.setVisibilityScope(userId == null ? LogConstants.SCOPE_SYSTEM : LogConstants.SCOPE_PRIVATE)
				.setResourceType("DOC")
				.setResourceId(resourceId)
				.setExceptionClass(ex == null ? null : ex.getClass().getName())
				.setDetails(details));
	}

	private Map<String, Object> mapOf(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			if (pairs[i] != null && pairs[i + 1] != null) {
				map.put(String.valueOf(pairs[i]), pairs[i + 1]);
			}
		}
		return map;
	}

	// 计算文件 SHA-256
	private String computeSha256(Path file) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not supported", ex);
		}
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("read file failed", ex);
		}
		byte[] hash = digest.digest();
		StringBuilder sb = new StringBuilder(hash.length * 2);
		for (byte b : hash) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
