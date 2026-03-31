import { defineStore } from "pinia";
import { getCurrentUser } from "@/api/auth";
import type { AuthTokenData, AuthUser } from "@/types";

const TOKEN_KEY = "auth_token";
const TOKEN_TYPE_KEY = "auth_token_type";
const EXPIRES_IN_KEY = "auth_expires_in";
const SAVED_AT_KEY = "auth_saved_at";

export const useAuthStore = defineStore("auth", {
    state: () => ({
        initialized: false,
        loading: false,
        isLoggedIn: false,
        user: null as AuthUser | null
    }),
    actions: {
        hasToken() {
            return !!localStorage.getItem(TOKEN_KEY);
        },
        persistToken(tokenData: AuthTokenData) {
            localStorage.setItem(TOKEN_KEY, tokenData.token);
            localStorage.setItem(TOKEN_TYPE_KEY, tokenData.tokenType || "Bearer");
            localStorage.setItem(EXPIRES_IN_KEY, String(tokenData.expiresIn ?? 0));
            localStorage.setItem(SAVED_AT_KEY, new Date().toISOString());
        },
        clearToken() {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(TOKEN_TYPE_KEY);
            localStorage.removeItem(EXPIRES_IN_KEY);
            localStorage.removeItem(SAVED_AT_KEY);
        },
        logout() {
            this.clearToken();
            this.isLoggedIn = false;
            this.user = null;
        },
        async fetchCurrentUser() {
            const resp = await getCurrentUser();
            if (resp.code !== 0 || !resp.data) {
                throw new Error(resp.message || "获取用户信息失败");
            }
            this.user = resp.data;
            this.isLoggedIn = true;
            return resp.data;
        },
        async initAuthState() {
            if (this.initialized) return;
            this.initialized = true;

            if (!this.hasToken()) {
                this.isLoggedIn = false;
                this.user = null;
                return;
            }

            this.loading = true;
            try {
                await this.fetchCurrentUser();
            } catch {
                this.logout();
            } finally {
                this.loading = false;
            }
        }
    }
});
