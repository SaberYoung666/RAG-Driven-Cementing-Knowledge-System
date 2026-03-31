<template>
  <div class="auth-page">
    <div class="auth-bg"></div>
    <a-card class="auth-card" :bordered="false">
      <template #title>
        <div class="auth-header">
          <h2>账户中心</h2>
        </div>
      </template>

      <a-tabs v-model:activeKey="activeTab" centered>
        <a-tab-pane key="login" tab="登录">
          <a-form layout="vertical" :model="loginForm" @finish="handleLogin">
            <a-form-item label="用户名" name="username" :rules="usernameRules">
              <a-input v-model:value="loginForm.username" size="large" placeholder="请输入用户名" />
            </a-form-item>
            <a-form-item label="密码" name="password" :rules="passwordRules">
              <a-input-password v-model:value="loginForm.password" size="large" placeholder="请输入密码" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit" block size="large" :loading="loginLoading">登录</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="register" tab="注册">
          <a-form layout="vertical" :model="registerForm" @finish="handleRegister">
            <a-form-item
              label="用户名"
              name="username"
              :rules="usernameRules"
              :validate-status="registerUsernameValidateStatus"
              :help="registerUsernameHelp"
            >
              <a-input
                v-model:value="registerForm.username"
                size="large"
                placeholder="请输入用户名"
                @blur="handleRegisterUsernameBlur"
                @input="resetUsernameCheckState"
              />
            </a-form-item>
            <a-form-item label="密码" name="password" :rules="passwordRules">
              <a-input-password v-model:value="registerForm.password" size="large" placeholder="请输入密码" />
            </a-form-item>
            <a-form-item label="确认密码" name="confirmPassword" :rules="confirmPasswordRules">
              <a-input-password
                v-model:value="registerForm.confirmPassword"
                size="large"
                placeholder="请再次输入密码"
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit" block size="large" :loading="registerLoading">
                创建账号
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "ant-design-vue";
import type { Rule } from "ant-design-vue/es/form";
import { checkUsername, loginByUsername, registerByUsername } from "@/api/auth";
import { useAuthStore } from "@/stores/auth";

const activeTab = ref("login");
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const loginLoading = ref(false);
const registerLoading = ref(false);
const usernameChecking = ref(false);
const registerUsernameAvailable = ref<boolean | null>(null);
const registerUsernameMessage = ref("");

const loginForm = ref({
  username: "",
  password: ""
});

const registerForm = ref({
  username: "",
  password: "",
  confirmPassword: ""
});

const passwordRules: Rule[] = [
  { required: true, message: "请输入密码" },
  { min: 6, message: "密码至少 6 位" },
  { max: 64, message: "密码最多 64 位" }
];

const usernameRules: Rule[] = [
  { required: true, message: "请输入用户名" },
  { min: 2, message: "用户名至少 2 个字符" }
];

const confirmPasswordRules: Rule[] = [
  { required: true, message: "请确认密码" },
  {
    validator: async (_rule: Rule, value: string) => {
      if (!value || value === registerForm.value.password) {
        return Promise.resolve();
      }
      return Promise.reject("两次输入的密码不一致");
    }
  }
];

const registerUsernameValidateStatus = computed(() => {
  if (usernameChecking.value) return "validating";
  if (registerUsernameAvailable.value === true) return "success";
  if (registerUsernameAvailable.value === false) return "error";
  return "";
});

const registerUsernameHelp = computed(() => {
  if (usernameChecking.value) return "正在检查用户名...";
  return registerUsernameMessage.value;
});

const redirectTarget = computed(() => {
  const redirect = route.query.redirect;
  return typeof redirect === "string" && redirect.startsWith("/") ? redirect : "/chat";
});

function resetUsernameCheckState() {
  registerUsernameAvailable.value = null;
  registerUsernameMessage.value = "";
}

async function ensureRegisterUsernameAvailable(username: string) {
  const cleanUsername = username.trim();
  if (!cleanUsername) {
    resetUsernameCheckState();
    return false;
  }

  usernameChecking.value = true;
  try {
    const resp = await checkUsername(cleanUsername);
    if (resp.code !== 0) {
      registerUsernameAvailable.value = null;
      registerUsernameMessage.value = resp.message || "用户名检查失败";
      return false;
    }
    registerUsernameAvailable.value = !!resp.data?.available;
    registerUsernameMessage.value = resp.data?.available ? "用户名可用" : "用户名已存在";
    return !!resp.data?.available;
  } catch {
    registerUsernameAvailable.value = null;
    registerUsernameMessage.value = "用户名检查失败，请稍后重试";
    return false;
  } finally {
    usernameChecking.value = false;
  }
}

async function handleRegisterUsernameBlur() {
  const username = registerForm.value.username.trim();
  if (!username) {
    resetUsernameCheckState();
    return;
  }
  await ensureRegisterUsernameAvailable(username);
}

async function handleLogin() {
  loginLoading.value = true;
  try {
    const payload = {
      username: loginForm.value.username.trim(),
      password: loginForm.value.password
    };
    const resp = await loginByUsername(payload);
    if (resp.code !== 0 || !resp.data?.token) {
      message.error(resp.message || "登录失败");
      return;
    }
    authStore.persistToken(resp.data);
    await authStore.fetchCurrentUser();
    message.success("登录成功");
    await router.push(redirectTarget.value);
  } catch {
    // 错误提示由 http 响应拦截器统一处理
  } finally {
    loginLoading.value = false;
  }
}

async function handleRegister() {
  const payload = {
    username: registerForm.value.username.trim(),
    password: registerForm.value.password
  };

  const available =
    registerUsernameAvailable.value === true
      ? true
      : await ensureRegisterUsernameAvailable(payload.username);

  if (!available) {
    message.error("用户名已被占用，请更换用户名");
    return;
  }

  registerLoading.value = true;
  try {
    const resp = await registerByUsername(payload);
    if (resp.code !== 0 || !resp.data?.token) {
      message.error(resp.message || "注册失败");
      return;
    }
    authStore.persistToken(resp.data);
    await authStore.fetchCurrentUser();
    message.success("注册成功");
    await router.push(redirectTarget.value);
  } catch {
    // 错误提示由 http 响应拦截器统一处理
  } finally {
    registerLoading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 20% 20%, rgba(var(--accent-rgb), 0.18), transparent 45%),
    radial-gradient(circle at 80% 80%, rgba(var(--accent-rgb), 0.12), transparent 50%),
    linear-gradient(135deg, var(--bg-soft), var(--bg));
  z-index: 0;
}

.auth-card {
  width: 100%;
  max-width: 460px;
  border-radius: 16px;
  box-shadow: var(--shadow);
  z-index: 1;
}

.auth-header h2 {
  margin: 0;
  color: var(--text);
}

.auth-header span {
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 520px) {
  .auth-page {
    padding: 16px;
  }

  .auth-card {
    border-radius: 12px;
  }
}
</style>
