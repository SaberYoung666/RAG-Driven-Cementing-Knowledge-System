import type { AuthUser } from "@/types";

export type RealtimeLogSource = "frontend" | "backend" | "rag";
export type RealtimeLogLevel = "DEBUG" | "INFO" | "WARN" | "ERROR";

export interface RealtimeLogEntry {
  type?: "log" | "heartbeat";
  source: RealtimeLogSource;
  level: RealtimeLogLevel | string;
  logger?: string | null;
  thread?: string | null;
  message: string;
  timestamp: string;
  details?: string | null;
}

export interface RealtimeLogConnection {
  stop: () => void;
}

function getAuthorizationHeader() {
  const token = localStorage.getItem("auth_token");
  const tokenType = localStorage.getItem("auth_token_type") || "Bearer";
  return token ? `${tokenType} ${token}` : "";
}

export function connectRealtimeLogStream(
  url: string,
  onMessage: (entry: RealtimeLogEntry) => void,
  onStatus?: (connected: boolean, reason?: string) => void
): RealtimeLogConnection {
  const controller = new AbortController();
  let stopped = false;

  const loop = async () => {
    while (!stopped) {
      try {
        const response = await fetch(url, {
          method: "GET",
          headers: {
            Authorization: getAuthorizationHeader(),
            Accept: "application/x-ndjson",
          },
          signal: controller.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`stream request failed: ${response.status}`);
        }

        onStatus?.(true);
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (!stopped) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed) continue;
            const payload = JSON.parse(trimmed) as RealtimeLogEntry;
            if (payload.type === "heartbeat") continue;
            onMessage(payload);
          }
        }
        onStatus?.(false, "stream closed");
      } catch (error) {
        if (stopped || controller.signal.aborted) break;
        const reason = error instanceof Error ? error.message : "stream connection failed";
        onStatus?.(false, reason);
        await new Promise((resolve) => window.setTimeout(resolve, 2000));
        continue;
      }
    }
  };

  void loop();

  return {
    stop() {
      stopped = true;
      controller.abort();
      onStatus?.(false, "stopped");
    },
  };
}

export function isAdmin(user?: AuthUser | null) {
  return user?.role === "ADMIN";
}
