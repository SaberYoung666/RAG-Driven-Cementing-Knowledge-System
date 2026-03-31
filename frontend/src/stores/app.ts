import { defineStore } from "pinia";

export type ThemeMode = "light" | "dark" | "system";
type ResolvedTheme = "light" | "dark";

const THEME_MODE_KEY = "theme-mode";

export const useAppStore = defineStore("app", {
    state: () => ({
        sessionId: "",
        themeMode: "system" as ThemeMode,
        resolvedTheme: "light" as ResolvedTheme,
    }),
    actions: {
        ensureSession() {
            if (!this.sessionId) {
                this.sessionId = crypto.randomUUID();
            }
            return this.sessionId;
        },
        getSystemTheme(): ResolvedTheme {
            return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
        },
        applyTheme(mode: ThemeMode) {
            const resolved = mode === "system" ? this.getSystemTheme() : mode;
            this.resolvedTheme = resolved;
            document.documentElement.setAttribute("data-theme", resolved);
        },
        setThemeMode(mode: ThemeMode, persist = true) {
            this.themeMode = mode;
            this.applyTheme(mode);
            if (persist) localStorage.setItem(THEME_MODE_KEY, mode);
        },
        initThemeMode() {
            const saved = (localStorage.getItem(THEME_MODE_KEY) as ThemeMode) || "system";
            this.setThemeMode(saved, false);

            const mql = window.matchMedia?.("(prefers-color-scheme: dark)");
            mql?.addEventListener?.("change", () => {
                if (this.themeMode === "system") this.applyTheme("system");
            });
        },
    },
});
