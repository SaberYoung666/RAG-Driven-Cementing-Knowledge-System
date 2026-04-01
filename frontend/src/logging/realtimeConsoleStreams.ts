import { connectRealtimeConsoleStream, type RealtimeConsoleEntry } from "@/api/realtimeConsole";

type StreamListener = (entry: RealtimeConsoleEntry) => void;
type StatusListener = (status: RealtimeConsoleStreamStatus) => void;

export interface RealtimeConsoleStreamStatus {
  backendConnected: boolean;
  backendReason?: string;
  ragConnected: boolean;
  ragReason?: string;
}

const listeners = new Set<StreamListener>();
const statusListeners = new Set<StatusListener>();
const entries: RealtimeConsoleEntry[] = [];
const ENTRY_LIMIT = 1000;
const status: RealtimeConsoleStreamStatus = {
  backendConnected: false,
  ragConnected: false,
};

let started = false;

function pushEntry(entry: RealtimeConsoleEntry) {
  entries.push(entry);
  if (entries.length > ENTRY_LIMIT) {
    entries.splice(0, entries.length - ENTRY_LIMIT);
  }
  listeners.forEach((listener) => listener(entry));
}

function emitStatus() {
  const snapshot = { ...status };
  statusListeners.forEach((listener) => listener(snapshot));
}

function updateStatus(source: "backend" | "rag", connected: boolean, reason?: string) {
  if (source === "backend") {
    status.backendConnected = connected;
    status.backendReason = reason;
  } else {
    status.ragConnected = connected;
    status.ragReason = reason;
  }
  emitStatus();
}

export function ensureRealtimeConsoleStreamsStarted() {
  if (started) return;
  started = true;

  connectRealtimeConsoleStream(
    "/api/v1/realtime-console/backend",
    (entry) => {
      pushEntry(entry);
    },
    (connected, reason) => {
      updateStatus("backend", connected, reason);
    }
  );

  connectRealtimeConsoleStream(
    "/api/v1/realtime-console/rag",
    (entry) => {
      pushEntry(entry);
    },
    (connected, reason) => {
      updateStatus("rag", connected, reason);
    }
  );
}

export function subscribeRealtimeConsoleStreamLogs(listener: StreamListener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getRealtimeConsoleStreamLogs() {
  return [...entries];
}

export function subscribeRealtimeConsoleStreamStatus(listener: StatusListener) {
  statusListeners.add(listener);
  return () => {
    statusListeners.delete(listener);
  };
}

export function getRealtimeConsoleStreamStatus() {
  return { ...status };
}
