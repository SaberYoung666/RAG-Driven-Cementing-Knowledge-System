import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import MainLayout from "@/layouts/MainLayout.vue";
import pinia from "@/stores";
import { useAuthStore } from "@/stores/auth";

const routes: RouteRecordRaw[] = [
    {
        path: "/auth",
        component: () => import("@/pages/AuthPage.vue"),
        meta: { guestOnly: true }
    },
    {
        path: "/",
        component: MainLayout,
        meta: { requiresAuth: true },
        children: [
            { path: "", redirect: "/chat" },
            { path: "chat", component: () => import("@/pages/ChatPage.vue") },
            { path: "profile", component: () => import("@/pages/ProfilePage.vue") },
            { path: "admin/docs", component: () => import("@/pages/DocsPage.vue") },
            { path: "admin/logs", redirect: "/admin/logs/overview" },
            { path: "admin/logs/overview", component: () => import("@/pages/LogsOverviewPage.vue") },
            { path: "admin/logs/list", component: () => import("@/pages/LogsPage.vue") },
            { path: "admin/eval", component: () => import("@/pages/EvalPage.vue") },
            { path: "admin/console", component: () => import("@/pages/ConsolePage.vue"), meta: { requiresAdmin: true } }
        ]
    }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia);
    const requiresAuth = to.matched.some((record) => record.meta.requiresAuth);
    const guestOnly = to.matched.some((record) => record.meta.guestOnly);
    const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin);

    if (!authStore.initialized) {
        await authStore.initAuthState();
    }

    if (requiresAuth && !authStore.isLoggedIn) {
        return {
            path: "/auth",
            query: to.fullPath && to.fullPath !== "/" ? { redirect: to.fullPath } : undefined
        };
    }

    if (guestOnly && authStore.isLoggedIn) {
        const redirect = typeof to.query.redirect === "string" ? to.query.redirect : "/chat";
        return redirect;
    }

    if (requiresAdmin && authStore.user?.role !== "ADMIN") {
        return "/chat";
    }

    return true;
});

export default router;
