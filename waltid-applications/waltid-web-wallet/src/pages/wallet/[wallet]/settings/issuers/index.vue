<template>
    <CenterMain>
        <h1 class="text-lg font-semibold text-center">Issuers</h1>
        <p class="text-center">Select Issuer to request credentials from.</p>
        <ol class="mt-8" role="list">
            <li v-for="issuer in issuers" :key="issuer"
                class="flex items-center justify-between py-4 rounded-lg shadow-md mt-4">
                <NuxtLink :to="`/wallet/${currentWallet}/settings/issuers/${issuer.did}`" class="w-[100%]">
                    <div class="flex items-start gap-x-3">
                        <p class="mx-2 text-black font-bold leading-6 text-gray-900">
                            {{ issuer.did }}
                        </p>
                    </div>
                    <div class="flex items-start gap-x-3">
                        <p class="mx-2 overflow-x-auto text-black-700 leading-6 text-sm">
                            {{ issuer.description || "Digital identity & wallet infrastructure provider." }}
                        </p>
                    </div>
                </NuxtLink>
            </li>
        </ol>
        <p v-if="issuers && issuers.length == 0" class="text-lg font-semibold text-center">No Issuers</p>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";

const currentWallet = useCurrentWallet()
const issuers = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/issuers`).data;
refreshNuxtData();

definePageMeta({
    layout: 'mobile'
});
useHead({
    title: "Issuers - walt.id",
});
</script>
