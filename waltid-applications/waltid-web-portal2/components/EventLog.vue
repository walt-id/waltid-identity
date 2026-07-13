<script setup lang="ts">
import type { SseEvent } from "~/composables/useSSE";

const props = defineProps<{
  events: SseEvent[];
  status: string | null;
  isTerminal: boolean;
}>();

const container = ref<HTMLElement | null>(null);

watch(
  () => props.events.length,
  () => {
    nextTick(() => {
      if (container.value)
        container.value.scrollTop = container.value.scrollHeight;
    });
  },
);

function formatTime(iso: string) {
  return iso.slice(11, 23);
}

function eventLabel(ev: SseEvent): string {
  if (!ev.parsed || typeof ev.parsed !== "object") return ev.raw;
  const obj = ev.parsed as Record<string, unknown>;
  const parts: string[] = [];
  if (obj.type) parts.push(String(obj.type));
  if (obj.status) parts.push(String(obj.status));
  if (obj.event) parts.push(String(obj.event));
  return parts.length > 0 ? parts.join(" · ") : ev.raw;
}

function eventColor(ev: SseEvent): string {
  if (!ev.parsed || typeof ev.parsed !== "object") return "";
  const s = (ev.parsed as Record<string, unknown>).status;
  if (typeof s !== "string") return "";
  const upper = s.toUpperCase();
  if (upper === "SUCCESSFUL") return "text-green-400";
  if (["FAILED", "UNSUCCESSFUL", "REJECTED", "EXPIRED"].includes(upper))
    return "text-red-400";
  return "text-blue-400";
}
</script>

<template>
  <div class="grid gap-2">
    <div class="flex items-center justify-between">
      <span class="form-label !mb-0">Result Log</span>
      <span
        v-if="status"
        class="text-xs font-semibold"
        :class="
          status === 'SUCCESSFUL'
            ? 'text-green-600'
            : isTerminal
              ? 'text-red-600'
              : 'text-blue-600'
        "
      >
        {{ status }}
      </span>
    </div>

    <div ref="container" class="log-box min-h-[160px] max-h-[320px]">
      <template v-if="events.length === 0"
        ><span class="text-[--color-text-muted]"
          >(waiting for events…)</span
        ></template
      >
      <div v-for="(ev, i) in events" :key="i">
        <span class="text-slate-500">{{ formatTime(ev.timestamp) }}</span>
        <span :class="eventColor(ev)"> {{ eventLabel(ev) }}</span>
      </div>
    </div>

    <div
      v-if="isTerminal && status === 'SUCCESSFUL'"
      class="p-3 rounded-lg bg-green-50 border border-green-200 text-sm text-green-800 font-medium"
    >
      Completed successfully
    </div>
    <div
      v-else-if="isTerminal"
      class="p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800 font-medium"
    >
      {{ status }}
    </div>
  </div>
</template>
