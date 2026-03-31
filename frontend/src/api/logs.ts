import { http } from "./http";
import type { ApiResponse, PageResult } from "@/types";

export type OverviewGranularity = "day" | "hour";
export type OverviewScope = "self" | "system" | "all";
export type OverviewModule = "AUTH" | "CHAT" | "DOCS" | "SESSION" | "EVAL" | "LOGGING" | "SYSTEM";
export type SystemLogLevel = "DEBUG" | "INFO" | "WARN" | "ERROR";
export type SystemLogSource = "REQUEST" | "BUSINESS" | "EXCEPTION" | "ASYNC";
export type SystemLogScope = "PRIVATE" | "SYSTEM";

export interface SystemLogItem {
    id: number;
    traceId: string;
    userId: number | null;
    username: string | null;
    userRole: string | null;
    visibilityScope: SystemLogScope;
    module: string;
    source: SystemLogSource;
    action: string;
    level: SystemLogLevel;
    success: boolean;
    message: string;
    detailsJson: string | null;
    resourceType: string | null;
    resourceId: string | null;
    httpMethod: string | null;
    requestPath: string | null;
    statusCode: number | null;
    clientIp: string | null;
    durationMs: number | null;
    exceptionClass: string | null;
    createdAt: string | null;
    startedAt: string | null;
    finishedAt: string | null;
}

export interface SystemLogListQuery {
    page?: number;
    pageSize?: number;
    module?: string;
    level?: string;
    source?: string;
    traceId?: string;
    action?: string;
    keyword?: string;
    scope?: string;
    from?: string;
    to?: string;
    userId?: number;
}

export interface SystemLogsOverviewQuery {
    from?: string;
    to?: string;
    granularity?: OverviewGranularity;
    module?: OverviewModule;
    userId?: number;
    scope?: OverviewScope;
    topN?: number;
}

export interface OverviewQueryEcho {
    from?: string;
    to?: string;
    granularity?: OverviewGranularity;
    module?: OverviewModule | null;
    userId?: number | null;
    scope?: OverviewScope;
    topN?: number;
}

export interface OverviewSummary {
    requestCount: number;
    successCount: number;
    errorCount: number;
    errorRate: number;
    avgDurationMs: number;
    systemErrorCount: number;
    activeUserCount: number;
}

export interface ModuleRequestTrendItem {
    time: string;
    module: string;
    requestCount: number;
    errorCount: number;
    avgDurationMs: number;
}

export interface ExceptionsRecentTopItem {
    exceptionClass: string;
    messageSample: string;
    count: number;
    latestOccurredAt?: string | null;
    module: string;
    action: string;
    level: string;
}

export interface ChatAnalyticsOverview {
    chatCount: number;
    refusedCount: number;
    refusedRate: number;
    avgLatencyMs: number;
    avgRetrievalMs: number;
    avgRerankMs: number;
    avgGenMs: number;
    avgCitationCount: number;
}

export interface ChatModeDistributionItem {
    mode: string;
    count: number;
    ratio: number;
}

export interface TopKDistributionItem {
    topK: number;
    count: number;
    ratio: number;
}

export interface Top1ScoreByTopKItem {
    topK: number;
    sampleCount: number;
    avgTop1Score: number;
    avgCitationScore: number;
    avgLatencyMs: number;
}

export interface TopHitDocItem {
    docId?: string | null;
    docName?: string | null;
    hitCount: number;
    avgScore: number;
    lastHitAt?: string | null;
}

export interface UploadTrendItem {
    time: string;
    count: number;
}

export interface DocsUploadOverview {
    uploadCount: number;
    uploadTrend: UploadTrendItem[];
}

export interface DocsProcessOverview {
    processRequestCount: number;
    processSuccessCount: number;
    processFailedCount: number;
    processSuccessRate: number;
    avgChunkCount: number;
}

export interface FailedDocTopItem {
    docId: string;
    docName?: string | null;
    failCount: number;
    latestFailedAt?: string | null;
    latestReason?: string | null;
}

export interface SystemLogsOverviewData {
    query: OverviewQueryEcho;
    summary: OverviewSummary;
    trends: {
        granularity: OverviewGranularity;
        moduleRequestTrend: ModuleRequestTrendItem[];
    };
    chatAnalytics: {
        overview: ChatAnalyticsOverview;
        modeDistribution: ChatModeDistributionItem[];
        topKDistribution: TopKDistributionItem[];
        top1ScoreByTopK: Top1ScoreByTopKItem[];
        topHitDocs: TopHitDocItem[];
    };
    docsAnalytics: {
        uploadOverview: DocsUploadOverview;
        processOverview: DocsProcessOverview;
        failedDocsTop: FailedDocTopItem[];
    };
    exceptions: {
        recentTop: ExceptionsRecentTopItem[];
    };
    meta: {
        generatedAt?: string;
        scopeApplied?: string;
    };
}

export function parseDetailsJson(detailsJson?: string | null) {
    if (!detailsJson) return null;
    try {
        return JSON.parse(detailsJson);
    } catch {
        return detailsJson;
    }
}

export async function fetchSystemLogs(params: SystemLogListQuery) {
    const { data } = await http.get<ApiResponse<PageResult<SystemLogItem>>>("/api/v1/system-logs", { params });
    return data;
}

export async function getSystemLogsOverview(params: SystemLogsOverviewQuery) {
    const { data } = await http.get<ApiResponse<SystemLogsOverviewData>>("/api/v1/system-logs/overview", { params });
    return data;
}
