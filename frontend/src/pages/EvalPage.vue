<template>
  <div class="page">
    <a-card title="离线评估（触发任务）" size="small">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="检索模式">
              <a-select v-model:value="modes" mode="multiple" :options="modeOpts" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="重排开关">
              <a-select v-model:value="rerankOptions" mode="multiple" :options="rerankOpts" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="召回数">
              <a-input-number v-model:value="topK" :min="1" :max="30" style="width:100%;" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="重排候选数">
              <a-input-number v-model:value="candidateK" :min="5" :max="60" style="width:100%;" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="12">
          <a-col :span="6">
            <a-form-item label="混合权重">
              <a-input-number v-model:value="alpha" :min="0" :max="1" :step="0.05" style="width:100%;" />
            </a-form-item>
          </a-col>
          <a-col :span="18">
            <a-form-item label="数据集 ID（可选）">
              <a-input v-model:value="datasetId" placeholder="不填则使用默认 evaluation/questions.jsonl" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-space>
          <a-button type="primary" :loading="loading" @click="run">触发评估</a-button>
          <a-button @click="clear">清空</a-button>
        </a-space>
      </a-form>

      <a-divider />

      <a-alert v-if="jobId" type="success" show-icon>
        <template #message>已触发评估任务：<span class="mono">{{ jobId }}</span></template>
        <template #description>
          后端可实现：GET /api/v1/eval/jobs/{jobId} 返回进度；或直接在日志/文件中查看输出。
        </template>
      </a-alert>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { message } from "ant-design-vue";
import { runEval } from "@/api/eval";

const loading = ref(false);
const datasetId = ref("");
const modes = ref<string[]>(["dense", "hybrid"]);
const rerankOptions = ref<boolean[]>([false, true]);
const topK = ref(6);
const candidateK = ref(20);
const alpha = ref(0.5);
const jobId = ref("");

const modeOpts = [
  { value: "dense", label: "向量检索" },
  { value: "hybrid", label: "混合检索" }
];
const rerankOpts = [{ value: false, label: "否" }, { value: true, label: "是" }];

async function run() {
  loading.value = true;
  try {
    const res = await runEval({
      datasetId: datasetId.value || undefined,
      modes: modes.value,
      rerankOptions: rerankOptions.value,
      topK: topK.value,
      candidateK: candidateK.value,
      alpha: alpha.value
    });
    if (res.code !== 0) return message.error(res.message || "触发失败");
    jobId.value = res.data.jobId;
    message.success("已触发评估");
  } finally {
    loading.value = false;
  }
}

function clear() {
  datasetId.value = "";
  jobId.value = "";
}
</script>
