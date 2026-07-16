<script setup lang="ts">
import type { SseEvent } from "~/composables/useSSE";

type JsonRecord = Record<string, unknown>;

interface PolicyEntry {
  key: string;
  label: string;
  description: string | null;
  success: boolean;
  executionTime: string | null;
  error: string | null;
  details: unknown | null;
}

interface PolicyGroup {
  key: string;
  label: string;
  passed: number;
  total: number;
  entries: PolicyEntry[];
}

interface PolicySection {
  key: string;
  title: string;
  groups: PolicyGroup[];
}

const props = defineProps<{
  events: SseEvent[];
}>();

function isRecord(value: unknown): value is JsonRecord {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function asString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function asRecordArray(value: unknown): JsonRecord[] {
  return Array.isArray(value) ? value.filter(isRecord) : [];
}

function formatPolicyName(value: string): string {
  return value
    .replace(/[/_-]/g, " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
    .trim();
}

function formatExecutionTime(value: unknown): string | null {
  const raw = asString(value);
  if (!raw) return null;

  const match = raw.match(/^PT(\d+\.?\d*)S$/);
  if (!match) return raw;

  const seconds = Number.parseFloat(match[1] ?? "0");
  return seconds < 1
    ? `${Math.round(seconds * 1000)}ms`
    : `${seconds.toFixed(2)}s`;
}

function stringifyError(value: unknown): string | null {
  if (!value) return null;
  if (typeof value === "string") return value;
  if (Array.isArray(value)) {
    const messages = value
      .map((item) => (typeof item === "string" ? item : JSON.stringify(item)))
      .filter(Boolean);
    return messages.length > 0 ? messages.join(", ") : null;
  }
  return JSON.stringify(value);
}

function hasDetails(value: unknown): boolean {
  if (!value) return false;
  if (Array.isArray(value)) return value.length > 0;
  if (isRecord(value)) return Object.keys(value).length > 0;
  return true;
}

function policyEntryFromResult(key: string, result: JsonRecord): PolicyEntry {
  const executed = isRecord(result.policy_executed)
    ? result.policy_executed
    : null;
  const policy = isRecord(result.policy) ? result.policy : null;
  const policyId =
    asString(executed?.id) ??
    asString(policy?.id) ??
    asString(policy?.policy) ??
    key;

  return {
    key,
    label: formatPolicyName(policyId),
    description: asString(executed?.description),
    success: result.success === true,
    executionTime: formatExecutionTime(result.execution_time),
    error: stringifyError(result.errors) ?? stringifyError(result.error),
    details: result.results ?? result.result ?? null,
  };
}

function makeGroup(
  key: string,
  label: string,
  entries: PolicyEntry[],
): PolicyGroup {
  return {
    key,
    label,
    passed: entries.filter((entry) => entry.success).length,
    total: entries.length,
    entries,
  };
}

function groupVcPolicies(policies: JsonRecord[]): PolicyGroup[] {
  const grouped = new Map<string, JsonRecord[]>();

  for (const policy of policies) {
    const queryId = asString(policy.query_id) ?? "unknown";
    const credentialIndex =
      typeof policy.credential_index === "number" ? policy.credential_index : 0;
    const key = `${queryId}:${credentialIndex}`;
    grouped.set(key, [...(grouped.get(key) ?? []), policy]);
  }

  const queryCounts = new Map<string, number>();
  for (const key of grouped.keys()) {
    const queryId = key.split(":")[0] ?? "unknown";
    queryCounts.set(queryId, (queryCounts.get(queryId) ?? 0) + 1);
  }

  return Array.from(grouped.entries()).map(([key, groupPolicies]) => {
    const [queryId = "unknown", credentialIndexRaw = "0"] = key.split(":");
    const credentialIndex = Number.parseInt(credentialIndexRaw, 10);
    const label =
      (queryCounts.get(queryId) ?? 0) > 1
        ? `${formatPolicyName(queryId)} #${credentialIndex + 1}`
        : formatPolicyName(queryId);

    return makeGroup(
      key,
      label,
      groupPolicies.map((policy, index) =>
        policyEntryFromResult(`${key}-${index}`, policy),
      ),
    );
  });
}

function groupSpecificVcPolicies(value: unknown): PolicyGroup[] {
  if (!isRecord(value)) return [];

  return Object.entries(value)
    .map(([queryId, policies]) =>
      makeGroup(
        queryId,
        formatPolicyName(queryId),
        asRecordArray(policies).map((policy, index) =>
          policyEntryFromResult(`${queryId}-${index}`, policy),
        ),
      ),
    )
    .filter((group) => group.total > 0);
}

function groupVpPolicies(value: unknown): PolicyGroup[] {
  if (!isRecord(value)) return [];

  return Object.entries(value)
    .map(([credentialId, policies]) => {
      if (!isRecord(policies)) {
        return makeGroup(credentialId, formatPolicyName(credentialId), []);
      }

      const entries = Object.entries(policies)
        .filter(([, result]) => isRecord(result))
        .map(([policyId, result]) =>
          policyEntryFromResult(policyId, result as JsonRecord),
        );

      return makeGroup(credentialId, formatPolicyName(credentialId), entries);
    })
    .filter((group) => group.total > 0);
}

function eventSession(event: SseEvent): JsonRecord | null {
  if (!isRecord(event.parsed)) return null;
  return isRecord(event.parsed.session) ? event.parsed.session : null;
}

const policyResults = computed<JsonRecord | null>(() => {
  const event = [...props.events].reverse().find((candidate) => {
    if (!isRecord(candidate.parsed)) return false;
    if (candidate.parsed.event !== "credential_policy_results_available") {
      return false;
    }

    const session = eventSession(candidate);
    return isRecord(session?.policy_results);
  });

  const session = event ? eventSession(event) : null;
  return isRecord(session?.policy_results) ? session.policy_results : null;
});

const sections = computed<PolicySection[]>(() => {
  const results = policyResults.value;
  if (!results) return [];

  return [
    {
      key: "vc",
      title: "Credential Policies",
      groups: groupVcPolicies(asRecordArray(results.vc_policies)),
    },
    {
      key: "specific-vc",
      title: "Credential-Specific Policies",
      groups: groupSpecificVcPolicies(results.specific_vc_policies),
    },
    {
      key: "vp",
      title: "Presentation Policies",
      groups: groupVpPolicies(results.vp_policies),
    },
  ].filter((section) => section.groups.length > 0);
});

const hasPolicyResults = computed(() => sections.value.length > 0);

function groupTone(group: PolicyGroup): string {
  return group.passed === group.total
    ? "border-green-200 bg-green-50/70 text-green-900 hover:bg-green-50"
    : "border-amber-200 bg-amber-50/70 text-amber-900 hover:bg-amber-50";
}

function entryTone(entry: PolicyEntry): string {
  return entry.success
    ? "border-green-200 bg-green-50/60 text-green-900"
    : "border-red-200 bg-red-50/60 text-red-900";
}
</script>

<template>
  <section v-if="hasPolicyResults" class="mt-5 grid gap-4">
    <div class="flex items-center justify-between gap-3">
      <div>
        <span class="form-label !mb-0">Policy Results</span>
        <p class="text-xs text-[--color-text-muted]">
          Shown from the credential_policy_results_available event.
        </p>
      </div>
    </div>

    <div v-for="section in sections" :key="section.key" class="grid gap-2">
      <h3 class="text-sm font-semibold text-[--color-text-secondary]">
        {{ section.title }}
      </h3>

      <details
        v-for="group in section.groups"
        :key="group.key"
        open
        class="group rounded-lg border border-[--color-border] bg-white"
      >
        <summary
          class="flex cursor-pointer list-none items-center justify-between gap-3 rounded-lg border p-3 transition-colors"
          :class="groupTone(group)"
        >
          <div>
            <div class="font-semibold">{{ group.label }}</div>
            <div class="text-xs opacity-80">
              {{ group.passed }}/{{ group.total }} policies passed
            </div>
          </div>
          <div class="text-xs font-semibold">Toggle</div>
        </summary>

        <div class="grid gap-2 p-3">
          <details
            v-for="entry in group.entries"
            :key="entry.key"
            class="group rounded-lg border bg-white"
            :class="entryTone(entry)"
          >
            <summary
              class="flex cursor-pointer list-none items-start justify-between gap-3 p-3"
            >
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span
                    class="rounded-full px-2 py-0.5 text-[11px] font-bold"
                    :class="
                      entry.success
                        ? 'bg-green-100 text-green-700'
                        : 'bg-red-100 text-red-700'
                    "
                  >
                    {{ entry.success ? "PASS" : "FAIL" }}
                  </span>
                  <span class="font-medium">{{ entry.label }}</span>
                </div>
                <p
                  v-if="entry.description"
                  class="mt-1 text-xs text-[--color-text-muted]"
                >
                  {{ entry.description }}
                </p>
              </div>
              <div
                class="flex shrink-0 items-center gap-2 text-xs text-[--color-text-muted]"
              >
                <span v-if="entry.executionTime">{{
                  entry.executionTime
                }}</span>
                <span>Toggle</span>
              </div>
            </summary>

            <div class="border-t border-white/70 p-3 text-sm">
              <div v-if="entry.error" class="mb-3 text-red-700">
                <span class="font-semibold">Error: </span>{{ entry.error }}
              </div>

              <div v-if="hasDetails(entry.details)">
                <span class="font-semibold text-[--color-text-secondary]">
                  Details
                </span>
                <pre class="log-box mt-2 max-h-48">{{
                  JSON.stringify(entry.details, null, 2)
                }}</pre>
              </div>

              <p
                v-if="!entry.error && !hasDetails(entry.details)"
                class="text-[--color-text-muted]"
              >
                No additional details available.
              </p>
            </div>
          </details>
        </div>
      </details>
    </div>
  </section>
</template>
