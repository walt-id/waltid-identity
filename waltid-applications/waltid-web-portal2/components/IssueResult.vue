<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";

defineProps<{
  session: ReturnType<typeof useIssuerSession>;
}>();

const config = useRuntimeConfig();
const walletUrl = config.public.walletUrl as string;
</script>

<template>
  <div class="grid gap-4">
    <template v-if="session.result.value">
      <QrDisplay
        v-if="
          session.result.value.flowType === 'qr' &&
          session.result.value.credentialOffer
        "
        :value="session.result.value.credentialOffer"
        :wallet-url="walletUrl"
        wallet-path="api/siop/initiateIssuance"
      />

      <div
        v-else
        class="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900"
      >
        <p class="font-semibold">Digital Credentials API issuance</p>
        <p class="mt-1">
          Session <code>{{ session.result.value.sessionId }}</code> was started
          via the browser Digital Credentials API
          (<code>openid4vci-v1</code>).
        </p>
        <p class="mt-2 text-xs text-blue-800">
          On desktop, Chrome may show a proximity QR for a nearby Android
          wallet. After the wallet engages, watch the result log for OpenID4VCI
          issuance events from the issuer. Browser create() cancellation or
          failure is shown below and does not replace issuer SSE status.
        </p>
      </div>

      <div
        v-if="session.browserHandoffNotice.value"
        class="p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm text-amber-900"
      >
        <p class="font-semibold">Browser Digital Credentials handoff</p>
        <p class="mt-1">{{ session.browserHandoffNotice.value }}</p>
        <p class="mt-1 text-xs">
          This is non-fatal. The issuer session is still running; use the result
          log for the authoritative issuance outcome.
        </p>
      </div>

      <div
        v-if="session.result.value.txCodeValue"
        class="p-3 rounded-lg bg-amber-50 border border-amber-200 text-sm"
      >
        <span class="font-semibold text-amber-800">PIN: </span>
        <code class="font-mono text-amber-900">{{
          session.result.value.txCodeValue
        }}</code>
      </div>
    </template>

    <div
      v-else
      class="flex items-center justify-center min-h-[200px] text-sm text-[--color-text-muted] text-center"
    >
      Offer result will appear here after creating an offer
    </div>
  </div>
</template>
