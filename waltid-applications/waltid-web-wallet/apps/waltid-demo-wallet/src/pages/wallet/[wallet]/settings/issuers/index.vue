<template>
  <CenterMain class="sm:mt-6 lg:ml-3">
    <h1 class="text-lg font-semibold text-center sm:text-left sm:font-bold sm:text-3xl">
      Issuers
    </h1>
    <p class="text-center sm:text-left">
      Select Issuer to request credentials from.
    </p>
    <ol class="mt-8" role="list">
      <li v-for="issuer in issuers" :key="issuer.did"
        class="flex items-center justify-between py-4 rounded-lg shadow-md mt-4">
        <NuxtLink :to="`/wallet/${currentWallet}/settings/issuers/${issuer.did}`" class="w-[100%]">
          <div class="flex items-start gap-x-3">
            <p class="mx-2 text-black font-bold leading-6 text-gray-900">
              {{ issuer.did }}
            </p>
          </div>
          <div class="flex items-start gap-x-3">
            <p class="mx-2 overflow-x-auto text-black-700 leading-6 text-sm">
              {{
                issuer.description ||
                "Digital identity & wallet infrastructure provider."
              }}
            </p>
          </div>
        </NuxtLink>
      </li>
    </ol>
    <p v-if="issuers && issuers.length == 0" class="text-lg font-semibold text-center">
      No Issuers
    </p>
  </CenterMain>
</template>

<script lang="ts" setup>
import { useCurrentWallet } from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";

const currentWallet = useCurrentWallet();

const hardcodedIssuer = { did: "did:example:1234567890", description: "PGP Issuer" };
const issuers = ref([hardcodedIssuer]);

const reqData = useLazyFetch<{ did: string, description: string }[]>(`/wallet-api/wallet/${currentWallet.value}/issuers`).data;
watch(reqData, (data: any) => {
  if (data) {
    issuers.value = [hardcodedIssuer];
    issuers.value.push(...data);
  }
}, { immediate: true });

definePageMeta({
  layout: window.innerWidth > 650 ? "desktop" : "mobile",
});
useHead({
  title: "Issuers - walt.id",
});
</script>
