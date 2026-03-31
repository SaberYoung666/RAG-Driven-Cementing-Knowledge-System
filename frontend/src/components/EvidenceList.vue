<template>
  <a-space direction="vertical" style="width:100%;" size="middle">
    <a-card v-for="(e, idx) in items" :key="e.chunkId + idx" size="small">
      <template #title>
        <span>证据 {{ idx + 1 }}</span>
        <span style="margin-left:12px; color:var(--muted);" class="mono">分数={{ fmt(e.score) }}</span>
      </template>

      <div style="color:var(--muted); margin-bottom:8px;" class="mono">
        来源={{ e.metadata?.source ?? e.source ?? "-" }}
        &nbsp; 页码={{ e.metadata?.page ?? e.page ?? "-" }}
        &nbsp; 章节={{ e.metadata?.section ?? e.section ?? "-" }}
        &nbsp; 分块 ID={{ e.chunkId }}
      </div>

      <a-collapse>
        <a-collapse-panel key="1" header="展开证据正文">
          <div style="white-space: pre-wrap; line-height: 1.7;">
            {{ e.text || "(无正文返回)" }}
          </div>
        </a-collapse-panel>
      </a-collapse>
    </a-card>
  </a-space>
</template>

<script setup lang="ts">
import type { RetrievedChunk } from "@/types";

defineProps<{ items: RetrievedChunk[] }>();

function fmt(v: any) {
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(4) : String(v ?? "");
}
</script>
