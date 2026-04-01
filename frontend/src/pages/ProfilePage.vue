<template>
  <div class="page profile-page">
    <a-card title="个人信息" :bordered="true">
      <a-descriptions :column="1" bordered size="middle">
        <a-descriptions-item label="用户编号">{{ authStore.user?.id ?? "-" }}</a-descriptions-item>
        <a-descriptions-item label="用户名">{{ authStore.user?.username ?? "-" }}</a-descriptions-item>
        <a-descriptions-item label="角色">{{ authStore.user?.role ?? "-" }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ authStore.user?.createdAt ?? "-" }}</a-descriptions-item>
      </a-descriptions>

      <div class="profile-actions">
        <a-button danger @click="handleLogout">退出登录</a-button>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { message } from "ant-design-vue";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const authStore = useAuthStore();

onMounted(async () => {
  await authStore.initAuthState();
  if (!authStore.isLoggedIn) {
    message.warning("请先登录");
    router.replace("/auth");
  }
});

function handleLogout() {
  authStore.logout();
  message.success("已退出登录");
  router.replace("/auth");
}
</script>

<style scoped>
.profile-page {
  height: 100%;
  overflow: auto;
}

.profile-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
