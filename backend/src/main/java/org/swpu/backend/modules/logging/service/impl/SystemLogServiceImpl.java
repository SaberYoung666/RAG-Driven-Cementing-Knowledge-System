package org.swpu.backend.modules.logging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.swpu.backend.common.api.CommonErrorCode;
import org.swpu.backend.common.api.PageResult;
import org.swpu.backend.common.exception.BusinessException;
import org.swpu.backend.common.logging.TraceContext;
import org.swpu.backend.common.security.AuthContextService;
import org.swpu.backend.modules.docs.entity.DocEntity;
import org.swpu.backend.modules.docs.mapper.dao.DocMapper;
import org.swpu.backend.modules.logging.LogConstants;
import org.swpu.backend.modules.logging.dto.SystemLogOverviewQuery;
import org.swpu.backend.modules.logging.dto.SystemLogQuery;
import org.swpu.backend.modules.logging.entity.SystemLogEntity;
import org.swpu.backend.modules.logging.mapper.dao.SystemLogMapper;
import org.swpu.backend.modules.logging.model.SystemLogCommand;
import org.swpu.backend.modules.logging.service.SystemLogService;
import org.swpu.backend.modules.logging.vo.SystemLogOverviewVo;
import org.swpu.backend.modules.logging.vo.SystemLogVo;

@Service
public class SystemLogServiceImpl implements SystemLogService {
    private static final Logger log = LoggerFactory.getLogger(SystemLogServiceImpl.class);
    private static final List<String> MODULE_ORDER = List.of("AUTH", "CHAT", "DOCS", "SESSION", "EVAL", "LOGGING", "SYSTEM");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH':00:00Z'");

    private final SystemLogMapper systemLogMapper;
    private final AuthContextService authContextService;
    private final ObjectMapper objectMapper;
    private final DocMapper docMapper;

    public SystemLogServiceImpl(SystemLogMapper systemLogMapper, AuthContextService authContextService, ObjectMapper objectMapper, DocMapper docMapper) {
        this.systemLogMapper = systemLogMapper;
        this.authContextService = authContextService;
        this.objectMapper = objectMapper;
        this.docMapper = docMapper;
    }

    @Override
    public void record(SystemLogCommand command) {
        if (command == null) {
            return;
        }
        try {
            SystemLogEntity entity = new SystemLogEntity();
            entity.setTraceId(StringUtils.hasText(command.getTraceId()) ? command.getTraceId() : TraceContext.getTraceId());
            entity.setUserId(command.getUserId());
            entity.setUsername(command.getUsername());
            entity.setUserRole(command.getUserRole());
            entity.setVisibilityScope(resolveScope(command));
            entity.setModule(defaultIfBlank(command.getModule(), "SYSTEM"));
            entity.setSource(defaultIfBlank(command.getSource(), LogConstants.SOURCE_BUSINESS));
            entity.setAction(defaultIfBlank(command.getAction(), "UNKNOWN"));
            entity.setLevel(defaultIfBlank(command.getLevel(), LogConstants.LEVEL_INFO));
            entity.setSuccess(command.getSuccess() == null ? Boolean.TRUE : command.getSuccess());
            entity.setMessage(defaultIfBlank(command.getMessage(), "no message"));
            entity.setDetailsJson(serializeDetails(command.getDetails()));
            entity.setResourceType(command.getResourceType());
            entity.setResourceId(command.getResourceId());
            entity.setHttpMethod(command.getHttpMethod());
            entity.setRequestPath(command.getRequestPath());
            entity.setStatusCode(command.getStatusCode());
            entity.setClientIp(command.getClientIp());
            entity.setDurationMs(command.getDurationMs());
            entity.setExceptionClass(command.getExceptionClass());
            entity.setCreatedAt(command.getCreatedAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : command.getCreatedAt());
            entity.setStartedAt(command.getStartedAt());
            entity.setFinishedAt(command.getFinishedAt());
            systemLogMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("Persist system log failed: module={}, action={}", command.getModule(), command.getAction(), ex);
        }
    }

    @Override
    public PageResult<SystemLogVo> listLogs(String bearerToken, SystemLogQuery query) {
        AuthContextService.CurrentUser currentUser = authContextService.resolveRequired(bearerToken);
        boolean admin = currentUser.isAdmin();
        int page = query == null || query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int size = query == null || query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();

        QueryWrapper<SystemLogEntity> wrapper = new QueryWrapper<>();
        if (!admin) {
            wrapper.and(w -> w.eq("user_id", currentUser.userId()).or().eq("visibility_scope", LogConstants.SCOPE_SYSTEM));
        } else if (query != null && query.getUserId() != null) {
            wrapper.eq("user_id", query.getUserId());
        }

        if (query != null) {
            if (StringUtils.hasText(query.getModule())) {
                wrapper.eq("module", query.getModule().trim().toUpperCase(Locale.ROOT));
            }
            if (StringUtils.hasText(query.getLevel())) {
                wrapper.eq("level", query.getLevel().trim().toUpperCase(Locale.ROOT));
            }
            if (StringUtils.hasText(query.getSource())) {
                wrapper.eq("source", query.getSource().trim().toUpperCase(Locale.ROOT));
            }
            if (StringUtils.hasText(query.getTraceId())) {
                wrapper.eq("trace_id", query.getTraceId().trim());
            }
            if (StringUtils.hasText(query.getAction())) {
                wrapper.like("action", query.getAction().trim());
            }
            if (StringUtils.hasText(query.getScope())) {
                wrapper.eq("visibility_scope", query.getScope().trim().toUpperCase(Locale.ROOT));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String keyword = query.getKeyword().trim();
                wrapper.and(w -> w.like("message", keyword)
                        .or().like("details_json", keyword)
                        .or().like("request_path", keyword)
                        .or().like("username", keyword)
                        .or().like("resource_id", keyword));
            }
            if (StringUtils.hasText(query.getFrom())) {
                wrapper.ge("created_at", parseOffsetDateTime(query.getFrom()));
            }
            if (StringUtils.hasText(query.getTo())) {
                wrapper.le("created_at", parseOffsetDateTime(query.getTo()));
            }
        }

        wrapper.orderByDesc("created_at");
        Page<SystemLogEntity> pageResult = systemLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<SystemLogVo> items = pageResult.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(items, pageResult.getTotal(), page, size);
    }

    @Override
    public SystemLogOverviewVo overview(String bearerToken, SystemLogOverviewQuery query) {
        AuthContextService.CurrentUser currentUser = authContextService.resolveRequired(bearerToken);
        ResolvedOverviewQuery resolved = resolveOverviewQuery(query);

        QueryWrapper<SystemLogEntity> wrapper = new QueryWrapper<>();
        VisibilityScope visibilityScope = applyOverviewVisibility(wrapper, currentUser, resolved.scope(), resolved.userId());
        wrapper.ge("created_at", resolved.from()).le("created_at", resolved.to());
        if (StringUtils.hasText(resolved.module())) {
            wrapper.eq("module", resolved.module());
        }
        wrapper.orderByAsc("created_at");

        List<AnalyticsLog> logs = toAnalyticsLogs(systemLogMapper.selectList(wrapper));
        Map<String, String> docNameLookup = loadDocNames(collectDocIds(logs));
        List<AnalyticsLog> requestLogs = logs.stream().filter(this::isCompletedRequestLog).toList();
        List<AnalyticsLog> chatLogs = logs.stream().filter(logItem -> "CHAT".equals(logItem.entity().getModule()) && "CHAT".equals(logItem.entity().getAction())).toList();
        List<AnalyticsLog> docsLogs = logs.stream().filter(logItem -> "DOCS".equals(logItem.entity().getModule())).toList();

        return new SystemLogOverviewVo(
                new SystemLogOverviewVo.QueryInfo(resolved.from().toString(), resolved.to().toString(), resolved.granularity(), resolved.module(), resolved.userId(), resolved.scope().toLowerCase(Locale.ROOT), resolved.topN()),
                buildSummary(requestLogs, logs),
                buildTrends(requestLogs, resolved.granularity()),
                buildChatAnalytics(chatLogs, docNameLookup, resolved.topN()),
                buildDocsAnalytics(docsLogs, docNameLookup, resolved.granularity(), resolved.topN()),
                buildExceptions(logs, resolved.topN()),
                new SystemLogOverviewVo.Meta(OffsetDateTime.now(ZoneOffset.UTC).toString(), visibilityScope.appliedScope())
        );
    }

    private SystemLogOverviewVo.Summary buildSummary(List<AnalyticsLog> requestLogs, List<AnalyticsLog> logs) {
        long requestCount = requestLogs.size();
        long successCount = requestLogs.stream().filter(logItem -> Boolean.TRUE.equals(logItem.entity().getSuccess())).count();
        long errorCount = requestLogs.stream().filter(logItem -> !Boolean.TRUE.equals(logItem.entity().getSuccess()) || "ERROR".equalsIgnoreCase(logItem.entity().getLevel())).count();
        double errorRate = requestCount == 0 ? 0D : round((double) errorCount / requestCount, 4);
        double avgDurationMs = round(averageLong(requestLogs.stream().map(logItem -> logItem.entity().getDurationMs()).toList()), 1);
        long systemErrorCount = logs.stream().filter(logItem -> LogConstants.SCOPE_SYSTEM.equalsIgnoreCase(logItem.entity().getVisibilityScope())).filter(logItem -> "ERROR".equalsIgnoreCase(logItem.entity().getLevel())).count();
        long activeUserCount = requestLogs.stream().map(logItem -> logItem.entity().getUserId()).filter(id -> id != null).distinct().count();
        return new SystemLogOverviewVo.Summary(requestCount, successCount, errorCount, errorRate, avgDurationMs, systemErrorCount, activeUserCount);
    }

    private SystemLogOverviewVo.Trends buildTrends(List<AnalyticsLog> requestLogs, String granularity) {
        Map<String, TrendAccumulator> buckets = new HashMap<>();
        for (AnalyticsLog logItem : requestLogs) {
            String module = normalizeModule(logItem.entity().getModule());
            String timeBucket = toTimeBucket(logItem.entity().getCreatedAt(), granularity);
            String key = timeBucket + "|" + module;
            TrendAccumulator acc = buckets.computeIfAbsent(key, ignored -> new TrendAccumulator(timeBucket, module));
            acc.requestCount++;
            if (!Boolean.TRUE.equals(logItem.entity().getSuccess()) || "ERROR".equalsIgnoreCase(logItem.entity().getLevel())) {
                acc.errorCount++;
            }
            if (logItem.entity().getDurationMs() != null) {
                acc.durationSum += logItem.entity().getDurationMs();
                acc.durationCount++;
            }
        }

        List<SystemLogOverviewVo.ModuleTrendPoint> points = buckets.values().stream()
                .sorted(Comparator.comparing(TrendAccumulator::time).thenComparing(acc -> MODULE_ORDER.indexOf(acc.module())))
                .map(acc -> new SystemLogOverviewVo.ModuleTrendPoint(acc.time(), acc.module(), acc.requestCount, acc.errorCount, round(acc.durationCount == 0 ? 0D : (double) acc.durationSum / acc.durationCount, 1)))
                .toList();
        return new SystemLogOverviewVo.Trends(granularity, points);
    }

    private SystemLogOverviewVo.Exceptions buildExceptions(List<AnalyticsLog> logs, int topN) {
        Map<String, ExceptionAccumulator> buckets = new HashMap<>();
        for (AnalyticsLog logItem : logs) {
            boolean isExceptionLog = LogConstants.SOURCE_EXCEPTION.equalsIgnoreCase(logItem.entity().getSource())
                    || logItem.entity().getExceptionClass() != null
                    || (LogConstants.SOURCE_REQUEST.equalsIgnoreCase(logItem.entity().getSource()) && !Boolean.TRUE.equals(logItem.entity().getSuccess()));
            if (!isExceptionLog) {
                continue;
            }

            String exceptionClass = defaultIfBlank(logItem.entity().getExceptionClass(), "REQUEST_FAILED");
            String messageSample = defaultIfBlank(logItem.entity().getMessage(), "unknown error");
            String key = exceptionClass + "|" + messageSample + "|" + normalizeModule(logItem.entity().getModule()) + "|" + defaultIfBlank(logItem.entity().getAction(), "UNKNOWN");
            ExceptionAccumulator acc = buckets.computeIfAbsent(key, ignored -> new ExceptionAccumulator(exceptionClass, messageSample, normalizeModule(logItem.entity().getModule()), defaultIfBlank(logItem.entity().getAction(), "UNKNOWN"), defaultIfBlank(logItem.entity().getLevel(), LogConstants.LEVEL_ERROR)));
            acc.count++;
            if (acc.latestOccurredAt == null || laterThan(logItem.entity().getCreatedAt(), acc.latestOccurredAt)) {
                acc.latestOccurredAt = logItem.entity().getCreatedAt();
                acc.level = defaultIfBlank(logItem.entity().getLevel(), LogConstants.LEVEL_ERROR);
            }
        }

        List<SystemLogOverviewVo.ExceptionTopItem> items = buckets.values().stream()
                .sorted(Comparator.comparingLong(ExceptionAccumulator::count).reversed().thenComparing(ExceptionAccumulator::latestOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topN)
                .map(acc -> new SystemLogOverviewVo.ExceptionTopItem(acc.exceptionClass(), truncate(acc.messageSample(), 200), acc.count(), acc.latestOccurredAt() == null ? null : acc.latestOccurredAt().toString(), acc.module(), acc.action(), acc.level()))
                .toList();
        return new SystemLogOverviewVo.Exceptions(items);
    }

    private SystemLogOverviewVo.ChatAnalytics buildChatAnalytics(List<AnalyticsLog> chatLogs, Map<String, String> docNameLookup, int topN) {
        long chatCount = chatLogs.size();
        long refusedCount = chatLogs.stream().filter(logItem -> asBoolean(logItem.details(), "refused")).count();
        double refusedRate = chatCount == 0 ? 0D : round((double) refusedCount / chatCount, 4);
        double avgLatencyMs = round(averageDouble(chatLogs.stream().map(logItem -> asDouble(logItem.details(), "latencyMs")).toList()), 1);
        double avgRetrievalMs = round(averageDouble(chatLogs.stream().map(logItem -> asDouble(logItem.details(), "retrievalMs")).toList()), 1);
        double avgRerankMs = round(averageDouble(chatLogs.stream().map(logItem -> asDouble(logItem.details(), "rerankMs")).toList()), 1);
        double avgGenMs = round(averageDouble(chatLogs.stream().map(logItem -> asDouble(logItem.details(), "genMs")).toList()), 1);
        double avgCitationCount = round(averageDouble(chatLogs.stream().map(logItem -> asDouble(logItem.details(), "citationCount")).toList()), 1);

        Map<String, Long> modeBuckets = new LinkedHashMap<>();
        Map<Integer, Long> topKBuckets = new LinkedHashMap<>();
        Map<Integer, TopKScoreAccumulator> top1ScoreBuckets = new LinkedHashMap<>();
        Map<String, DocHitAccumulator> docHitBuckets = new LinkedHashMap<>();

        for (AnalyticsLog logItem : chatLogs) {
            String mode = defaultIfBlank(asText(logItem.details(), "mode"), "unknown");
            modeBuckets.merge(mode, 1L, Long::sum);

            Integer topK = asInteger(logItem.details(), "topK");
            if (topK != null) {
                topKBuckets.merge(topK, 1L, Long::sum);
                TopKScoreAccumulator acc = top1ScoreBuckets.computeIfAbsent(topK, ignored -> new TopKScoreAccumulator(topK));
                Double top1Score = asDouble(logItem.details(), "top1Score");
                if (top1Score != null) {
                    acc.top1ScoreSum += top1Score;
                    acc.top1ScoreCount++;
                }
                Double avgCitationScore = asDouble(logItem.details(), "avgCitationScore");
                if (avgCitationScore != null) {
                    acc.citationScoreSum += avgCitationScore;
                    acc.citationScoreCount++;
                }
                Double latencyMs = asDouble(logItem.details(), "latencyMs");
                if (latencyMs != null) {
                    acc.latencySum += latencyMs;
                    acc.latencyCount++;
                }
            }

            for (JsonNode docNode : arrayElements(logItem.details(), "docs")) {
                String docId = asText(docNode, "docId");
                String docName = firstNonBlank(asText(docNode, "docName"), docNameLookup.get(docId), docId);
                String key = firstNonBlank(docId, docName, "UNKNOWN");
                DocHitAccumulator acc = docHitBuckets.computeIfAbsent(key, ignored -> new DocHitAccumulator(docId, docName));
                acc.hitCount++;
                if (StringUtils.hasText(docName)) {
                    acc.docName = docName;
                } else if (StringUtils.hasText(docId) && docNameLookup.containsKey(docId)) {
                    acc.docName = docNameLookup.get(docId);
                }
                Double score = asDouble(docNode, "score");
                if (score != null) {
                    acc.scoreSum += score;
                    acc.scoreCount++;
                }
                if (acc.lastHitAt == null || laterThan(logItem.entity().getCreatedAt(), acc.lastHitAt)) {
                    acc.lastHitAt = logItem.entity().getCreatedAt();
                }
            }
        }

        List<SystemLogOverviewVo.ModeDistributionItem> modeDistribution = modeBuckets.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new SystemLogOverviewVo.ModeDistributionItem(entry.getKey(), entry.getValue(), chatCount == 0 ? 0D : round((double) entry.getValue() / chatCount, 4)))
                .toList();

        List<SystemLogOverviewVo.TopKDistributionItem> topKDistribution = topKBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SystemLogOverviewVo.TopKDistributionItem(entry.getKey(), entry.getValue(), chatCount == 0 ? 0D : round((double) entry.getValue() / chatCount, 4)))
                .toList();

        List<SystemLogOverviewVo.Top1ScoreByTopKItem> top1ScoreByTopK = top1ScoreBuckets.values().stream()
                .sorted(Comparator.comparingInt(TopKScoreAccumulator::topK))
                .map(acc -> new SystemLogOverviewVo.Top1ScoreByTopKItem(acc.topK(), acc.top1ScoreCount, round(acc.top1ScoreCount == 0 ? 0D : acc.top1ScoreSum / acc.top1ScoreCount, 4), round(acc.citationScoreCount == 0 ? 0D : acc.citationScoreSum / acc.citationScoreCount, 4), round(acc.latencyCount == 0 ? 0D : acc.latencySum / acc.latencyCount, 1)))
                .toList();

        List<SystemLogOverviewVo.TopHitDocItem> topHitDocs = docHitBuckets.values().stream()
                .sorted(Comparator.comparingLong(DocHitAccumulator::hitCount).reversed().thenComparing(DocHitAccumulator::lastHitAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topN)
                .map(acc -> new SystemLogOverviewVo.TopHitDocItem(acc.docId, firstNonBlank(acc.docName, docNameLookup.get(acc.docId), acc.docId), acc.hitCount, round(acc.scoreCount == 0 ? 0D : acc.scoreSum / acc.scoreCount, 4), acc.lastHitAt == null ? null : acc.lastHitAt.toString()))
                .toList();

        return new SystemLogOverviewVo.ChatAnalytics(new SystemLogOverviewVo.ChatOverview(chatCount, refusedCount, refusedRate, avgLatencyMs, avgRetrievalMs, avgRerankMs, avgGenMs, avgCitationCount), modeDistribution, topKDistribution, top1ScoreByTopK, topHitDocs);
    }

    private SystemLogOverviewVo.DocsAnalytics buildDocsAnalytics(List<AnalyticsLog> docsLogs, Map<String, String> docNameLookup, String granularity, int topN) {
        Map<String, Long> uploadTrendBuckets = new LinkedHashMap<>();
        long uploadCount = 0L;
        long processRequestCount = 0L;
        Set<String> successDocIds = new HashSet<>();
        List<Double> successChunkCounts = new ArrayList<>();
        Set<String> failedDocIds = new HashSet<>();
        Map<String, FailedDocAccumulator> failedDocBuckets = new HashMap<>();

        for (AnalyticsLog logItem : docsLogs) {
            String action = defaultIfBlank(logItem.entity().getAction(), "");
            if ("INGEST_ACCEPTED".equalsIgnoreCase(action)) {
                uploadCount++;
                uploadTrendBuckets.merge(toTimeBucket(logItem.entity().getCreatedAt(), granularity), 1L, Long::sum);
            } else if ("PROCESS_DOCS".equalsIgnoreCase(action)) {
                Integer docCount = asInteger(logItem.details(), "docCount");
                processRequestCount += docCount == null || docCount < 1 ? 1L : docCount;
            }

            if ("SYNC_DOC_STATUS".equalsIgnoreCase(action)) {
                String docId = firstNonBlank(asText(logItem.details(), "docId"), logItem.entity().getResourceId());
                String docName = firstNonBlank(asText(logItem.details(), "docName"), docNameLookup.get(docId));
                String status = firstNonBlank(asText(logItem.details(), "status"), "");
                Integer chunkCount = asInteger(logItem.details(), "chunkCount");
                if (isProcessedStatus(status) && StringUtils.hasText(docId)) {
                    successDocIds.add(docId);
                    if (chunkCount != null) {
                        successChunkCounts.add(chunkCount.doubleValue());
                    }
                }
                if (isFailedStatus(status) && StringUtils.hasText(docId)) {
                    registerFailedDoc(failedDocIds, failedDocBuckets, docId, docName, logItem.entity().getCreatedAt(), logItem.entity().getMessage());
                }
            } else if ("SYNC_DOC_STATUS_FAILED".equalsIgnoreCase(action)) {
                registerFailedDoc(failedDocIds, failedDocBuckets, logItem.entity().getResourceId(), asText(logItem.details(), "docName"), logItem.entity().getCreatedAt(), logItem.entity().getMessage());
            } else if ("PROCESS_DOCS_FAILED".equalsIgnoreCase(action)) {
                List<String> docIds = textList(logItem.details(), "docIds");
                if (docIds.isEmpty() && StringUtils.hasText(logItem.entity().getResourceId())) {
                    docIds = List.of(logItem.entity().getResourceId());
                }
                for (String docId : docIds) {
                    registerFailedDoc(failedDocIds, failedDocBuckets, docId, docNameLookup.get(docId), logItem.entity().getCreatedAt(), logItem.entity().getMessage());
                }
            }
        }

        List<SystemLogOverviewVo.TimeCountPoint> uploadTrend = uploadTrendBuckets.entrySet().stream()
                .map(entry -> new SystemLogOverviewVo.TimeCountPoint(entry.getKey(), entry.getValue()))
                .toList();

        long processSuccessCount = successDocIds.size();
        long processFailedCount = failedDocIds.size();
        double processSuccessRate = processRequestCount == 0 ? 0D : round((double) processSuccessCount / processRequestCount, 4);
        double avgChunkCount = round(averageDouble(successChunkCounts), 1);

        List<SystemLogOverviewVo.FailedDocItem> failedDocsTop = failedDocBuckets.values().stream()
                .sorted(Comparator.comparingLong(FailedDocAccumulator::failCount).reversed().thenComparing(FailedDocAccumulator::latestFailedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(topN)
                .map(acc -> new SystemLogOverviewVo.FailedDocItem(acc.docId, firstNonBlank(acc.docName, docNameLookup.get(acc.docId), acc.docId), acc.failCount, acc.latestFailedAt == null ? null : acc.latestFailedAt.toString(), truncate(acc.latestReason, 200)))
                .toList();

        return new SystemLogOverviewVo.DocsAnalytics(new SystemLogOverviewVo.UploadOverview(uploadCount, uploadTrend), new SystemLogOverviewVo.ProcessOverview(processRequestCount, processSuccessCount, processFailedCount, processSuccessRate, avgChunkCount), failedDocsTop);
    }

    private void registerFailedDoc(Set<String> failedDocIds, Map<String, FailedDocAccumulator> failedDocBuckets, String docId, String docName, OffsetDateTime failedAt, String reason) {
        if (!StringUtils.hasText(docId)) {
            return;
        }
        failedDocIds.add(docId);
        FailedDocAccumulator acc = failedDocBuckets.computeIfAbsent(docId, ignored -> new FailedDocAccumulator(docId, docName));
        acc.failCount++;
        acc.latestFailedAt = maxTime(acc.latestFailedAt, failedAt);
        acc.latestReason = firstNonBlank(reason, acc.latestReason, "unknown");
        if (StringUtils.hasText(docName)) {
            acc.docName = docName;
        }
    }

    private Set<String> collectDocIds(List<AnalyticsLog> logs) {
        Set<String> docIds = new HashSet<>();
        for (AnalyticsLog logItem : logs) {
            if ("DOC".equalsIgnoreCase(logItem.entity().getResourceType()) && StringUtils.hasText(logItem.entity().getResourceId())) {
                docIds.add(logItem.entity().getResourceId());
            }
            String detailDocId = asText(logItem.details(), "docId");
            if (StringUtils.hasText(detailDocId)) {
                docIds.add(detailDocId);
            }
            for (String docId : textList(logItem.details(), "docIds")) {
                if (StringUtils.hasText(docId)) {
                    docIds.add(docId);
                }
            }
            for (JsonNode docNode : arrayElements(logItem.details(), "docs")) {
                String docId = asText(docNode, "docId");
                if (StringUtils.hasText(docId)) {
                    docIds.add(docId);
                }
            }
        }
        return docIds;
    }

    private Map<String, String> loadDocNames(Set<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Map.of();
        }
        List<DocEntity> docs = docMapper.selectBatchIds(docIds);
        Map<String, String> lookup = new HashMap<>();
        for (DocEntity doc : docs) {
            if (doc != null && StringUtils.hasText(doc.getDocId()) && StringUtils.hasText(doc.getTitle())) {
                lookup.put(doc.getDocId(), doc.getTitle());
            }
        }
        return lookup;
    }

    private List<AnalyticsLog> toAnalyticsLogs(List<SystemLogEntity> entities) {
        List<AnalyticsLog> logs = new ArrayList<>();
        for (SystemLogEntity entity : entities) {
            logs.add(new AnalyticsLog(entity, parseDetails(entity.getDetailsJson())));
        }
        return logs;
    }

    private JsonNode parseDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(detailsJson);
        } catch (Exception ex) {
            return MissingNode.getInstance();
        }
    }

    private boolean isCompletedRequestLog(AnalyticsLog logItem) {
        return LogConstants.SOURCE_REQUEST.equalsIgnoreCase(logItem.entity().getSource()) && logItem.entity().getFinishedAt() != null;
    }

    private VisibilityScope applyOverviewVisibility(QueryWrapper<SystemLogEntity> wrapper, AuthContextService.CurrentUser currentUser, String scope, Long requestedUserId) {
        String normalizedScope = normalizeOverviewScope(scope);
        if (!currentUser.isAdmin()) {
            if (requestedUserId != null && !requestedUserId.equals(currentUser.userId())) {
                throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "cannot query other users logs");
            }
            return switch (normalizedScope) {
                case "SELF" -> {
                    wrapper.eq("user_id", currentUser.userId());
                    yield new VisibilityScope("user_self");
                }
                case "SYSTEM" -> {
                    wrapper.eq("visibility_scope", LogConstants.SCOPE_SYSTEM);
                    yield new VisibilityScope("user_system");
                }
                default -> {
                    wrapper.and(w -> w.eq("user_id", currentUser.userId()).or().eq("visibility_scope", LogConstants.SCOPE_SYSTEM));
                    yield new VisibilityScope("user_all");
                }
            };
        }

        Long targetUserId = requestedUserId;
        return switch (normalizedScope) {
            case "SELF" -> {
                wrapper.eq("user_id", targetUserId == null ? currentUser.userId() : targetUserId);
                yield new VisibilityScope(targetUserId == null ? "admin_self" : "admin_user_self");
            }
            case "SYSTEM" -> {
                wrapper.eq("visibility_scope", LogConstants.SCOPE_SYSTEM);
                yield new VisibilityScope("admin_system");
            }
            default -> {
                if (targetUserId != null) {
                    wrapper.and(w -> w.eq("user_id", targetUserId).or().eq("visibility_scope", LogConstants.SCOPE_SYSTEM));
                    yield new VisibilityScope("admin_user_all");
                }
                yield new VisibilityScope("admin_all");
            }
        };
    }

    private ResolvedOverviewQuery resolveOverviewQuery(SystemLogOverviewQuery query) {
        OffsetDateTime to = StringUtils.hasText(query == null ? null : query.getTo())
                ? parseOffsetDateTime(query.getTo())
                : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = StringUtils.hasText(query == null ? null : query.getFrom())
                ? parseOffsetDateTime(query.getFrom())
                : to.minusDays(7);
        if (from.isAfter(to)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "from must be earlier than to");
        }
        String granularity = normalizeGranularity(query == null ? null : query.getGranularity());
        String module = StringUtils.hasText(query == null ? null : query.getModule()) ? query.getModule().trim().toUpperCase(Locale.ROOT) : null;
        String scope = normalizeOverviewScope(query == null ? null : query.getScope());
        int topN = query == null || query.getTopN() == null || query.getTopN() < 1 ? 10 : Math.min(query.getTopN(), 100);
        Long userId = query == null ? null : query.getUserId();
        return new ResolvedOverviewQuery(from, to, granularity, module, scope, topN, userId);
    }

    private SystemLogVo toVo(SystemLogEntity entity) {
        return new SystemLogVo(
                entity.getId(),
                entity.getTraceId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getUserRole(),
                entity.getVisibilityScope(),
                entity.getModule(),
                entity.getSource(),
                entity.getAction(),
                entity.getLevel(),
                entity.getSuccess(),
                entity.getMessage(),
                entity.getDetailsJson(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getHttpMethod(),
                entity.getRequestPath(),
                entity.getStatusCode(),
                entity.getClientIp(),
                entity.getDurationMs(),
                entity.getExceptionClass(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getStartedAt() == null ? null : entity.getStartedAt().toString(),
                entity.getFinishedAt() == null ? null : entity.getFinishedAt().toString()
        );
    }

    private String serializeDetails(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return String.valueOf(details);
        }
    }

    private String resolveScope(SystemLogCommand command) {
        if (StringUtils.hasText(command.getVisibilityScope())) {
            return command.getVisibilityScope().trim().toUpperCase(Locale.ROOT);
        }
        return command.getUserId() == null ? LogConstants.SCOPE_SYSTEM : LogConstants.SCOPE_PRIVATE;
    }

    private OffsetDateTime parseOffsetDateTime(String text) {
        try {
            return Instant.parse(text.trim()).atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "invalid time format, expected ISO-8601");
        }
    }

    private String normalizeOverviewScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "ALL";
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SELF", "SYSTEM", "ALL").contains(normalized)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "scope must be self, system or all");
        }
        return normalized;
    }

    private String normalizeGranularity(String granularity) {
        if (!StringUtils.hasText(granularity)) {
            return "day";
        }
        String normalized = granularity.trim().toLowerCase(Locale.ROOT);
        if (!List.of("day", "hour").contains(normalized)) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "granularity must be day or hour");
        }
        return normalized;
    }

    private String normalizeModule(String module) {
        if (!StringUtils.hasText(module)) {
            return "SYSTEM";
        }
        String normalized = module.trim().toUpperCase(Locale.ROOT);
        return MODULE_ORDER.contains(normalized) ? normalized : "SYSTEM";
    }

    private String toTimeBucket(OffsetDateTime time, String granularity) {
        OffsetDateTime normalized = (time == null ? OffsetDateTime.now(ZoneOffset.UTC) : time).withOffsetSameInstant(ZoneOffset.UTC);
        return "hour".equalsIgnoreCase(granularity) ? normalized.format(HOUR_FORMATTER) : normalized.format(DAY_FORMATTER);
    }

    private boolean laterThan(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) {
            return false;
        }
        if (right == null) {
            return true;
        }
        return left.isAfter(right);
    }

    private OffsetDateTime maxTime(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private boolean isProcessedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return List.of("已处理", "done", "ready", "success").contains(normalized);
    }

    private boolean isFailedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return List.of("处理失败", "failed", "error").contains(normalized);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Double asDouble(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.isNumber() ? child.doubleValue() : parseDouble(child.asText(null));
    }

    private Integer asInteger(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.isInt() || child.isLong() ? child.intValue() : parseInteger(child.asText(null));
    }

    private Boolean asBoolean(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return false;
        }
        return child.isBoolean() ? child.booleanValue() : Boolean.parseBoolean(child.asText("false"));
    }

    private String asText(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private List<String> textList(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (!child.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : child) {
            if (item != null && !item.isNull() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }

    private List<JsonNode> arrayElements(JsonNode node, String field) {
        JsonNode child = node == null ? MissingNode.getInstance() : node.path(field);
        if (!child.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        child.forEach(items::add);
        return items;
    }

    private Double parseDouble(String value) {
        try {
            return StringUtils.hasText(value) ? Double.parseDouble(value.trim()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.parseInt(value.trim()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private double averageDouble(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        double sum = 0D;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0D : sum / count;
    }

    private double averageLong(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0D;
        }
        double sum = 0D;
        int count = 0;
        for (Long value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0D : sum / count;
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record AnalyticsLog(SystemLogEntity entity, JsonNode details) {
    }

    private record ResolvedOverviewQuery(
            OffsetDateTime from,
            OffsetDateTime to,
            String granularity,
            String module,
            String scope,
            int topN,
            Long userId
    ) {
    }

    private record VisibilityScope(String appliedScope) {
    }

    private static final class TrendAccumulator {
        private final String time;
        private final String module;
        private long requestCount;
        private long errorCount;
        private long durationSum;
        private long durationCount;

        private TrendAccumulator(String time, String module) {
            this.time = time;
            this.module = module;
        }

        private String time() {
            return time;
        }

        private String module() {
            return module;
        }
    }

    private static final class ExceptionAccumulator {
        private final String exceptionClass;
        private final String messageSample;
        private final String module;
        private final String action;
        private long count;
        private OffsetDateTime latestOccurredAt;
        private String level;

        private ExceptionAccumulator(String exceptionClass, String messageSample, String module, String action, String level) {
            this.exceptionClass = exceptionClass;
            this.messageSample = messageSample;
            this.module = module;
            this.action = action;
            this.level = level;
        }

        private String exceptionClass() {
            return exceptionClass;
        }

        private String messageSample() {
            return messageSample;
        }

        private String module() {
            return module;
        }

        private String action() {
            return action;
        }

        private long count() {
            return count;
        }

        private OffsetDateTime latestOccurredAt() {
            return latestOccurredAt;
        }

        private String level() {
            return level;
        }
    }

    private static final class TopKScoreAccumulator {
        private final int topK;
        private long top1ScoreCount;
        private double top1ScoreSum;
        private long citationScoreCount;
        private double citationScoreSum;
        private long latencyCount;
        private double latencySum;

        private TopKScoreAccumulator(int topK) {
            this.topK = topK;
        }

        private int topK() {
            return topK;
        }
    }

    private static final class DocHitAccumulator {
        private final String docId;
        private String docName;
        private long hitCount;
        private double scoreSum;
        private long scoreCount;
        private OffsetDateTime lastHitAt;

        private DocHitAccumulator(String docId, String docName) {
            this.docId = docId;
            this.docName = docName;
        }

        private long hitCount() {
            return hitCount;
        }

        private OffsetDateTime lastHitAt() {
            return lastHitAt;
        }
    }

    private static final class FailedDocAccumulator {
        private final String docId;
        private String docName;
        private long failCount;
        private OffsetDateTime latestFailedAt;
        private String latestReason;

        private FailedDocAccumulator(String docId, String docName) {
            this.docId = docId;
            this.docName = docName;
        }

        private long failCount() {
            return failCount;
        }

        private OffsetDateTime latestFailedAt() {
            return latestFailedAt;
        }
    }
}
