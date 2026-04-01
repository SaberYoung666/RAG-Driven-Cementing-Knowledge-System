import type { RealtimeLogEntry } from "@/api/realtimeLogs";

type ConsoleLevel = "DEBUG" | "INFO" | "WARN" | "ERROR";
type Listener = (entry: RealtimeLogEntry) => void;

const listeners = new Set<Listener>();
const entries: RealtimeLogEntry[] = [];
const ENTRY_LIMIT = 500;
let installed = false;

function pushEntry(entry: RealtimeLogEntry) {
  entries.push(entry);
  if (entries.length > ENTRY_LIMIT) {
    entries.splice(0, entries.length - ENTRY_LIMIT);
  }
  listeners.forEach((listener) => listener(entry));
}

function serializeValue(value: unknown, seen = new WeakSet<object>()): string {
  if (typeof value === "string") return value;
  if (value instanceof Error) return value.stack || value.message;
  if (value == null) return String(value);
  if (typeof value === "object") {
    try {
      const raw = String(value);
      if (raw !== "[object Object]" && raw !== "[object Array]") {
        return raw;
      }
    } catch {
      // fall through to JSON serialization
    }
    try {
      return JSON.stringify(
        value,
        (_key, currentValue) => {
          if (typeof currentValue === "object" && currentValue !== null) {
            if (seen.has(currentValue)) return "[Circular]";
            seen.add(currentValue);
          }
          return currentValue;
        }
      );
    } catch {
      return Object.prototype.toString.call(value);
    }
  }
  return String(value);
}

function emitConsole(level: ConsoleLevel, args: unknown[]) {
  pushEntry({
    type: "log",
    source: "frontend",
    level,
    logger: "console",
    thread: "browser",
    message: args.map((item) => serializeValue(item)).join(" "),
    timestamp: new Date().toISOString(),
    details: null,
  });
}

export function installConsoleCapture() {
  if (installed) return;
  installed = true;
  const consoleObject = console as unknown as Record<string, (...args: unknown[]) => void>;

  const methods: Array<[keyof Console, ConsoleLevel]> = [
    ["debug", "DEBUG"],
    ["log", "INFO"],
    ["info", "INFO"],
    ["warn", "WARN"],
    ["error", "ERROR"],
  ];

  for (const [method, level] of methods) {
    const original = (consoleObject[method as string] || console.log.bind(console)) as (...args: unknown[]) => void;
    consoleObject[method as string] = (...args: unknown[]) => {
      emitConsole(level, args);
      original(...args);
    };
  }

  window.addEventListener("error", (event) => {
    pushEntry({
      type: "log",
      source: "frontend",
      level: "ERROR",
      logger: "window.onerror",
      thread: "browser",
      message: event.message || "Unhandled error",
      timestamp: new Date().toISOString(),
      details: event.error instanceof Error ? event.error.stack || event.error.message : null,
    });
  });

  window.addEventListener("unhandledrejection", (event) => {
    pushEntry({
      type: "log",
      source: "frontend",
      level: "ERROR",
      logger: "window.unhandledrejection",
      thread: "browser",
      message: "Unhandled promise rejection",
      timestamp: new Date().toISOString(),
      details: serializeValue(event.reason),
    });
  });
}

export function subscribeConsoleLogs(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getConsoleLogs() {
  return [...entries];
}
