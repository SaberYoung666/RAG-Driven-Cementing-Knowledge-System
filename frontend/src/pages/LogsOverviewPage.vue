<template>
  <div class="page overview-page">
    <a-card title="日志总览" size="small">
      <div class="overview-shell">
        <a-form layout="vertical" class="filter-form">
          <a-row :gutter="12">
            <a-col :xs="24" :sm="24" :md="12" :lg="10">
              <a-form-item label="时间范围">
                <a-range-picker
                  v-model:value="rangeValue"
                  class="full-width"
                  show-time
                  :allow-clear="false"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :lg="4">
              <a-form-item label="粒度">
                <a-select v-model:value="granularity" :options="granularityOptions" />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :lg="4">
              <a-form-item label="范围">
                <a-select v-model:value="scope" :options="scopeOptions" />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :lg="3">
              <a-form-item label="Top N">
                <a-input-number v-model:value="topN" class="full-width" :min="1" :max="100" />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :lg="3">
              <a-form-item label="用户 ID">
                <a-input-number v-model:value="userId" class="full-width" :min="1" placeholder="可选" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="12">
            <a-col :xs="24" :sm="12" :md="8" :lg="6">
              <a-form-item label="模块">
                <a-select v-model:value="module" allow-clear placeholder="全部模块" :options="moduleOptions" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="16" :lg="18">
              <a-form-item label="操作">
                <a-space wrap>
                  <a-button type="primary" :loading="loading" @click="loadOverview">查询</a-button>
                  <a-button @click="resetFilters">重置</a-button>
                  <span class="query-meta">
                    生成时间：{{ formatDateTime(overview?.meta?.generatedAt) }}
                  </span>
                  <span class="query-meta">
                    生效范围：{{ overview?.meta?.scopeApplied || "-" }}
                  </span>
                </a-space>
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>

        <a-spin :spinning="loading">
          <div class="summary-grid">
            <div v-for="card in summaryCards" :key="card.label" class="metric-card">
              <div class="metric-label">{{ card.label }}</div>
              <div class="metric-value">{{ card.value }}</div>
              <div v-if="card.hint" class="metric-hint">{{ card.hint }}</div>
            </div>
          </div>

          <a-row :gutter="[12, 12]">
            <a-col :xs="24" :xl="16">
              <a-card size="small" title="请求趋势" :bordered="false" class="inner-card">
                <div v-if="trendDisplay.length" class="trend-list">
                  <div v-for="item in trendDisplay" :key="`${item.time}-${item.module}`" class="trend-item">
                    <div class="trend-topline">
                      <div class="trend-title">
                        <span class="trend-time">{{ item.time }}</span>
                        <span class="trend-module">{{ item.module }}</span>
                      </div>
                      <div class="trend-metrics">
                        <span>{{ formatInteger(item.requestCount) }} 次请求</span>
                        <span>{{ formatInteger(item.errorCount) }} 错误</span>
                        <span>{{ formatMs(item.avgDurationMs) }}</span>
                      </div>
                    </div>
                    <div class="trend-track">
                      <div class="trend-fill" :style="{ width: `${item.requestWidth}%` }" />
                    </div>
                    <div class="trend-foot">
                      <span>错误率 {{ formatPercent(item.errorRate) }}</span>
                      <span>强度 {{ item.requestWidth.toFixed(0) }}%</span>
                    </div>
                  </div>
                </div>
                <a-empty v-else description="暂无趋势数据" />
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="8">
              <a-card size="small" title="查询条件" :bordered="false" class="inner-card">
                <a-descriptions bordered :column="1" size="small">
                  <a-descriptions-item label="时间范围">
                    {{ formatDateTime(overview?.query?.from) }} 至 {{ formatDateTime(overview?.query?.to) }}
                  </a-descriptions-item>
                  <a-descriptions-item label="粒度">{{ overview?.query?.granularity || "-" }}</a-descriptions-item>
                  <a-descriptions-item label="模块">{{ overview?.query?.module || "全部" }}</a-descriptions-item>
                  <a-descriptions-item label="范围">{{ overview?.query?.scope || "-" }}</a-descriptions-item>
                  <a-descriptions-item label="用户 ID">{{ overview?.query?.userId ?? "-" }}</a-descriptions-item>
                  <a-descriptions-item label="Top N">{{ overview?.query?.topN ?? "-" }}</a-descriptions-item>
                </a-descriptions>
              </a-card>
            </a-col>
          </a-row>

          <a-row :gutter="[12, 12]">
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="对话分析概览" :bordered="false" class="inner-card">
                <div class="mini-grid">
                  <div v-for="item in chatOverviewCards" :key="item.label" class="mini-card">
                    <div class="mini-label">{{ item.label }}</div>
                    <div class="mini-value">{{ item.value }}</div>
                  </div>
                </div>
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="文档处理概览" :bordered="false" class="inner-card">
                <div class="mini-grid">
                  <div v-for="item in docsProcessCards" :key="item.label" class="mini-card">
                    <div class="mini-label">{{ item.label }}</div>
                    <div class="mini-value">{{ item.value }}</div>
                  </div>
                </div>
              </a-card>
            </a-col>
          </a-row>

          <a-row :gutter="[12, 12]">
            <a-col :xs="24" :xl="8">
              <a-card size="small" title="模式分布" :bordered="false" class="inner-card">
                <div v-if="modeDistributionDisplay.length" class="bar-list">
                  <div v-for="item in modeDistributionDisplay" :key="item.mode" class="bar-item">
                    <div class="bar-header">
                      <span class="bar-name">{{ item.mode }}</span>
                      <span class="bar-value">{{ formatInteger(item.count) }} / {{ formatPercent(item.ratio) }}</span>
                    </div>
                    <div class="bar-track">
                      <div class="bar-fill" :style="{ width: `${item.width}%` }" />
                    </div>
                  </div>
                </div>
                <a-empty v-else description="暂无模式分布数据" />
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="8">
              <a-card size="small" title="TopK 分布" :bordered="false" class="inner-card">
                <div v-if="topKDistributionDisplay.length" class="bar-list">
                  <div v-for="item in topKDistributionDisplay" :key="item.topK" class="bar-item">
                    <div class="bar-header">
                      <span class="bar-name">TopK = {{ item.topK }}</span>
                      <span class="bar-value">{{ formatInteger(item.count) }} / {{ formatPercent(item.ratio) }}</span>
                    </div>
                    <div class="bar-track">
                      <div class="bar-fill warm" :style="{ width: `${item.width}%` }" />
                    </div>
                  </div>
                </div>
                <a-empty v-else description="暂无 TopK 分布数据" />
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="8">
              <a-card size="small" title="文档上传趋势" :bordered="false" class="inner-card">
                <div v-if="uploadTrendDisplay.length" class="column-chart">
                  <div v-for="item in uploadTrendDisplay" :key="item.time" class="column-item">
                    <div class="column-value">{{ item.count }}</div>
                    <div class="column-track">
                      <div class="column-fill" :style="{ height: `${item.height}%` }" />
                    </div>
                    <div class="column-label">{{ item.label }}</div>
                  </div>
                </div>
                <a-empty v-else description="暂无上传趋势数据" />
              </a-card>
            </a-col>
          </a-row>

          <a-row :gutter="[12, 12]">
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="Top1 得分与延迟" :bordered="false" class="inner-card">
                <div v-if="top1ScoreDisplay.length" class="score-stack">
                  <div v-for="item in top1ScoreDisplay" :key="item.topK" class="score-card">
                    <div class="score-head">
                      <div>
                        <div class="score-title">TopK {{ item.topK }}</div>
                        <div class="score-sub">样本 {{ formatInteger(item.sampleCount) }}</div>
                      </div>
                      <div class="score-latency">{{ formatMs(item.avgLatencyMs) }}</div>
                    </div>
                    <div class="dual-bars">
                      <div class="dual-bar-item">
                        <div class="dual-bar-meta">
                          <span>Top1 分数</span>
                          <span>{{ formatDecimal(item.avgTop1Score, 4) }}</span>
                        </div>
                        <div class="bar-track compact">
                          <div class="bar-fill" :style="{ width: `${item.top1Width}%` }" />
                        </div>
                      </div>
                      <div class="dual-bar-item">
                        <div class="dual-bar-meta">
                          <span>引用分数</span>
                          <span>{{ formatDecimal(item.avgCitationScore, 4) }}</span>
                        </div>
                        <div class="bar-track compact">
                          <div class="bar-fill warm" :style="{ width: `${item.citationWidth}%` }" />
                        </div>
                      </div>
                      <div class="dual-bar-item">
                        <div class="dual-bar-meta">
                          <span>平均耗时</span>
                          <span>{{ formatMs(item.avgLatencyMs) }}</span>
                        </div>
                        <div class="bar-track compact">
                          <div class="bar-fill neutral" :style="{ width: `${item.latencyWidth}%` }" />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <a-empty v-else description="暂无 Top1 评分数据" />
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="命中文档 Top" :bordered="false" class="inner-card">
                <div v-if="topHitDocsDisplay.length" class="leaderboard">
                  <div v-for="(item, index) in topHitDocsDisplay" :key="item.key" class="leader-row">
                    <div class="leader-rank">{{ index + 1 }}</div>
                    <div class="leader-main">
                      <div class="leader-top">
                        <span class="leader-name">{{ item.name }}</span>
                        <span class="leader-stat">{{ formatInteger(item.hitCount) }} 次命中</span>
                      </div>
                      <div class="bar-track compact">
                        <div class="bar-fill" :style="{ width: `${item.hitWidth}%` }" />
                      </div>
                      <div class="leader-bottom">
                        <span>平均分数 {{ formatDecimal(item.avgScore, 4) }}</span>
                        <span>{{ formatDateTime(item.lastHitAt) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                <a-empty v-else description="暂无命中文档数据" />
              </a-card>
            </a-col>
          </a-row>

          <a-row :gutter="[12, 12]">
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="异常排行" :bordered="false" class="inner-card">
                <a-table
                  size="small"
                  :columns="exceptionColumns"
                  :data-source="overview?.exceptions?.recentTop ?? []"
                  :pagination="false"
                  :row-key="(record: any) => `${record.exceptionClass}-${record.module}-${record.action}`"
                  :scroll="{ x: 760 }"
                />
              </a-card>
            </a-col>
            <a-col :xs="24" :xl="12">
              <a-card size="small" title="失败文档 Top" :bordered="false" class="inner-card">
                <a-table
                  size="small"
                  :columns="failedDocsColumns"
                  :data-source="overview?.docsAnalytics?.failedDocsTop ?? []"
                  :pagination="false"
                  row-key="docId"
                  :scroll="{ x: 760 }"
                />
              </a-card>
            </a-col>
          </a-row>
        </a-spin>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import dayjs, { type Dayjs } from "dayjs";
import { message } from "ant-design-vue";
import {
  getSystemLogsOverview,
  type OverviewGranularity,
  type OverviewModule,
  type OverviewScope,
  type SystemLogsOverviewData,
} from "@/api/logs";

const defaultRange = () => [dayjs().subtract(7, "day"), dayjs()] as [Dayjs, Dayjs];

const loading = ref(false);
const overview = ref<SystemLogsOverviewData | null>(null);
const rangeValue = ref<[Dayjs, Dayjs]>(defaultRange());
const granularity = ref<OverviewGranularity>("day");
const module = ref<OverviewModule | undefined>(undefined);
const scope = ref<OverviewScope>("all");
const topN = ref(10);
const userId = ref<number | null>(null);

const granularityOptions = [
  { value: "day", label: "按天" },
  { value: "hour", label: "按小时" },
];

const scopeOptions = [
  { value: "all", label: "全部" },
  { value: "self", label: "本人" },
  { value: "system", label: "系统" },
];

const moduleOptions = [
  { value: "AUTH", label: "AUTH" },
  { value: "CHAT", label: "CHAT" },
  { value: "DOCS", label: "DOCS" },
  { value: "SESSION", label: "SESSION" },
  { value: "EVAL", label: "EVAL" },
  { value: "LOGGING", label: "LOGGING" },
  { value: "SYSTEM", label: "SYSTEM" },
];

const summaryCards = computed(() => {
  const summary = overview.value?.summary;
  return [
    { label: "总请求数", value: formatInteger(summary?.requestCount), hint: `成功 ${formatInteger(summary?.successCount)}` },
    { label: "失败请求数", value: formatInteger(summary?.errorCount), hint: `错误率 ${formatPercent(summary?.errorRate)}` },
    { label: "平均耗时", value: formatMs(summary?.avgDurationMs), hint: "全请求平均" },
    { label: "系统错误数", value: formatInteger(summary?.systemErrorCount), hint: `活跃用户 ${formatInteger(summary?.activeUserCount)}` },
  ];
});

const chatOverviewCards = computed(() => {
  const item = overview.value?.chatAnalytics?.overview;
  return [
    { label: "对话总数", value: formatInteger(item?.chatCount) },
    { label: "拒答数", value: formatInteger(item?.refusedCount) },
    { label: "拒答率", value: formatPercent(item?.refusedRate) },
    { label: "平均总耗时", value: formatMs(item?.avgLatencyMs) },
    { label: "平均检索耗时", value: formatMs(item?.avgRetrievalMs) },
    { label: "平均重排耗时", value: formatMs(item?.avgRerankMs) },
    { label: "平均生成耗时", value: formatMs(item?.avgGenMs) },
    { label: "平均引用数", value: formatDecimal(item?.avgCitationCount, 1) },
  ];
});

const docsProcessCards = computed(() => {
  const upload = overview.value?.docsAnalytics?.uploadOverview;
  const process = overview.value?.docsAnalytics?.processOverview;
  return [
    { label: "上传总数", value: formatInteger(upload?.uploadCount) },
    { label: "处理请求数", value: formatInteger(process?.processRequestCount) },
    { label: "处理成功数", value: formatInteger(process?.processSuccessCount) },
    { label: "处理失败数", value: formatInteger(process?.processFailedCount) },
    { label: "处理成功率", value: formatPercent(process?.processSuccessRate) },
    { label: "平均分块数", value: formatDecimal(process?.avgChunkCount, 1) },
  ];
});

const trendDisplay = computed(() => {
  const items = overview.value?.trends?.moduleRequestTrend ?? [];
  const maxRequest = Math.max(...items.map((item) => item.requestCount || 0), 1);
  return items.map((item) => ({
    ...item,
    requestWidth: (item.requestCount / maxRequest) * 100,
    errorRate: item.requestCount > 0 ? item.errorCount / item.requestCount : 0,
  }));
});

const modeDistributionDisplay = computed(() => {
  const items = overview.value?.chatAnalytics?.modeDistribution ?? [];
  const maxCount = Math.max(...items.map((item) => item.count || 0), 1);
  return items.map((item) => ({
    ...item,
    width: (item.count / maxCount) * 100,
  }));
});

const topKDistributionDisplay = computed(() => {
  const items = overview.value?.chatAnalytics?.topKDistribution ?? [];
  const maxCount = Math.max(...items.map((item) => item.count || 0), 1);
  return items.map((item) => ({
    ...item,
    width: (item.count / maxCount) * 100,
  }));
});

const uploadTrendDisplay = computed(() => {
  const items = overview.value?.docsAnalytics?.uploadOverview?.uploadTrend ?? [];
  const maxCount = Math.max(...items.map((item) => item.count || 0), 1);
  return items.map((item) => ({
    ...item,
    height: (item.count / maxCount) * 100,
    label: item.time.length > 10 ? item.time.slice(5, 13) : item.time.slice(5),
  }));
});

const top1ScoreDisplay = computed(() => {
  const items = overview.value?.chatAnalytics?.top1ScoreByTopK ?? [];
  const maxLatency = Math.max(...items.map((item) => item.avgLatencyMs || 0), 1);
  return items.map((item) => ({
    ...item,
    top1Width: Math.max(0, Math.min(100, (item.avgTop1Score || 0) * 100)),
    citationWidth: Math.max(0, Math.min(100, (item.avgCitationScore || 0) * 100)),
    latencyWidth: (item.avgLatencyMs / maxLatency) * 100,
  }));
});

const topHitDocsDisplay = computed(() => {
  const items = overview.value?.chatAnalytics?.topHitDocs ?? [];
  const maxHit = Math.max(...items.map((item) => item.hitCount || 0), 1);
  return items.map((item, index) => ({
    ...item,
    key: `${item.docId || item.docName || "doc"}-${index}`,
    name: item.docName || item.docId || "未命名文档",
    hitWidth: (item.hitCount / maxHit) * 100,
  }));
});

const exceptionColumns = [
  { title: "异常类", dataIndex: "exceptionClass", key: "exceptionClass", width: 180 },
  { title: "模块", dataIndex: "module", key: "module", width: 90 },
  { title: "动作", dataIndex: "action", key: "action", width: 120 },
  { title: "级别", dataIndex: "level", key: "level", width: 90 },
  { title: "次数", dataIndex: "count", key: "count", width: 80 },
  {
    title: "最近时间",
    key: "latestOccurredAt",
    width: 170,
    customRender: ({ record }: any) => formatDateTime(record.latestOccurredAt),
  },
  { title: "示例消息", dataIndex: "messageSample", key: "messageSample" },
];

const failedDocsColumns = [
  {
    title: "文档",
    key: "docName",
    customRender: ({ record }: any) => record.docName || record.docId || "-",
  },
  { title: "失败次数", dataIndex: "failCount", key: "failCount", width: 100 },
  {
    title: "最近失败时间",
    key: "latestFailedAt",
    width: 170,
    customRender: ({ record }: any) => formatDateTime(record.latestFailedAt),
  },
  { title: "最近原因", dataIndex: "latestReason", key: "latestReason" },
];

function formatInteger(value?: number | null) {
  return Number.isFinite(Number(value)) ? String(Math.round(Number(value))) : "0";
}

function formatDecimal(value?: number | null, digits = 1) {
  return Number.isFinite(Number(value)) ? Number(value).toFixed(digits) : "0";
}

function formatMs(value?: number | null) {
  return `${formatDecimal(value, 1)} ms`;
}

function formatPercent(value?: number | null) {
  return `${(Number(value ?? 0) * 100).toFixed(2)}%`;
}

function formatDateTime(value?: string | null) {
  if (!value || !dayjs(value).isValid()) return "-";
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

async function loadOverview() {
  if (!rangeValue.value?.length) {
    message.warning("请先选择时间范围");
    return;
  }
  loading.value = true;
  try {
    const [from, to] = rangeValue.value;
    const res = await getSystemLogsOverview({
      from: from.toISOString(),
      to: to.toISOString(),
      granularity: granularity.value,
      module: module.value,
      userId: userId.value || undefined,
      scope: scope.value,
      topN: topN.value,
    });
    if (res.code !== 0) {
      message.error(res.message || "加载日志总览失败");
      return;
    }
    overview.value = res.data;
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  rangeValue.value = defaultRange();
  granularity.value = "day";
  module.value = undefined;
  scope.value = "all";
  topN.value = 10;
  userId.value = null;
  loadOverview();
}

onMounted(() => {
  loadOverview();
});
</script>

<style scoped>
.overview-page {
  height: 100%;
  overflow: auto;
}

.overview-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-form {
  padding: 4px 0 2px;
}

.full-width {
  width: 100%;
}

.query-meta {
  color: var(--muted);
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card,
.mini-card {
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--bg-elevated);
  padding: 16px;
}

.metric-label,
.mini-label {
  font-size: 13px;
  color: var(--muted);
}

.metric-value {
  margin-top: 12px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text);
}

.metric-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--muted);
}

.inner-card {
  height: 100%;
}

.trend-list,
.bar-list,
.score-stack,
.leaderboard {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trend-item,
.score-card,
.leader-row {
  border: 1px solid var(--border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
  padding: 14px;
}

.trend-topline,
.trend-foot,
.bar-header,
.leader-top,
.leader-bottom,
.score-head,
.dual-bar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.trend-title,
.trend-metrics {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.trend-time,
.trend-module,
.leader-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
}

.trend-time {
  border: 1px solid var(--border);
  color: var(--muted);
}

.trend-module,
.leader-rank {
  background: rgba(var(--accent-rgb), 0.14);
  color: var(--text);
}

.trend-metrics,
.trend-foot,
.bar-value,
.leader-bottom,
.score-sub,
.score-latency,
.dual-bar-meta {
  font-size: 12px;
  color: var(--muted);
}

.trend-track,
.bar-track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.bar-track.compact {
  height: 8px;
}

.trend-fill,
.bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(var(--accent-rgb), 0.45), rgba(var(--accent-rgb), 0.95));
}

.bar-fill.warm {
  background: linear-gradient(90deg, #f59e0b, #f97316);
}

.bar-fill.neutral {
  background: linear-gradient(90deg, #64748b, #94a3b8);
}

.bar-name,
.leader-name,
.score-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.column-chart {
  min-height: 220px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(48px, 1fr));
  gap: 10px;
  align-items: end;
}

.column-item {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.column-value,
.column-label {
  font-size: 12px;
  color: var(--muted);
}

.column-track {
  width: 100%;
  height: 150px;
  padding: 6px;
  border-radius: 14px;
  background: var(--bg-soft);
  display: flex;
  align-items: end;
}

.column-fill {
  width: 100%;
  min-height: 10px;
  border-radius: 10px;
  background: linear-gradient(180deg, rgba(var(--accent-rgb), 0.38), rgba(var(--accent-rgb), 0.95));
}

.dual-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dual-bar-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.leader-row {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 12px;
  align-items: start;
}

.leader-rank {
  min-height: 28px;
  font-weight: 700;
}

.leader-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mini-card {
  padding: 14px;
}

.mini-value {
  margin-top: 10px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}

:deep(.inner-card .ant-card-body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .summary-grid,
  .mini-grid {
    grid-template-columns: 1fr;
  }

  .trend-topline,
  .trend-foot,
  .bar-header,
  .leader-top,
  .leader-bottom,
  .score-head,
  .dual-bar-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .column-chart {
    grid-template-columns: repeat(auto-fit, minmax(42px, 1fr));
  }
}
</style>
