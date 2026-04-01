<template>
  <a-layout class="app-layout">
    <a-layout-sider
      class="app-sider"
      :width="280"
      :collapsed-width="72"
      :collapsed="collapsed"
      :trigger="null"
      :theme="appStore.resolvedTheme === 'dark' ? 'dark' : 'light'"
    >
      <div class="sider-top">
        <span v-if="!collapsed" class="sider-title">固井 RAG 系统</span>
        <button class="collapse-trigger" type="button" aria-label="折叠侧边栏" @click="toggleCollapsed">
          <MenuFoldOutlined v-if="!collapsed" />
          <MenuUnfoldOutlined v-else />
        </button>
      </div>

      <div class="sider-menu-wrap">
        <div class="nav-list">
          <section class="nav-section">
            <div class="nav-row" :class="{ active: isChatRoute }">
              <button class="nav-main" type="button" @click="goChatRoot">
                <CommentOutlined class="nav-icon" />
                <span v-if="!collapsed" class="nav-label">问答</span>
              </button>
              <button
                v-if="!collapsed"
                class="nav-toggle"
                type="button"
                :aria-label="chatChildrenOpen ? '收起问答子列表' : '展开问答子列表'"
                @click.stop="chatChildrenOpen = !chatChildrenOpen"
              >
                <UpOutlined v-if="chatChildrenOpen" />
                <DownOutlined v-else />
              </button>
            </div>

            <div v-if="!collapsed && chatChildrenOpen" class="nav-children">
              <button class="child-create-btn" type="button" @click="startNewConversation">
                <PlusOutlined />
                <span>新对话</span>
              </button>

              <a-spin :spinning="sessionLoading">
                <div v-if="sessionItems.length" class="session-list">
                  <div
                    v-for="item in sessionItems"
                    :key="item.sessionId"
                    class="session-item"
                    :class="{ active: isActiveSession(item.sessionId) }"
                    role="button"
                    tabindex="0"
                    @click="openSession(item.sessionId)"
                    @keydown.enter.prevent="openSession(item.sessionId)"
                    @keydown.space.prevent="openSession(item.sessionId)"
                  >
                    <div class="session-main">
                      <div class="session-name">{{ item.title || "未命名会话" }}</div>
                      <div class="session-time">{{ formatSessionTime(item.lastMessageAt || item.updatedAt || item.createdAt) }}</div>
                    </div>
                    <div class="session-actions" @click.stop>
                      <button class="session-action-btn" type="button" aria-label="重命名" @click="openRename(item)">
                        <EditOutlined />
                      </button>
                      <a-popconfirm title="确认删除该会话？" ok-text="删除" cancel-text="取消" @confirm="removeSession(item.sessionId)">
                        <button class="session-action-btn danger" type="button" aria-label="删除" @click.stop>
                          <DeleteOutlined />
                        </button>
                      </a-popconfirm>
                    </div>
                  </div>

                  <button v-if="hasMoreSessions" class="child-more-btn" type="button" @click="loadMoreSessions">
                    展开更多
                  </button>
                </div>

                <div v-else class="child-empty">暂无会话</div>
              </a-spin>
            </div>
          </section>

          <button class="single-row" :class="{ active: route.path === '/admin/docs' }" type="button" @click="go('/admin/docs')">
            <FileTextOutlined class="nav-icon" />
            <span v-if="!collapsed" class="nav-label">文档管理</span>
          </button>

          <section class="nav-section">
            <div class="nav-row" :class="{ active: isLogsRoute }">
              <button class="nav-main" type="button" @click="go('/admin/logs/overview')">
                <ProfileOutlined class="nav-icon" />
                <span v-if="!collapsed" class="nav-label">日志</span>
              </button>
              <button
                v-if="!collapsed"
                class="nav-toggle"
                type="button"
                :aria-label="logsChildrenOpen ? '收起日志子列表' : '展开日志子列表'"
                @click.stop="logsChildrenOpen = !logsChildrenOpen"
              >
                <UpOutlined v-if="logsChildrenOpen" />
                <DownOutlined v-else />
              </button>
            </div>

            <div v-if="!collapsed && logsChildrenOpen" class="nav-children">
              <button
                class="child-link"
                :class="{ active: route.path === '/admin/logs/overview' }"
                type="button"
                @click="go('/admin/logs/overview')"
              >
                日志总览
              </button>
              <button
                class="child-link"
                :class="{ active: route.path === '/admin/logs/list' }"
                type="button"
                @click="go('/admin/logs/list')"
              >
                日志列表
              </button>
              <button
                v-if="isAdmin"
                class="child-link"
                :class="{ active: route.path === '/admin/console' }"
                type="button"
                @click="go('/admin/console')"
              >
                运行控制台
              </button>
            </div>
          </section>

          <button class="single-row" :class="{ active: route.path === '/admin/eval' }" type="button" @click="go('/admin/eval')">
            <BarChartOutlined class="nav-icon" />
            <span v-if="!collapsed" class="nav-label">评估</span>
          </button>
        </div>
      </div>

      <div class="sider-bottom">
        <div class="bottom-item">
          <a-dropdown :trigger="['hover']" :placement="collapsed ? 'rightTop' : 'topLeft'">
            <button class="theme-switch-btn" type="button" aria-label="主题切换">
              <component :is="currentThemeIcon" class="theme-switch-icon" />
              <span v-if="!collapsed" class="theme-switch-text">{{ currentThemeLabel }}</span>
              <DownOutlined v-if="!collapsed" class="theme-switch-arrow" />
            </button>
            <template #overlay>
              <a-menu :selectedKeys="[appStore.themeMode]" @click="onThemeMenuClick">
                <a-menu-item key="light">
                  <div class="theme-option">
                    <BulbOutlined />
                    <span>浅色</span>
                  </div>
                </a-menu-item>
                <a-menu-item key="dark">
                  <div class="theme-option">
                    <CloudOutlined />
                    <span>深色</span>
                  </div>
                </a-menu-item>
                <a-menu-item key="system">
                  <div class="theme-option">
                    <DesktopOutlined />
                    <span>系统</span>
                  </div>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>

        <button class="profile-btn" type="button" aria-label="个人中心" @click="goProfile">
          <a-avatar class="app-header__avatar" :size="32" :src="userAvatar">
            <template #icon>
              <UserOutlined />
            </template>
          </a-avatar>
          <span v-if="!collapsed" class="profile-name">{{ userName }}</span>
        </button>
      </div>
    </a-layout-sider>

    <a-layout class="app-main">
      <a-layout-content class="app-content">
        <div class="app-content__inner">
          <router-view />
        </div>
      </a-layout-content>
    </a-layout>

    <a-modal v-model:open="renameOpen" title="修改会话标题" ok-text="保存" cancel-text="取消" :confirm-loading="renameLoading" @ok="confirmRename">
      <a-input v-model:value="renameTitle" :maxlength="80" placeholder="请输入新标题" />
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import dayjs from "dayjs";
import { message } from "ant-design-vue";
import { useRoute, useRouter } from "vue-router";
import {
  UserOutlined,
  BulbOutlined,
  CloudOutlined,
  DesktopOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CommentOutlined,
  FileTextOutlined,
  ProfileOutlined,
  BarChartOutlined,
  DownOutlined,
  UpOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
} from "@ant-design/icons-vue";
import { useAppStore, type ThemeMode } from "@/stores/app";
import { useAuthStore } from "@/stores/auth";
import { deleteSession, listSessions, updateSessionTitle } from "@/api/chat";
import type { ChatSessionItem } from "@/types";

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();

const collapsed = ref(false);
const chatChildrenOpen = ref(true);
const logsChildrenOpen = ref(true);
const userAvatar = ref<string>("");
const userName = computed(() => authStore.user?.username || "未登录");
const isAdmin = computed(() => authStore.user?.role === "ADMIN");
const isChatRoute = computed(() => route.path === "/chat");
const isLogsRoute = computed(() => route.path.startsWith("/admin/logs"));
const currentSessionId = computed(() => (typeof route.query.sessionId === "string" ? route.query.sessionId : ""));

const sessionItems = ref<ChatSessionItem[]>([]);
const sessionLoading = ref(false);
const sessionPage = ref(1);
const sessionPageSize = 10;
const sessionTotal = ref(0);

const renameOpen = ref(false);
const renameLoading = ref(false);
const renameSessionId = ref("");
const renameTitle = ref("");

const hasMoreSessions = computed(() => sessionItems.value.length < sessionTotal.value);

const currentThemeIcon = computed(() => {
  if (appStore.themeMode === "light") return BulbOutlined;
  if (appStore.themeMode === "dark") return CloudOutlined;
  return DesktopOutlined;
});

const currentThemeLabel = computed(() => {
  if (appStore.themeMode === "light") return "浅色";
  if (appStore.themeMode === "dark") return "深色";
  return "系统";
});

function toggleCollapsed() {
  collapsed.value = !collapsed.value;
}

function onThemeMenuClick({ key }: { key: ThemeMode }) {
  appStore.setThemeMode(key);
}

function goProfile() {
  router.push(authStore.isLoggedIn ? "/profile" : "/auth");
}

function go(path: string) {
  router.push(path);
}

function goChatRoot() {
  router.push("/chat");
}

function startNewConversation() {
  const shouldDispatch = route.path === "/chat" && !currentSessionId.value;
  router.push({ path: "/chat", query: {} });
  if (shouldDispatch) {
    window.dispatchEvent(new CustomEvent("chat-new-conversation"));
  }
}

function openSession(sessionId: string) {
  router.push({ path: "/chat", query: { sessionId } });
}

function isActiveSession(sessionId: string) {
  return isChatRoute.value && currentSessionId.value === sessionId;
}

function openRename(item: ChatSessionItem) {
  renameSessionId.value = item.sessionId;
  renameTitle.value = item.title || "";
  renameOpen.value = true;
}

async function confirmRename() {
  const title = renameTitle.value.trim();
  if (!title) {
    message.warning("标题不能为空");
    return;
  }
  renameLoading.value = true;
  try {
    const resp = await updateSessionTitle(renameSessionId.value, title);
    if (resp.code !== 0) {
      message.error(resp.message || "更新标题失败");
      return;
    }
    renameOpen.value = false;
    message.success("标题已更新");
    await refreshSessions();
  } finally {
    renameLoading.value = false;
  }
}

async function removeSession(sessionId: string) {
  const deletingCurrent = currentSessionId.value === sessionId;
  const resp = await deleteSession(sessionId);
  if (resp.code !== 0) {
    message.error(resp.message || "删除会话失败");
    return;
  }
  message.success("会话已删除");
  await refreshSessions();
  if (deletingCurrent) {
    router.push({ path: "/chat", query: {} });
  }
}

async function loadSessionPage(page: number, append = false) {
  if (sessionLoading.value) return;
  sessionLoading.value = true;
  try {
    const resp = await listSessions({ page, pageSize: sessionPageSize });
    if (resp.code !== 0) {
      message.error(resp.message || "加载会话列表失败");
      return;
    }
    const items = resp.data?.items ?? [];
    sessionItems.value = append ? [...sessionItems.value, ...items] : items;
    sessionTotal.value = resp.data?.total ?? 0;
    sessionPage.value = page;
  } finally {
    sessionLoading.value = false;
  }
}

async function refreshSessions() {
  sessionPage.value = 1;
  await loadSessionPage(1, false);
}

async function loadMoreSessions() {
  if (!hasMoreSessions.value) return;
  await loadSessionPage(sessionPage.value + 1, true);
}

function formatSessionTime(ts?: string) {
  if (!ts || !dayjs(ts).isValid()) return "";
  return dayjs(ts).format("MM-DD HH:mm");
}

function handleSessionRefresh() {
  refreshSessions();
}

onMounted(() => {
  refreshSessions();
  window.addEventListener("chat-sessions-updated", handleSessionRefresh);
});

onBeforeUnmount(() => {
  window.removeEventListener("chat-sessions-updated", handleSessionRefresh);
});
</script>

<style scoped>
.app-layout {
  height: 100vh;
  padding: 10px;
  gap: 10px;
  overflow: hidden;
}

.app-main {
  min-height: 0;
  overflow: hidden;
}

.app-sider {
  border-radius: 16px;
  border: 1px solid var(--border, #eee);
  background: var(--bg-elevated, #fff);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.app-sider :deep(.ant-layout-sider-children) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sider-top {
  height: 56px;
  padding: 0 12px 0 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sider-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.collapse-trigger {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid var(--border, #eee);
  background: var(--bg, #fff);
  color: var(--muted, #666);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.collapse-trigger:hover {
  color: var(--text, #222);
  border-color: var(--text, #222);
}

.sider-menu-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 6px 10px 12px;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-row,
.single-row {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--muted, #666);
  display: flex;
  align-items: center;
  transition: all 0.2s ease;
}

.nav-row.active,
.single-row.active {
  background: rgba(var(--accent-rgb), 0.12);
  border-color: rgba(var(--accent-rgb), 0.35);
  color: var(--text, #222);
}

.nav-main,
.single-row {
  min-height: 42px;
  flex: 1;
  border: none;
  background: transparent;
  color: inherit;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  cursor: pointer;
  text-align: left;
}

.single-row {
  border: 1px solid transparent;
}

.nav-main:hover,
.nav-toggle:hover,
.single-row:hover {
  color: var(--text, #222);
}

.nav-toggle {
  width: 34px;
  height: 34px;
  margin-right: 6px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: inherit;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.nav-icon {
  font-size: 16px;
  flex: 0 0 auto;
}

.nav-label {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-children {
  margin-left: 18px;
  padding-left: 12px;
  border-left: 1px solid var(--border, #eee);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.child-create-btn,
.child-link,
.child-more-btn {
  min-height: 36px;
  border-radius: 10px;
  border: 1px solid var(--border, #eee);
  background: var(--bg, #fff);
  color: var(--muted, #666);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 10px;
  cursor: pointer;
}

.child-link {
  justify-content: flex-start;
  padding: 0 12px;
}

.child-link.active {
  border-color: rgba(var(--accent-rgb), 0.45);
  background: rgba(var(--accent-rgb), 0.14);
  color: var(--text, #222);
}

.child-create-btn:hover,
.child-link:hover,
.child-more-btn:hover {
  color: var(--text, #222);
  border-color: var(--text, #222);
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  width: 100%;
  border: 1px solid var(--border, #eee);
  background: var(--bg, #fff);
  border-radius: 12px;
  padding: 10px;
  color: var(--text, #222);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  text-align: left;
}

.session-item.active {
  border-color: rgba(var(--accent-rgb), 0.45);
  background: rgba(var(--accent-rgb), 0.14);
}

.session-main {
  min-width: 0;
  flex: 1;
}

.session-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.session-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted, #666);
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.session-action-btn {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid var(--border, #eee);
  background: transparent;
  color: var(--muted, #666);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.session-action-btn:hover {
  color: var(--text, #222);
}

.session-action-btn.danger:hover {
  color: #ef4444;
}

.child-empty {
  min-height: 72px;
  border: 1px dashed var(--border, #eee);
  border-radius: 12px;
  color: var(--muted, #666);
  display: grid;
  place-items: center;
  padding: 10px;
}

.sider-bottom {
  margin-top: auto;
  padding: 12px 12px 16px;
  border-top: 1px solid var(--border, #eee);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.bottom-item {
  width: 100%;
  display: flex;
}

.theme-switch-btn {
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid var(--border, #eee);
  background: var(--bg, #fff);
  color: var(--muted, #666);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.theme-switch-btn:hover {
  color: var(--text, #222);
  border-color: var(--text, #222);
}

.theme-switch-icon {
  flex: 0 0 auto;
}

.theme-switch-text {
  width: 100%;
  text-align: center;
  pointer-events: none;
}

.theme-switch-arrow {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
}

.theme-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.app-header__avatar {
  border: 1px solid var(--border, #eee);
}

.profile-btn {
  width: 100%;
  height: 38px;
  border-radius: 10px;
  border: 1px solid var(--border, #eee);
  background: var(--bg, #fff);
  color: var(--text, #222);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  padding: 2px 8px;
}

.app-sider.ant-layout-sider-collapsed .sider-menu-wrap {
  padding-left: 6px;
  padding-right: 6px;
}

.app-sider.ant-layout-sider-collapsed .nav-row,
.app-sider.ant-layout-sider-collapsed .single-row {
  justify-content: center;
}

.app-sider.ant-layout-sider-collapsed .nav-main,
.app-sider.ant-layout-sider-collapsed .single-row {
  justify-content: center;
  padding: 0;
}

.app-sider.ant-layout-sider-collapsed .bottom-item {
  justify-content: center;
}

.app-sider.ant-layout-sider-collapsed .theme-switch-btn {
  width: 38px;
  padding: 0;
}

.app-sider.ant-layout-sider-collapsed .profile-btn {
  width: 38px;
  padding: 0;
  justify-content: center;
}

.profile-btn:hover {
  border-color: var(--text, #222);
}

.profile-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-content {
  min-height: 0;
  overflow: hidden;
}

.app-content__inner {
  height: 100%;
  overflow: hidden;
}

@media (max-width: 960px) {
  .app-layout {
    padding: 8px;
    gap: 8px;
  }
}
</style>
