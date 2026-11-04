<script setup lang="ts">
const props = defineProps<{
  walletId: string;
  action: "issue" | "verify";
  disabled?: boolean;
}>();

const emit = defineEmits<{
  click: [];
}>();

const browserLanguage = ref("en");

onMounted(() => {
  browserLanguage.value = navigator.language.toLowerCase();
});

const dYouLanguage = computed(() =>
  browserLanguage.value.startsWith("de") ? "de" : "en",
);

const franceLanguage = computed(() =>
  browserLanguage.value.startsWith("fr") ? "fr" : "en",
);

const dYouSrc = computed(() => {
  const kind = props.action === "issue" ? "add" : "verify";
  return `/wallets/d-you/d-you-wallet_button_${kind}_${dYouLanguage.value}.svg`;
});

const franceSrc = computed(() => {
  const kind = props.action === "issue" ? "add" : "verify";
  return `/wallets/france-identite/france-identite_button_${kind}_${franceLanguage.value}.svg`;
});

const dYouLabel = computed(() => {
  if (dYouLanguage.value === "de") {
    return props.action === "issue"
      ? "Hinzufügen zu d-you Wallet"
      : "Verifizieren mit d-you Wallet";
  }
  return props.action === "issue"
    ? "Add to d-you Wallet"
    : "Verify with d-you Wallet";
});

const franceLabel = computed(() => {
  if (franceLanguage.value === "fr") {
    return props.action === "issue"
      ? "Ajouter à France Identité"
      : "Vérifier avec France Identité";
  }
  return props.action === "issue"
    ? "Add to France Identité"
    : "Verify with France Identité";
});

const eudiSrc = computed(() => {
  const kind = props.action === "issue" ? "add" : "verify";
  return `/wallets/eu-wallet/eu-wallet_button_${kind}_en.svg`;
});

const eudiLabel = computed(() =>
  props.action === "issue"
    ? "Add to EUDI Reference Wallet"
    : "Verify with EUDI Reference Wallet",
);

const issuanceBlocked = computed(
  () => props.action === "issue" && props.walletId === "france-identite",
);
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
    v-else-if="walletId === 'france-identite'"
    type="button"
    class="inline-flex border-0 bg-transparent p-0 leading-none disabled:cursor-not-allowed disabled:opacity-40 disabled:grayscale"
    :disabled="disabled || issuanceBlocked"
    :aria-label="franceLabel"
    @click="emit('click')"
  >
    <img :src="franceSrc" alt="" class="h-[55px] w-auto" />
  </button>
  <button
    v-else-if="walletId === 'eudi-reference-wallet'"
    type="button"
    class="inline-flex border-0 bg-transparent p-0 leading-none disabled:opacity-60"
    :disabled="disabled"
    :aria-label="eudiLabel"
    @click="emit('click')"
  >
    <img :src="eudiSrc" alt="" class="h-[55px] w-auto" />
  </button>
</template>
