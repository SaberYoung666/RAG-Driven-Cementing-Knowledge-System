import { http } from "./http";
import type { ApiResponse, AuthTokenData, AuthUser, UsernameCheckData } from "@/types";

export interface AuthPayload {
    username: string;
    password: string;
}

export async function checkUsername(username: string) {
    const { data } = await http.get<ApiResponse<UsernameCheckData>>("/api/v1/auth/check-username", {
        params: { username }
    });
    return data;
}

export async function registerByUsername(payload: AuthPayload) {
    const { data } = await http.post<ApiResponse<AuthTokenData>>("/api/v1/auth/register", payload);
    return data;
}

export async function loginByUsername(payload: AuthPayload) {
    const { data } = await http.post<ApiResponse<AuthTokenData>>("/api/v1/auth/login", payload);
    return data;
}

export async function getCurrentUser() {
    const { data } = await http.get<ApiResponse<AuthUser>>("/api/v1/auth/me");
    return data;
}
