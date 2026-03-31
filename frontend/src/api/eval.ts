import { http } from "./http";
import type { ApiResponse } from "@/types";

export async function runEval(body: {
    datasetId?: string;
    modes?: string[];
    rerankOptions?: boolean[];
    topK?: number;
    candidateK?: number;
    alpha?: number;
}) {
    const { data } = await http.post<ApiResponse<{ jobId: string; accepted: boolean }>>("/api/v1/eval/run", body);
    return data;
}
