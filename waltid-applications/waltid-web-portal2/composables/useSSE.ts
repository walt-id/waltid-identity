export interface SseEvent {
  timestamp: string;
  raw: string;
  parsed: unknown | null;
}

const TERMINAL_STATUSES = new Set([
  "SUCCESSFUL",
  "FAILED",
  "EXPIRED",
  "UNSUCCESSFUL",
  "REJECTED",
]);

function getStatus(parsed: unknown): string | null {
  if (!parsed || typeof parsed !== "object") return null;
  const obj = parsed as Record<string, { status?: string }>;
  if (typeof obj.session?.status === "string")
    return obj.session.status.toUpperCase();
  if (typeof (parsed as Record<string, unknown>).status === "string") {
    return String((parsed as Record<string, unknown>).status).toUpperCase();
  }
  return null;
}

export function useSSE() {
  const events = ref<SseEvent[]>([]);
  const status = ref<string | null>(null);
  const isTerminal = ref(false);
  let source: EventSource | null = null;

  function reset() {
    close();
    events.value = [];
    status.value = null;
    isTerminal.value = false;
  }

  function addEvent(payload: unknown) {
    const raw = typeof payload === "string" ? payload : JSON.stringify(payload);
    let parsed: unknown = null;
    try {
      parsed = JSON.parse(raw);
    } catch {
      /* non-JSON keep raw */
    }

    events.value.push({
      timestamp: new Date().toISOString(),
      raw,
      parsed,
    });

    const s = getStatus(parsed);
    if (s) {
      status.value = s;
      if (TERMINAL_STATUSES.has(s)) {
        isTerminal.value = true;
        close();
      }
    }
  }

  function setTerminal(nextStatus: string, payload?: unknown) {
    status.value = nextStatus.toUpperCase();
    isTerminal.value = true;
    if (payload !== undefined) addEvent(payload);
    close();
  }

  function open(url: string) {
      reset();

      source = new EventSource(url);

      source.onmessage = (e: MessageEvent) => {
        addEvent(e.data);

      source = new EventSource(url);

      source.onmessage = (e: MessageEvent) => {
        let parsed: unknown = null;
        try {
          parsed = JSON.parse(e.data);
        } catch {
          /* non-JSON keep raw */
        }

        events.value.push({
          timestamp: new Date().toISOString(),
          raw: e.data,
          parsed,
        });

        const s = getStatus(parsed);
        if (s) {
          status.value = s;
          if (TERMINAL_STATUSES.has(s)) {
            isTerminal.value = true;
            close();
          }
        }
      };

      source.onerror = () => {
        // EventSource auto-reconnects on transient errors; only close on terminal state
        if (isTerminal.value) close();
      };
    }
  }

  function close() {
    source?.close();
    source = null;
  }

  onUnmounted(close);

  return {
    events,
    status,
    isTerminal,
    open,
    close,
    reset,
    addEvent,
    setTerminal,
  };
}
