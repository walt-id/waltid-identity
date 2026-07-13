<script setup lang="ts">
import type { useVerifierSession } from "~/composables/useVerifierSession";

const props = defineProps<{
  session: ReturnType<typeof useVerifierSession>;
}>();

const config = useRuntimeConfig();
const walletUrl = config.public.walletUrl as string;

const verifiedClaims = computed(() => {
  if (
    !props.session.sse.isTerminal.value ||
    props.session.sse.status.value !== "SUCCESSFUL"
  )
    return null;
  const last = [...props.session.sse.events.value]
    .reverse()
    .find((ev) => ev.parsed);
  if (!last?.parsed || typeof last.parsed !== "object") return null;
  const obj = last.parsed as Record<string, unknown>;
  return obj.policyResults ?? obj.verifiedClaims ?? obj.credentials ?? null;
});
</script>

<template>
  <div class="grid gap-4">
    <template v-if="session.result.value">
      <QrDisplay
        v-if="
          session.result.value.flowType === 'qr' &&
          session.result.value.authorizationRequestUrl
        "
        :value="session.result.value.authorizationRequestUrl"
        :wallet-url="walletUrl"
        wallet-path="api/siop/initiatePresentation"
        :label="session.result.value.authorizationRequestUrl"
      />

      <div
        v-else
        class="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900"
      >
        <p class="font-semibold">Digital Credentials API session</p>
        <p class="mt-1">
          Session <code>{{ session.result.value.sessionId }}</code> is running
          through the browser Digital Credentials API.
        </p>
      </div>

      <div v-if="verifiedClaims" class="grid gap-2">
        <span class="form-label">Verified Claims</span>
        <pre class="log-box max-h-64">{{
          JSON.stringify(verifiedClaims, null, 2)
        }}</pre>
      </div>
    </template>

    <div
      v-else
      class="flex items-center justify-center min-h-[200px] text-sm text-[--color-text-muted] text-center"
    >
      QR code will appear here after creating a session
    </div>
  </div>
</template>
