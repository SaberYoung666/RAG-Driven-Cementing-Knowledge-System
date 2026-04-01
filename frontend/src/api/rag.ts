import { http } from "./http";
import type { ApiResponse, RagStatus } from "@/types";

export async function getRagStatus() {
    const { data } = await http.get<ApiResponse<RagStatus>>("/api/v1/rag/status");
    return data;
}
