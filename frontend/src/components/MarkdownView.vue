<template>
  <div class="md" v-html="html"></div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownIt from "markdown-it";

const props = defineProps<{ content: string }>();

const md = new MarkdownIt({ linkify: true, breaks: true });

const html = computed(() => md.render(props.content || ""));
</script>

<style scoped>
.md :deep(h1), .md :deep(h2), .md :deep(h3) { margin: 12px 0 8px; }
.md :deep(p) { line-height: 1.7; margin: 8px 0; }
.md :deep(code) {
  padding: 2px 6px;
  background: var(--bg-soft);
  border-radius: 6px;
}
.md :deep(pre) {
  padding: 12px;
  background: var(--bg-soft);
  border-radius: 12px;
  overflow: auto;
  border: 1px solid var(--border);
}
</style>
