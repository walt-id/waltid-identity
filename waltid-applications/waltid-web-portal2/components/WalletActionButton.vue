<script setup lang="ts">
const props = defineProps<{
  walletId: string;
  action: "issue" | "verify";
  disabled?: boolean;
}>();

const emit = defineEmits<{
  click: [];
}>();

const language = ref<"en" | "de">("en");

onMounted(() => {
  if (navigator.language.toLowerCase().startsWith("de")) {
    language.value = "de";
  }
});

const dYouSrc = computed(() => {
  const kind = props.action === "issue" ? "add" : "verify";
  return `/wallets/d-you/d-you-wallet_button_${kind}_${language.value}.svg`;
});

const dYouLabel = computed(() => {
  if (language.value === "de") {
    return props.action === "issue"
      ? "Hinzufügen zu d-you Wallet"
      : "Verifizieren mit d-you Wallet";
  }
  return props.action === "issue"
    ? "Add to d-you Wallet"
    : "Verify with d-you Wallet";
});

const labeledButton = computed(() => {
  if (props.walletId === "eudi-reference-wallet") {
    return {
      icon: "/wallets/eu-wallet/eu_wallet.svg",
      label:
        props.action === "issue"
          ? "Add to EUDI Reference Wallet"
          : "Verify with EUDI Reference Wallet",
    };
  }
  if (props.walletId === "france-identite") {
    return {
      icon: "",
      label:
        props.action === "issue"
          ? "Add to France Identité"
          : "Verify with France Identité",
    };
  }
  return null;
});
</script>

<template>
  <button
    v-if="walletId === 'd-you'"
    type="button"
    class="inline-flex border-0 bg-transparent p-0 leading-none disabled:opacity-60"
    :disabled="disabled"
    :aria-label="dYouLabel"
    @click="emit('click')"
  >
    <img :src="dYouSrc" alt="" class="h-[55px] w-auto" />
  </button>
  <button
    v-else-if="labeledButton"
    type="button"
    class="relative flex h-16 w-full max-w-[28rem] items-center justify-center gap-3 rounded-full border border-gray-200 bg-white px-6 text-[17px] font-semibold whitespace-nowrap text-gray-900 shadow-[0_4px_16px_rgba(0,0,0,0.06)] disabled:opacity-60"
    :disabled="disabled"
    @click="emit('click')"
  >
    <img
      v-if="labeledButton.icon"
      :src="labeledButton.icon"
      alt=""
      class="h-7 w-auto"
    />
    {{ labeledButton.label }}
  </button>
</template>
