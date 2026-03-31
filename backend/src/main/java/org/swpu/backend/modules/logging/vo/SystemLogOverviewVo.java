package org.swpu.backend.modules.logging.vo;

import java.util.List;

public record SystemLogOverviewVo(
        QueryInfo query,
        Summary summary,
        Trends trends,
        ChatAnalytics chatAnalytics,
        DocsAnalytics docsAnalytics,
        Exceptions exceptions,
        Meta meta
) {
    public record QueryInfo(
            String from,
            String to,
            String granularity,
            String module,
            Long userId,
            String scope,
            Integer topN
    ) {
    }

    public record Summary(
            long requestCount,
            long successCount,
            long errorCount,
            double errorRate,
            double avgDurationMs,
            long systemErrorCount,
            long activeUserCount
    ) {
    }

    public record Trends(
            String granularity,
            List<ModuleTrendPoint> moduleRequestTrend
    ) {
    }

    public record ModuleTrendPoint(
            String time,
            String module,
            long requestCount,
            long errorCount,
            double avgDurationMs
    ) {
    }

    public record Exceptions(
            List<ExceptionTopItem> recentTop
    ) {
    }

    public record ExceptionTopItem(
            String exceptionClass,
            String messageSample,
            long count,
            String latestOccurredAt,
            String module,
            String action,
            String level
    ) {
    }

    public record ChatAnalytics(
            ChatOverview overview,
            List<ModeDistributionItem> modeDistribution,
            List<TopKDistributionItem> topKDistribution,
            List<Top1ScoreByTopKItem> top1ScoreByTopK,
            List<TopHitDocItem> topHitDocs
    ) {
    }

    public record ChatOverview(
            long chatCount,
            long refusedCount,
            double refusedRate,
            double avgLatencyMs,
            double avgRetrievalMs,
            double avgRerankMs,
            double avgGenMs,
            double avgCitationCount
    ) {
    }

    public record ModeDistributionItem(
            String mode,
            long count,
            double ratio
    ) {
    }

    public record TopKDistributionItem(
            int topK,
            long count,
            double ratio
    ) {
    }

    public record Top1ScoreByTopKItem(
            int topK,
            long sampleCount,
            double avgTop1Score,
            double avgCitationScore,
            double avgLatencyMs
    ) {
    }

    public record TopHitDocItem(
            String docId,
            String docName,
            long hitCount,
            double avgScore,
            String lastHitAt
    ) {
    }

    public record DocsAnalytics(
            UploadOverview uploadOverview,
            ProcessOverview processOverview,
            List<FailedDocItem> failedDocsTop
    ) {
    }

    public record UploadOverview(
            long uploadCount,
            List<TimeCountPoint> uploadTrend
    ) {
    }

    public record TimeCountPoint(
            String time,
            long count
    ) {
    }

    public record ProcessOverview(
            long processRequestCount,
            long processSuccessCount,
            long processFailedCount,
            double processSuccessRate,
            double avgChunkCount
    ) {
    }

    public record FailedDocItem(
            String docId,
            String docName,
            long failCount,
            String latestFailedAt,
            String latestReason
    ) {
    }

    public record Meta(
            String generatedAt,
            String scopeApplied
    ) {
    }
}
