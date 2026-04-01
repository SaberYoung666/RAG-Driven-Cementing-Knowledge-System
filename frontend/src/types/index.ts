export type ChatMode = "dense" | "hybrid";

export interface Citation {
    evidenceId: string;  // "证据1"
    score: number;
    docId?: string;
    chunkId: string;
    source?: string;
    page?: number | string;
    section?: string;
}

export interface RetrievedChunk {
    rank?: number;
    chunkId: string;
    score: number;
    text?: string;
    source?: string;
    page?: number | string;
    section?: string;
    metadata?: Record<string, any>;
}

export interface ChatRequest {
    query: string;
    sessionId?: string;
    topK?: number;
    topR?: number;
    mode?: ChatMode;
    alpha?: number;
    rerankOn?: boolean;
    candidateK?: number;
    minScore?: number;
    useLlm?: boolean;
    constraints?: Record<string, any>;
}

export interface ChatResponseData {
    answer: string;
    refused: boolean;
    citations: Citation[];
    retrieved?: RetrievedChunk[];
    latencyMs?: number;
    confidenceFlag?: "HIGH" | "MID" | "LOW";
}

export interface ApiResponse<T> {
    code: number;
    message: string;
    data: T;
    traceId?: string;
    ts?: string;
}

export interface DocItem {
    docId: string;
    title: string;
    source?: string;
    uploadTime?: string;
    version?: string;
    hash?: string;
    status?: "READY" | "PROCESSING" | "FAILED";
    chunkCount?: number;
}

export interface DocProcessInfo {
    docId: string;
    status?: "READY" | "PROCESSING" | "FAILED" | string;
    progress?: number;
    stage?: string;
    message?: string;
    chunkCount?: number;
    updatedAt?: string;
    detail?: string;
}

export interface PageResult<T> {
    total: number;
    items: T[];
    page?: number;
    size?: number;
}

export interface AuthTokenData {
    token: string;
    tokenType: string;
    expiresIn: number;
}

export interface UsernameCheckData {
    username: string;
    available: boolean;
}

export interface AuthUser {
    id: number;
    username: string;
    role: "USER" | "ADMIN";
    createdAt: string;
}

export interface ChatSessionItem {
    sessionId: string;
    title: string;
    createdAt?: string;
    updatedAt?: string;
    lastMessageAt?: string;
}

export interface ChatSessionMessage {
    id?: string;
    role: "user" | "assistant";
    content: string;
    createdAt?: string;
    citations?: Citation[];
}

export interface ChatSessionDetail {
    sessionId: string;
    title: string;
    createdAt?: string;
    updatedAt?: string;
    messages: ChatSessionMessage[];
}
