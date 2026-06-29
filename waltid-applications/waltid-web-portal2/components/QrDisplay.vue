<script setup lang="ts">
import QrcodeVue from 'qrcode.vue'

const props = defineProps<{
  value: string        // raw openid4vp:// or openid-credential-offer:// URL (for QR)
  walletUrl: string    // web wallet base URL
  walletPath: string   // e.g. 'api/siop/initiatePresentation' or 'api/siop/initiateIssuance'
  label?: string
}>()

const copied = ref(false)

// Mirrors sendToWebWallet from the old portal:
// walletUrl/walletPath + query string from the raw value
const webWalletHref = computed(() => {
  const qs = props.value.substring(props.value.indexOf('?'))
  return `${props.walletUrl}/${props.walletPath}${qs}`
})

async function copyToClipboard() {
  await navigator.clipboard.writeText(props.value)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}
</script>

<template>
  <div class="flex flex-col items-center gap-4 py-4">
    <div class="p-3 bg-white rounded-lg border border-[--color-border]">
      <QrcodeVue :value="value" :size="200" level="M" />
    </div>

    <div class="flex gap-2">
      <a :href="webWalletHref" class="hidden btn btn-primary" target="_blank" rel="noopener noreferrer">
        Open in Wallet
      </a>
      <button class="btn btn-secondary" @click="copyToClipboard">
        {{ copied ? 'Copied!' : 'Copy URL' }}
      </button>
    </div>

    <p v-if="label" class="text-xs text-[--color-text-muted] text-center max-w-full break-all px-4">
      {{ label }}
    </p>
  </div>
</template>
