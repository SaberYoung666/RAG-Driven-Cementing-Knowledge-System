<template>
  <div class="page console-page">
    <a-card title="运行控制台" size="small">
      <div class="console-shell">
        <div class="console-toolbar">
          <a-space wrap>
            <a-select v-model:value="sourceFilter" style="width: 150px" :options="sourceOptions" />
            <a-switch v-model:checked="autoScroll" checked-children="自动滚动" un-checked-children="手动浏览" />
            <a-button @click="clearLogs">清空显示</a-button>
          </a-space>
          <div class="status-group">
            <a-tag :color="backendConnected ? 'success' : 'error'">后端 {{ backendConnected ? "已连接" : "断开" }}</a-tag>
            <a-tag :color="ragConnected ? 'success' : 'error'">RAG {{ ragConnected ? "已连接" : "断开" }}</a-tag>
            <span class="status-meta">共 {{ filteredLogs.length }} 段</span>
          </div>
        </div>

        <div ref="logViewport" class="console-viewport mono">
          <div v-if="filteredLogs.length === 0" class="console-empty">暂无日志输出</div>
          <pre v-else class="console-output">{{ filteredOutput }}</pre>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import type { RealtimeLogEntry } from "@/api/realtimeLogs";
import type { RealtimeConsoleEntry } from "@/api/realtimeConsole";
import { getConsoleLogs, subscribeConsoleLogs } from "@/logging/consoleCapture";
import {
  ensureRealtimeConsoleStreamsStarted,
  getRealtimeConsoleStreamLogs,
  getRealtimeConsoleStreamStatus,
  subscribeRealtimeConsoleStreamLogs,
  subscribeRealtimeConsoleStreamStatus,
} from "@/logging/realtimeConsoleStreams";

type DisplayLogEntry = {
  id: string;
  source: "frontend" | "backend" | "rag";
  raw: string;
  timestamp: string;
};

const LOG_LIMIT = 1000;
const logs = ref<DisplayLogEntry[]>([]);
const sourceFilter = ref<"all" | "frontend" | "backend" | "rag">("all");
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

const filteredLogs = computed(() =>
  logs.value.filter((item) => sourceFilter.value === "all" || item.source === sourceFilter.value)
);
const filteredOutput = computed(() => filteredLogs.value.map((item) => item.raw).join(""));

let logSequence = 0;
let stopConsoleSubscribe: (() => void) | null = null;
let stopRealtimeConsoleSubscribe: (() => void) | null = null;
let stopRealtimeStatusSubscribe: (() => void) | null = null;

function appendDisplayEntry(source: DisplayLogEntry["source"], timestamp: string, raw: string) {
  logs.value.push({
    id: `${source}-${timestamp}-${logSequence++}`,
    source,
    timestamp,
    raw,
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

function ensureTrailingNewline(value: string) {
  return value.endsWith("\n") ? value : `${value}\n`;
}

function appendFrontendLog(entry: RealtimeLogEntry) {
  const raw = entry.details ? `${entry.message}\n${entry.details}` : entry.message;
  appendDisplayEntry("frontend", entry.timestamp, ensureTrailingNewline(raw));
}

function appendRealtimeChunk(entry: RealtimeConsoleEntry) {
  if (!entry.raw) return;
  appendDisplayEntry(entry.source, entry.timestamp, entry.raw);
}

function clearLogs() {
  logs.value = [];
}

onMounted(() => {
  ensureRealtimeConsoleStreamsStarted();

  getConsoleLogs().forEach((entry) => appendFrontendLog(entry));
  stopConsoleSubscribe = subscribeConsoleLogs(appendFrontendLog);

  getRealtimeConsoleStreamLogs().forEach((entry) => appendRealtimeChunk(entry));
  stopRealtimeConsoleSubscribe = subscribeRealtimeConsoleStreamLogs(appendRealtimeChunk);

  const currentStatus = getRealtimeConsoleStreamStatus();
  backendConnected.value = currentStatus.backendConnected;
  ragConnected.value = currentStatus.ragConnected;
  stopRealtimeStatusSubscribe = subscribeRealtimeConsoleStreamStatus((status) => {
    backendConnected.value = status.backendConnected;
    ragConnected.value = status.ragConnected;
  });
});

onBeforeUnmount(() => {
  stopConsoleSubscribe?.();
  stopRealtimeConsoleSubscribe?.();
  stopRealtimeStatusSubscribe?.();
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
}

.console-empty {
  min-height: 240px;
  display: grid;
  place-items: center;
  color: var(--muted);
}

.console-output {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: var(--text);
}

@media (max-width: 768px) {
  .console-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .console-viewport {
    min-height: 55vh;
    max-height: none;
  }
}
</style>
