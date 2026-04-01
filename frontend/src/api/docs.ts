import { http } from "./http";
import type { ApiResponse, DocItem, DocProcessInfo, PageResult, ProcessStartResult } from "@/types";

export async function listDocs(params: { page?: number; pageSize?: number; keyword?: string; status?: string }) {
    const { data } = await http.get<ApiResponse<PageResult<DocItem>>>("/api/v1/docs", { params });
    return data;
}

export async function deleteDoc(docId: string) {
    const { data } = await http.delete<ApiResponse<{ deleted: boolean }>>(`/api/v1/docs/${encodeURIComponent(docId)}`);
    return data;
}

export async function ingestFile(file: File, overwrite = false) {
    const form = new FormData();
    form.append("file", file);
    form.append("overwrite", String(overwrite));
    const { data } = await http.post<ApiResponse<{ jobId: string; accepted: boolean; message?: string }>>(
        "/api/v1/ingest",
        form,
        { headers: { "Content-Type": "multipart/form-data" } }
    );
    return data;
}

export async function processDoc(docId: string) {
    const { data } = await http.post<ApiResponse<ProcessStartResult>>(
        `/api/v1/docs/${encodeURIComponent(docId)}/process`
    );
    return data;
}

export async function getDocProcessInfo(docId: string) {
    const { data } = await http.get<ApiResponse<DocProcessInfo>>(
        `/api/v1/docs/${encodeURIComponent(docId)}/process`
    );
    return data;
}
