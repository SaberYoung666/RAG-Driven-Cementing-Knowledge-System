import { http } from "./http";
import type { ApiResponse, ChatRequest, ChatResponseData, ChatSessionDetail, ChatSessionItem, PageResult } from "@/types";

export async function postChat(req: ChatRequest) {
    const { data } = await http.post<ApiResponse<ChatResponseData>>("/api/v1/chat", req);
    return data;
}

export async function createSession(body?: { title?: string }) {
    const { data } = await http.post<ApiResponse<{ sessionId: string; title?: string }>>("/api/v1/chat/sessions", body ?? {});
    return data;
}

export async function listSessions(params: { page?: number; pageSize?: number }) {
    const { data } = await http.get<ApiResponse<PageResult<ChatSessionItem>>>("/api/v1/chat/sessions", { params });
    return data;
}

export async function getSessionDetail(sessionId: string) {
    const { data } = await http.get<ApiResponse<ChatSessionDetail>>(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}`);
    return data;
}

export async function updateSessionTitle(sessionId: string, title: string) {
    const { data } = await http.patch<ApiResponse<{ updated: boolean }>>(
        `/api/v1/chat/sessions/${encodeURIComponent(sessionId)}`,
        { title }
    );
    return data;
}

export async function deleteSession(sessionId: string) {
    const { data } = await http.delete<ApiResponse<{ deleted: boolean }>>(`/api/v1/chat/sessions/${encodeURIComponent(sessionId)}`);
    return data;
}
