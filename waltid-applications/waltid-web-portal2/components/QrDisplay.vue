<script setup lang="ts">
import QrcodeVue from "qrcode.vue";
import { webWalletHref as walletHref } from "~/utils/webWalletLink";

const props = defineProps<{
  value: string; // raw openid4vp:// or openid-credential-offer:// URL (for QR)
  walletUrl: string; // web wallet base URL
}>();

const copied = ref(false);

const webWalletHref = computed(() => walletHref(props.walletUrl, props.value));

async function copyToClipboard() {
  await navigator.clipboard.writeText(props.value);
  copied.value = true;
  setTimeout(() => {
    copied.value = false;
  }, 2000);
}
</script>

<template>
  <div class="flex flex-col items-center gap-4 py-2 sm:py-4">
    <div class="p-3 bg-white rounded-lg border border-[--color-border]">
      <QrcodeVue :value="value" :size="200" level="M" />
    </div>

    <div class="flex flex-col sm:flex-row gap-2 w-full sm:w-auto">
      <a
        :href="webWalletHref"
        class="btn btn-primary w-full sm:w-auto"
        target="_blank"
        rel="noopener noreferrer"
      >
        Open in Wallet
      </a>
      <button class="btn btn-secondary w-full sm:w-auto" @click="copyToClipboard">
        {{ copied ? "Copied!" : "Copy OpenID Link" }}
      </button>
    </div>
  </div>
</template>
