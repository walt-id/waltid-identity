<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";
import { DC_API_ISSUANCE_DOCS_URL } from "~/utils/dcApiIssuance";

const props = defineProps<{
  session: ReturnType<typeof useIssuerSession>;
}>();

const config = useRuntimeConfig();
const walletUrl = config.public.walletUrl as string;

const isDcApi = computed(
  () => props.session.result.value?.flowType === "dc_api",
);
const dcApiFailed = computed(
  () => props.session.result.value?.dcApiHandoffStatus === "failed",
);
const dcApiSuccess = computed(
  () => props.session.result.value?.dcApiHandoffStatus === "success",
);
const docsUrl = computed(
  () =>
    props.session.result.value?.dcApiDocsUrl ?? DC_API_ISSUANCE_DOCS_URL,
);
</script>

<template>
  <div class="grid gap-4">
    <template v-if="session.result.value">
      <div
        v-if="isDcApi && dcApiFailed"
        class="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-900 space-y-2"
      >
        <p class="font-semibold">Digital Credentials API handoff failed</p>
        <p>
          {{
            session.result.value.dcApiError ||
            session.error.value ||
            "This browser could not start DC API issuance."
          }}
        </p>
        <p class="text-xs text-red-800">
          See the
          <a
            :href="docsUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="underline font-medium text-red-950"
          >
            Chrome Digital Credentials API issuance docs
          </a>
          for browser/flag/wallet requirements. You can continue with the QR /
          deep link offer below.
        </p>
      </div>

      <div
        v-else-if="isDcApi && !dcApiFailed"
        class="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900"
      >
        <p class="font-semibold">Digital Credentials API issuance</p>
        <p class="mt-1">
          Session <code>{{ session.result.value.sessionId }}</code>
          <template v-if="dcApiSuccess">
            was handed off through the browser Digital Credentials API
            (<code>openid4vci-v1</code>).
          </template>
          <template v-else>
            is starting browser Digital Credentials API handoff
            (<code>openid4vci-v1</code>)…
          </template>
        </p>
        <p class="mt-2 text-xs text-blue-800">
          On desktop, Chrome may show its own proximity QR for a nearby Android
          wallet. Watch the event log for OpenID4VCI progress.
        </p>
      </div>

      <QrDisplay
        v-if="
          (session.result.value.flowType === 'qr' || dcApiFailed) &&
          session.result.value.credentialOffer
        "
        :value="session.result.value.credentialOffer"
        :wallet-url="walletUrl"
        wallet-path="api/siop/initiateIssuance"
      />

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
