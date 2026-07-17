<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";

const props = defineProps<{
  session: ReturnType<typeof useIssuerSession>;
}>();

const config = useRuntimeConfig();
const walletUrl = config.public.walletUrl as string;
</script>

<template>
  <div class="grid gap-4">
    <template v-if="session.result.value">
      <QrDisplay
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
      QR code will appear here after creating an offer
    </div>
  </div>
</template>
