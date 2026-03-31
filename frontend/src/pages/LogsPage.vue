<template>
  <div class="page logs-page">
    <a-card title="日志列表" size="small">
      <div class="logs-shell">
        <a-form layout="vertical" class="filter-form">
          <a-row :gutter="12">
            <a-col :xs="24" :sm="24" :md="12" :xl="8">
              <a-form-item label="时间范围">
                <a-range-picker
                  v-model:value="timeRange"
                  class="full-width"
                  show-time
                  allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="4">
              <a-form-item label="模块">
                <a-select
                  v-model:value="module"
                  allow-clear
                  placeholder="全部模块"
                  :options="moduleOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="4">
              <a-form-item label="级别">
                <a-select
                  v-model:value="level"
                  allow-clear
                  placeholder="全部级别"
                  :options="levelOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="4">
              <a-form-item label="来源">
                <a-select
                  v-model:value="source"
                  allow-clear
                  placeholder="全部来源"
                  :options="sourceOptions"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="4">
              <a-form-item label="范围">
                <a-select
                  v-model:value="scope"
                  allow-clear
                  placeholder="全部范围"
                  :options="scopeOptions"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="12">
            <a-col :xs="24" :sm="12" :md="8" :xl="5">
              <a-form-item label="Trace ID">
                <a-input v-model:value="traceId" allow-clear placeholder="精确匹配 traceId" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :xl="4">
              <a-form-item label="动作">
                <a-input v-model:value="action" allow-clear placeholder="按动作模糊匹配" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :sm="12" :md="8" :xl="5">
              <a-form-item label="关键词">
                <a-input v-model:value="keyword" allow-clear placeholder="搜索摘要、详情、路径、用户名等" />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="3">
              <a-form-item label="用户 ID">
                <a-input-number
                  v-model:value="userId"
                  class="full-width"
                  :min="1"
                  placeholder="仅管理员生效"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="12" :sm="12" :md="6" :xl="7">
              <a-form-item label="操作">
                <a-space wrap>
                  <a-button type="primary" :loading="loading" @click="handleSearch">查询</a-button>
                  <a-button @click="resetFilters">重置</a-button>
                  <span class="query-tip">支持点击表格中的 traceId 直接反查链路</span>
                </a-space>
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>

        <a-table
          :columns="columns"
          :data-source="items"
          :loading="loading"
          :pagination="pagination"
          rowKey="id"
          :scroll="{ x: 1560 }"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'createdAt'">
              <div class="cell-stack">
                <span>{{ formatDateTime(record.createdAt) }}</span>
                <span class="cell-sub">{{ formatDateTime(record.startedAt) }}</span>
              </div>
            </template>

            <template v-else-if="column.key === 'level'">
              <div class="cell-stack">
                <a-tag :color="getLevelColor(record.level)">{{ record.level }}</a-tag>
                <a-tag :color="record.success ? 'success' : 'error'">
                  {{ record.success ? "SUCCESS" : "FAILED" }}
                </a-tag>
              </div>
            </template>

            <template v-else-if="column.key === 'moduleSource'">
              <div class="cell-stack">
                <a-tag color="blue">{{ record.module || "-" }}</a-tag>
                <a-tag>{{ record.source || "-" }}</a-tag>
              </div>
            </template>

            <template v-else-if="column.key === 'user'">
              <div class="cell-stack">
                <span>{{ formatUser(record) }}</span>
                <span class="cell-sub">{{ record.userRole || "-" }}</span>
              </div>
            </template>

            <template v-else-if="column.key === 'summary'">
              <div class="summary-cell">
                <div class="summary-head">
                  <span class="summary-action">{{ record.action || "-" }}</span>
                  <a-tag color="gold">{{ record.visibilityScope }}</a-tag>
                </div>
                <div class="summary-message">{{ record.message || "-" }}</div>
                <div class="cell-sub">
                  {{ record.resourceType || "-" }} / {{ record.resourceId || "-" }}
                </div>
              </div>
            </template>

            <template v-else-if="column.key === 'request'">
              <div class="cell-stack">
                <span>
                  {{ record.httpMethod || "-" }}
                  {{ record.requestPath || "" }}
                </span>
                <span class="cell-sub">
                  状态 {{ record.statusCode ?? "-" }} / IP {{ record.clientIp || "-" }}
                </span>
              </div>
            </template>

            <template v-else-if="column.key === 'traceId'">
              <a-button type="link" class="trace-link" @click="applyTraceFilter(record.traceId)">
                {{ record.traceId }}
              </a-button>
            </template>

            <template v-else-if="column.key === 'durationMs'">
              <span>{{ formatDuration(record.durationMs) }}</span>
            </template>

            <template v-else-if="column.key === 'operation'">
              <a-button type="link" @click="openDetail(record)">详情</a-button>
            </template>
          </template>
        </a-table>
      </div>
    </a-card>

    <a-drawer v-model:open="drawerOpen" :title="drawerTitle" width="860">
      <div v-if="current" class="detail-shell">
        <a-alert
          v-if="current.exceptionClass"
          type="error"
          show-icon
          :message="current.exceptionClass"
          :description="current.message || '日志包含异常信息'"
        />

        <a-descriptions bordered size="small" :column="2">
          <a-descriptions-item label="日志 ID">{{ current.id }}</a-descriptions-item>
          <a-descriptions-item label="Trace ID">{{ current.traceId }}</a-descriptions-item>
          <a-descriptions-item label="用户">{{ formatUser(current) }}</a-descriptions-item>
          <a-descriptions-item label="用户角色">{{ current.userRole || "-" }}</a-descriptions-item>
          <a-descriptions-item label="模块">{{ current.module || "-" }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ current.source || "-" }}</a-descriptions-item>
          <a-descriptions-item label="动作">{{ current.action || "-" }}</a-descriptions-item>
          <a-descriptions-item label="级别">{{ current.level || "-" }}</a-descriptions-item>
          <a-descriptions-item label="业务结果">
            {{ current.success ? "成功" : "失败" }}
          </a-descriptions-item>
          <a-descriptions-item label="可见范围">
            {{ current.visibilityScope || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="HTTP 请求">
            {{ current.httpMethod || "-" }} {{ current.requestPath || "" }}
          </a-descriptions-item>
          <a-descriptions-item label="HTTP 状态">
            {{ current.statusCode ?? "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="资源">
            {{ current.resourceType || "-" }} / {{ current.resourceId || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="客户端 IP">
            {{ current.clientIp || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="耗时">
            {{ formatDuration(current.durationMs) }}
          </a-descriptions-item>
          <a-descriptions-item label="异常类">
            {{ current.exceptionClass || "-" }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatDateTime(current.createdAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="开始时间">
            {{ formatDateTime(current.startedAt) }}
          </a-descriptions-item>
          <a-descriptions-item label="结束时间">
            {{ formatDateTime(current.finishedAt) }}
          </a-descriptions-item>
        </a-descriptions>

        <a-card size="small" title="日志摘要">
          <div class="message-block">{{ current.message || "-" }}</div>
        </a-card>

        <a-card size="small" title="详情内容">
          <JsonView :value="parsedCurrentDetails ?? current.detailsJson ?? null" />
        </a-card>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import dayjs, { type Dayjs } from "dayjs";
import { message } from "ant-design-vue";
import type { TablePaginationConfig } from "ant-design-vue";
import JsonView from "@/components/JsonView.vue";
import {
  fetchSystemLogs,
  parseDetailsJson,
  type SystemLogItem,
  type SystemLogListQuery,
  type SystemLogLevel,
} from "@/api/logs";

const defaultRange = () => [dayjs().subtract(7, "day"), dayjs()] as [Dayjs, Dayjs];

const loading = ref(false);
const items = ref<SystemLogItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);

const timeRange = ref<[Dayjs, Dayjs] | null>(defaultRange());
const module = ref<string | undefined>(undefined);
const level = ref<SystemLogLevel | undefined>(undefined);
const source = ref<string | undefined>(undefined);
const scope = ref<string | undefined>(undefined);
const traceId = ref("");
const action = ref("");
const keyword = ref("");
const userId = ref<number | null>(null);

const drawerOpen = ref(false);
const current = ref<SystemLogItem | null>(null);

const moduleOptions = [
  { value: "AUTH", label: "AUTH" },
  { value: "CHAT", label: "CHAT" },
  { value: "DOCS", label: "DOCS" },
  { value: "SESSION", label: "SESSION" },
  { value: "EVAL", label: "EVAL" },
  { value: "LOGGING", label: "LOGGING" },
  { value: "SYSTEM", label: "SYSTEM" },
];

const levelOptions = [
  { value: "DEBUG", label: "DEBUG" },
  { value: "INFO", label: "INFO" },
  { value: "WARN", label: "WARN" },
  { value: "ERROR", label: "ERROR" },
];

const sourceOptions = [
  { value: "REQUEST", label: "REQUEST" },
  { value: "BUSINESS", label: "BUSINESS" },
  { value: "EXCEPTION", label: "EXCEPTION" },
  { value: "ASYNC", label: "ASYNC" },
];

const scopeOptions = [
  { value: "PRIVATE", label: "PRIVATE" },
  { value: "SYSTEM", label: "SYSTEM" },
];

const levelColorMap: Record<SystemLogLevel, string> = {
  DEBUG: "default",
  INFO: "processing",
  WARN: "warning",
  ERROR: "error",
};

const columns = [
  { title: "创建时间", key: "createdAt", dataIndex: "createdAt", width: 180 },
  { title: "级别/结果", key: "level", width: 110 },
  { title: "模块/来源", key: "moduleSource", width: 140 },
  { title: "用户", key: "user", width: 170 },
  { title: "动作/摘要", key: "summary" },
  { title: "请求信息", key: "request", width: 260 },
  { title: "Trace ID", key: "traceId", width: 260 },
  { title: "耗时", key: "durationMs", dataIndex: "durationMs", width: 100 },
  { title: "操作", key: "operation", width: 90, fixed: "right" as const },
];

const pagination = computed<TablePaginationConfig>(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (count) => `共 ${count} 条`,
}));

const drawerTitle = computed(() => {
  if (!current.value) return "日志详情";
  return `日志详情 #${current.value.id}`;
});

const parsedCurrentDetails = computed(() => parseDetailsJson(current.value?.detailsJson));

function normalizeText(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

function formatDateTime(value?: string | null) {
  if (!value || !dayjs(value).isValid()) return "-";
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

function formatDuration(value?: number | null) {
  return Number.isFinite(Number(value)) ? `${Number(value).toFixed(1)} ms` : "-";
}

function getLevelColor(value?: string | null) {
  if (!value) return "default";
  return levelColorMap[value as SystemLogLevel] || "default";
}

function formatUser(record: Pick<SystemLogItem, "username" | "userId">) {
  if (record.username && record.userId != null) {
    return `${record.username} (#${record.userId})`;
  }
  if (record.username) return record.username;
  if (record.userId != null) return `#${record.userId}`;
  return "系统";
}

function buildQuery(): SystemLogListQuery {
  const params: SystemLogListQuery = {
    page: page.value,
    pageSize: pageSize.value,
    module: module.value,
    level: level.value,
    source: source.value,
    traceId: normalizeText(traceId.value),
    action: normalizeText(action.value),
    keyword: normalizeText(keyword.value),
    scope: scope.value,
    userId: userId.value || undefined,
  };

  if (timeRange.value?.length) {
    params.from = timeRange.value[0].toISOString();
    params.to = timeRange.value[1].toISOString();
  }

  return params;
}

async function load() {
  loading.value = true;
  try {
    const res = await fetchSystemLogs(buildQuery());
    if (res.code !== 0) {
      message.error(res.message || "加载日志列表失败");
      return;
    }
    items.value = res.data.items ?? [];
    total.value = res.data.total ?? 0;
    page.value = res.data.page ?? page.value;
    pageSize.value = res.data.size ?? pageSize.value;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  page.value = 1;
  load();
}

function resetFilters() {
  timeRange.value = defaultRange();
  module.value = undefined;
  level.value = undefined;
  source.value = undefined;
  scope.value = undefined;
  traceId.value = "";
  action.value = "";
  keyword.value = "";
  userId.value = null;
  page.value = 1;
  pageSize.value = 20;
  load();
}

function applyTraceFilter(value?: string | null) {
  if (!value) return;
  traceId.value = value;
  page.value = 1;
  load();
}

function openDetail(record: SystemLogItem) {
  current.value = record;
  drawerOpen.value = true;
}

function handleTableChange(nextPagination: TablePaginationConfig) {
  page.value = nextPagination.current || 1;
  pageSize.value = nextPagination.pageSize || 20;
  load();
}

onMounted(() => {
  load();
});
</script>

<style scoped>
.logs-page {
  height: 100%;
  overflow: auto;
}

.logs-shell,
.detail-shell {
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

.query-tip,
.cell-sub {
  color: var(--muted);
  font-size: 12px;
}

.cell-stack,
.summary-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-action {
  font-weight: 600;
  color: var(--text);
}

.summary-message,
.message-block {
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.trace-link {
  padding-inline: 0;
  height: auto;
  text-align: left;
  white-space: normal;
  word-break: break-all;
}

@media (max-width: 768px) {
  .summary-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
