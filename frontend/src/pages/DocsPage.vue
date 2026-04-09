<template>
  <div class="page docs-page">
    <a-card title="文档管理" size="small">
      <a-alert
        v-if="!appStore.ragStatus.connected || !appStore.ragStatus.serviceAvailable"
        type="warning"
        show-icon
        style="margin-bottom:12px;"
        :message="docsProcessBlockedReason"
      />
      <a-space style="margin-bottom:12px;">
        <a-upload :beforeUpload="beforeUpload" :showUploadList="false">
          <a-button type="primary">上传并入库</a-button>
        </a-upload>
        <a-switch v-model:checked="overwrite" />
        <span style="color:var(--muted);">覆盖已存在文档</span>
        <span @click.stop>
          <a-popconfirm
            title="确认重建全部索引？"
            description="这会按当前分块数据全量重建 FAISS 和 BM25，期间 CPU 占用会提升。"
            ok-text="重建"
            cancel-text="取消"
            @confirm="triggerRebuildIndex"
          >
            <a-button :loading="rebuildLoading" :disabled="isRebuildDisabled">重建索引</a-button>
          </a-popconfirm>
        </span>

        <a-input v-model:value="keyword" placeholder="关键词" style="width:240px;" allowClear />
        <a-button @click="load">查询</a-button>
        <a-button @click="load">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </a-space>

      <a-table
        :columns="columns"
        :dataSource="items"
        :pagination="pagination"
        rowKey="docId"
        :loading="loading"
        :customRow="bindRowEvents"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            {{ processStatusText(record.status) }}
          </template>
          <template v-if="column.key === 'actions'">
            <a-space size="small">
              <a-button size="small" :disabled="isProcessActionDisabled(record)" @click.stop="onProcessAction(record)">{{ processActionLabel(record) }}</a-button>
              <span @click.stop>
                <a-popconfirm title="确认删除？" @confirm="remove(record.docId)">
                  <a-button danger size="small" @click.stop>删除</a-button>
                </a-popconfirm>
              </span>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="processModalOpen"
      :title="processModalTitle"
      :footer="null"
      width="640"
      :confirm-loading="processModalLoading"
    >
      <a-descriptions bordered :column="1" size="small">
        <a-descriptions-item label="文档编号">{{ processDocId || "-" }}</a-descriptions-item>
        <a-descriptions-item label="处理状态">{{ processStatusText(processInfo?.status) }}</a-descriptions-item>
        <a-descriptions-item label="进度">
          <template v-if="normalizeStatus(processInfo?.status) === 'PROCESSING'">
            <a-progress :percent="processPercent" :status="progressStatus" />
          </template>
          <template v-else>{{ processPercent }}%</template>
        </a-descriptions-item>
        <a-descriptions-item label="阶段">{{ processInfo?.stage || "-" }}</a-descriptions-item>
        <a-descriptions-item label="失败阶段">{{ processInfo?.failedStage || "-" }}</a-descriptions-item>
        <a-descriptions-item label="异常类型">{{ processInfo?.errorType || "-" }}</a-descriptions-item>
        <a-descriptions-item label="Trace ID">
          <a-space v-if="processInfo?.traceId" size="small">
            <span>{{ processInfo.traceId }}</span>
            <a-button type="link" size="small" @click="openLogsForTrace(processInfo.traceId)">查看日志</a-button>
          </a-space>
          <template v-else>-</template>
        </a-descriptions-item>
        <a-descriptions-item label="分块数">{{ processInfo?.chunkCount ?? "-" }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ processInfo?.updatedAt || "-" }}</a-descriptions-item>
        <a-descriptions-item label="说明">{{ processInfo?.message || processInfo?.detail || "-" }}</a-descriptions-item>
      </a-descriptions>
      <a-collapse v-if="processInfo?.debugDetail" style="margin-top: 12px;">
        <a-collapse-panel key="debug" header="调试详情">
          <pre class="debug-detail">{{ processInfo.debugDetail }}</pre>
        </a-collapse-panel>
      </a-collapse>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { ReloadOutlined } from "@ant-design/icons-vue";
import type { TablePaginationConfig } from "ant-design-vue";
import { ingestFile, listDocs, deleteDoc, processDoc, getDocProcessInfo, rebuildIndex } from "@/api/docs";
import { useAppStore } from "@/stores/app";
import type { DocItem, DocProcessInfo } from "@/types";

const MAX_UPLOAD_SIZE_MB = 500;
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024;
const appStore = useAppStore();
const router = useRouter();

const loading = ref(false);
const items = ref<DocItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const keyword = ref("");
const overwrite = ref(false);
const processModalOpen = ref(false);
const processModalLoading = ref(false);
const processDocId = ref("");
const processInfo = ref<DocProcessInfo | null>(null);
const rebuildLoading = ref(false);
let autoRefreshTimer: number | null = null;
let isLoadingDocs = false;

function isTerminalStatus(status?: string) {
  const s = normalizeStatus(status);
  return s === "READY" || s === "FAILED";
}

const columns = [
  { title: "文档编号", dataIndex: "docId", key: "docId", width: 220 },
  { title: "标题", dataIndex: "title", key: "title" },
  { title: "状态", dataIndex: "status", key: "status", width: 120 },
  { title: "分块数", dataIndex: "chunkCount", key: "chunkCount", width: 120 },
  { title: "操作", key: "actions", width: 200 }
];

const pagination = ref<TablePaginationConfig>({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true
});

const processModalTitle = computed(() => {
  if (!processInfo.value) return "处理详情";
  const s = normalizeStatus(processInfo.value.status);
  return s === "PROCESSING" ? "处理进度" : "处理详情";
});

const processPercent = computed(() => {
  const raw = Number(processInfo.value?.progress ?? 0);
  if (!Number.isFinite(raw)) return 0;
  return Math.max(0, Math.min(100, Math.round(raw)));
});

const progressStatus = computed(() => {
  const s = normalizeStatus(processInfo.value?.status);
  if (s === "FAILED") return "exception";
  if (s === "READY") return "success";
  return "active";
});
const docsProcessBlockedReason = computed(() => appStore.ragStatus.message || "RAG 服务未连接，请稍后再试");
const isRebuildDisabled = computed(() => rebuildLoading.value || !appStore.ragStatus.connected || !appStore.ragStatus.serviceAvailable);

function normalizeStatus(status?: string) {
  const s = (status || "").toUpperCase();
  if (!s) return "UNPROCESSED";
  if (s === "READY" || s === "SUCCESS" || s === "DONE") return "READY";
  if (s === "PROCESSING" || s === "RUNNING" || s === "PENDING" || s === "QUEUED" || s === "INDEXING" || s === "OCR_PROCESSING" || s === "CLEANING" || s === "SPLITTING") return "PROCESSING";
  if (s === "FAILED" || s === "ERROR") return "FAILED";
  if (s === "未处理") return "UNPROCESSED";
  if (s === "处理中") return "PROCESSING";
  if (s === "处理失败") return "FAILED";
  if (s === "已处理") return "READY";
  return s;
}

function processStatusText(status?: string) {
  const s = normalizeStatus(status);
  if (s === "UNPROCESSED") return "未处理";
  if (s === "PROCESSING") return "处理中";
  if (s === "FAILED") return "处理失败";
  if (s === "READY") return "已处理（待重建索引）";
  return status || "-";
}

function processActionLabel(record: DocItem) {
  const s = normalizeStatus(record.status);
  if (s === "PROCESSING") return "查看详情";
  if (s === "FAILED") return "重新处理";
  if (s === "READY") return "查看详情";
  return "处理";
}

function isProcessActionDisabled(record: DocItem) {
  const s = normalizeStatus(record.status);
  if (s === "PROCESSING" || s === "READY") return false;
  return !appStore.ragStatus.connected || !appStore.ragStatus.serviceAvailable;
}

async function load(options: { silent?: boolean } = {}) {
  if (isLoadingDocs) return;
  const { silent = false } = options;
  isLoadingDocs = true;
  if (!silent) loading.value = true;
  try {
    const res = await listDocs({ page: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined });
    if (res.code !== 0) {
      if (!silent) message.error(res.message || "加载失败");
      return;
    }
    items.value = res.data.items;
    total.value = res.data.total;
    pagination.value = { ...pagination.value, current: page.value, pageSize: pageSize.value, total: total.value };
  } catch {
    if (!silent) message.error("加载失败");
  } finally {
    if (!silent) loading.value = false;
    isLoadingDocs = false;
  }
}

function onTableChange(p: TablePaginationConfig) {
  page.value = p.current || 1;
  pageSize.value = p.pageSize || 10;
  load();
}

async function openProcessModal(docId: string) {
  processDocId.value = docId;
  processInfo.value = null;
  processModalOpen.value = true;
  processModalLoading.value = true;
  await refreshProcessInfo();
}

async function refreshProcessInfo(silent = false) {
  if (!processDocId.value) return;
  if (processInfo.value?.docId === processDocId.value && isTerminalStatus(processInfo.value?.status)) return;
  if (!silent) processModalLoading.value = true;
  try {
    const res = await getDocProcessInfo(processDocId.value);
    if (res.code !== 0) {
      if (!silent) message.error(res.message || "加载处理信息失败");
      processInfo.value = { docId: processDocId.value, message: "暂无处理详情" };
      return;
    }
    processInfo.value = res.data;
    if (isTerminalStatus(processInfo.value?.status)) {
      void load({ silent: true });
    }
  } catch {
    processInfo.value = { docId: processDocId.value, message: "暂无处理详情" };
  } finally {
    if (!silent) processModalLoading.value = false;
  }
}

async function triggerProcess(docId: string) {
  const res = await processDoc(docId);
  if (res.code !== 0) return message.error(res.message || "处理触发失败");
  if (!res.data || res.data.acceptedCount <= 0) {
    message.warning("当前文档未被受理，可能已在处理中或状态不支持");
    return;
  }
  message.success(`已提交 ${res.data.acceptedCount} 个处理任务`);
  await load();
}

async function triggerRebuildIndex() {
  if (isRebuildDisabled.value) {
    message.warning(docsProcessBlockedReason.value);
    return;
  }
  rebuildLoading.value = true;
  try {
    const res = await rebuildIndex({ rebuildFaiss: true, rebuildBm25: true });
    if (res.code !== 0 || !res.data) {
      message.error(res.message || "重建索引失败");
      return;
    }
    message.success(res.data.message || "索引重建已完成");
  } catch {
    message.error("重建索引失败");
  } finally {
    rebuildLoading.value = false;
  }
}

async function onProcessAction(record: DocItem) {
  const s = normalizeStatus(record.status);
  if ((s === "UNPROCESSED" || s === "FAILED") && isProcessActionDisabled(record)) {
    message.warning(docsProcessBlockedReason.value);
    return;
  }
  if (s === "PROCESSING" || s === "READY") {
    await openProcessModal(record.docId);
    return;
  }
  await triggerProcess(record.docId);
}

function bindRowEvents(record: DocItem) {
  return {
    style: { cursor: "pointer" },
    onClick: () => {
      void openProcessModal(record.docId);
    }
  };
}

function openLogsForTrace(traceId: string) {
  router.push({ path: "/admin/logs/list", query: { traceId } });
}

async function remove(docId: string) {
  const res = await deleteDoc(docId);
  if (res.code !== 0) return message.error(res.message || "删除失败");
  message.success("已删除");
  load();
}

async function beforeUpload(file: File) {
  if (file.size > MAX_UPLOAD_SIZE_BYTES) {
    message.error(`上传文件不能超过 ${MAX_UPLOAD_SIZE_MB}MB`);
    return false;
  }
  const res = await ingestFile(file, overwrite.value);
  if (res.code !== 0) return message.error(res.message || "上传失败");
  message.success(res.data.message || "已提交入库任务");
  load();
  return false; // 阻止 Upload 默认上传，走我们自定义
}

onMounted(() => {
  void load();
  autoRefreshTimer = window.setInterval(() => {
    if (items.value.some((item) => normalizeStatus(item.status) === "PROCESSING")) {
      void load({ silent: true });
    }
    if (processModalOpen.value && processDocId.value && !isTerminalStatus(processInfo.value?.status)) {
      void refreshProcessInfo(true);
    }
  }, 2000);
});

onBeforeUnmount(() => {
  if (autoRefreshTimer !== null) {
    window.clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
});
</script>

<style scoped>
.docs-page {
  padding-top: 0;
}

.debug-detail {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
