<template>
  <a-config-provider :theme="antdTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { theme } from "ant-design-vue";
import { useAppStore } from "@/stores/app";
import { useAuthStore } from "@/stores/auth";

const { defaultAlgorithm, darkAlgorithm } = theme;
const appStore = useAppStore();
const authStore = useAuthStore();
appStore.initThemeMode();
authStore.initAuthState();

const antdTheme = computed(() => ({
  algorithm: appStore.resolvedTheme === "dark" ? darkAlgorithm : defaultAlgorithm,
}));
</script>
