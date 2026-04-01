export type RealtimeConsoleSource = "backend" | "rag";

export interface RealtimeConsoleEntry {
  type?: "chunk" | "heartbeat";
  source: RealtimeConsoleSource;
  raw: string | null;
  timestamp: string;
}

export interface RealtimeConsoleConnection {
  stop: () => void;
}

function getAuthorizationHeader() {
  const token = localStorage.getItem("auth_token");
  const tokenType = localStorage.getItem("auth_token_type") || "Bearer";
  return token ? `${tokenType} ${token}` : "";
}

export function connectRealtimeConsoleStream(
  url: string,
  onMessage: (entry: RealtimeConsoleEntry) => void,
  onStatus?: (connected: boolean, reason?: string) => void
): RealtimeConsoleConnection {
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
            const payload = JSON.parse(trimmed) as RealtimeConsoleEntry;
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
