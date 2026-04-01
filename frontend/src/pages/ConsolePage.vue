<template>
  <div class="page console-page">
    <a-card title="运行控制台" size="small">
      <div class="console-shell">
        <div class="console-toolbar">
          <a-space wrap>
            <a-select v-model:value="sourceFilter" style="width: 150px" :options="sourceOptions" />
            <a-select v-model:value="levelFilter" style="width: 150px" :options="levelOptions" />
            <a-switch v-model:checked="autoScroll" checked-children="自动滚动" un-checked-children="手动浏览" />
            <a-button @click="clearLogs">清空显示</a-button>
          </a-space>
          <div class="status-group">
            <a-tag :color="backendConnected ? 'success' : 'error'">后端 {{ backendConnected ? "已连接" : "断开" }}</a-tag>
            <a-tag :color="ragConnected ? 'success' : 'error'">RAG {{ ragConnected ? "已连接" : "断开" }}</a-tag>
            <span class="status-meta">共 {{ filteredLogs.length }} 条</span>
          </div>
        </div>

        <div ref="logViewport" class="console-viewport mono">
          <div v-if="filteredLogs.length === 0" class="console-empty">暂无日志输出</div>
          <div v-for="item in filteredLogs" :key="item.id" class="log-row" :class="`level-${normalizeLevel(item.level)}`">
            <div class="log-meta">
              <span class="log-time">{{ formatTime(item.timestamp) }}</span>
              <a-tag class="log-tag" :color="getSourceColor(item.source)">{{ item.source }}</a-tag>
              <a-tag class="log-tag" :color="getLevelColor(item.level)">{{ normalizeLevel(item.level) }}</a-tag>
              <span class="log-logger">{{ item.logger || "-" }}</span>
            </div>
            <div class="log-message">{{ item.message }}</div>
            <pre v-if="item.details" class="log-details">{{ item.details }}</pre>
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import dayjs from "dayjs";
import { connectRealtimeLogStream, type RealtimeLogEntry } from "@/api/realtimeLogs";
import { getConsoleLogs, subscribeConsoleLogs } from "@/logging/consoleCapture";

type DisplayLogEntry = RealtimeLogEntry & { id: string };

const LOG_LIMIT = 1000;
const logs = ref<DisplayLogEntry[]>([]);
const sourceFilter = ref<"all" | "frontend" | "backend" | "rag">("all");
const levelFilter = ref<"all" | "DEBUG" | "INFO" | "WARN" | "ERROR">("all");
const autoScroll = ref(true);
const backendConnected = ref(false);
const ragConnected = ref(false);
const logViewport = ref<HTMLElement | null>(null);

const sourceOptions = [
  { value: "all", label: "全部来源" },
  { value: "frontend", label: "前端" },
  { value: "backend", label: "后端" },
  { value: "rag", label: "RAG" },
];

const levelOptions = [
  { value: "all", label: "全部级别" },
  { value: "DEBUG", label: "DEBUG" },
  { value: "INFO", label: "INFO" },
  { value: "WARN", label: "WARN" },
  { value: "ERROR", label: "ERROR" },
];

const filteredLogs = computed(() =>
  logs.value.filter((item) => {
    const sourceMatched = sourceFilter.value === "all" || item.source === sourceFilter.value;
    const levelMatched = levelFilter.value === "all" || normalizeLevel(item.level) === levelFilter.value;
    return sourceMatched && levelMatched;
  })
);

let logSequence = 0;
let stopConsoleSubscribe: (() => void) | null = null;
let stopBackendStream: (() => void) | null = null;
let stopRagStream: (() => void) | null = null;

function appendLog(entry: RealtimeLogEntry) {
  logs.value.push({
    ...entry,
    id: `${entry.source}-${entry.timestamp}-${logSequence++}`,
  });
  if (logs.value.length > LOG_LIMIT) {
    logs.value.splice(0, logs.value.length - LOG_LIMIT);
  }
  if (autoScroll.value) {
    void nextTick(() => {
      if (logViewport.value) {
        logViewport.value.scrollTop = logViewport.value.scrollHeight;
      }
    });
  }
}

function clearLogs() {
  logs.value = [];
}

function normalizeLevel(level?: string | null) {
  const normalized = (level || "INFO").toUpperCase();
  if (["DEBUG", "INFO", "WARN", "ERROR"].includes(normalized)) {
    return normalized;
  }
  return "INFO";
}

function formatTime(value: string) {
  return dayjs(value).isValid() ? dayjs(value).format("HH:mm:ss.SSS") : value;
}

function getLevelColor(level?: string | null) {
  switch (normalizeLevel(level)) {
    case "DEBUG":
      return "default";
    case "WARN":
      return "warning";
    case "ERROR":
      return "error";
    default:
      return "processing";
  }
}

function getSourceColor(source: string) {
  if (source === "frontend") return "geekblue";
  if (source === "backend") return "green";
  return "volcano";
}

onMounted(() => {
  getConsoleLogs().forEach(appendLog);
  stopConsoleSubscribe = subscribeConsoleLogs(appendLog);

  const backendConnection = connectRealtimeLogStream(
    "/api/v1/realtime-logs/backend",
    appendLog,
    (connected) => {
      backendConnected.value = connected;
    }
  );
  stopBackendStream = backendConnection.stop;

  const ragConnection = connectRealtimeLogStream(
    "/api/v1/realtime-logs/rag",
    appendLog,
    (connected) => {
      ragConnected.value = connected;
    }
  );
  stopRagStream = ragConnection.stop;
});

onBeforeUnmount(() => {
  stopConsoleSubscribe?.();
  stopBackendStream?.();
  stopRagStream?.();
});
</script>

<style scoped>
.console-page {
  height: 100%;
  overflow: auto;
}

.console-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.console-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.status-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.status-meta {
  color: var(--muted);
  font-size: 12px;
}

.console-viewport {
  min-height: 65vh;
  max-height: 70vh;
  overflow: auto;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background:
    radial-gradient(circle at top right, rgba(var(--accent-rgb), 0.08), transparent 25%),
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 95%, transparent), var(--bg-soft));
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.console-empty {
  min-height: 240px;
  display: grid;
  place-items: center;
  color: var(--muted);
}

.log-row {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
}

.log-row.level-ERROR {
  border-color: rgba(239, 68, 68, 0.4);
}

.log-row.level-WARN {
  border-color: rgba(245, 158, 11, 0.4);
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-time,
.log-logger {
  color: var(--muted);
  font-size: 12px;
}

.log-tag {
  margin-inline-end: 0;
}

.log-message {
  margin-top: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: var(--text);
}

.log-details {
  margin: 10px 0 0;
  padding: 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .console-toolbar,
  .log-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .console-viewport {
    min-height: 55vh;
    max-height: none;
  }
}
</style>
