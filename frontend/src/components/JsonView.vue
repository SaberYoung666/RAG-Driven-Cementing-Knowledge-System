<template>
  <a-typography-paragraph class="mono" :copyable="{ text: pretty }">
    <pre style="margin:0;">{{ pretty }}</pre>
  </a-typography-paragraph>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ value: any }>();
const pretty = computed(() => JSON.stringify(props.value, null, 2));
</script>
