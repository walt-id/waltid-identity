<template>
    <CenterMain>
        <div class="flex justify-between items-center">
            <h1 class="text-lg font-semibold">Your issuers:</h1>

            <div class="flex justify-between gap-2">
                <button
                    class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="importIssuer"
                >
                    <InboxArrowDownIcon aria-hidden="true" class="h-5 w-5 text-white mr-1" />
                    <span>Import issuer</span>
                </button>
            </div>
        </div>

        <ol class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-2" role="list">
            <li v-for="issuer in issuers" :key="issuer" class="flex items-center justify-between gap-x-6 py-4">
                <div class="min-w-0">
                    <div class="flex items-start gap-x-3">
                        <p class="mx-2 text-base font-semibold leading-6 text-gray-900">
                            {{ issuer.name }}
                        </p>
                    </div>
                    <div class="flex items-start gap-x-3">
                        <p class="mx-2 overflow-x-auto text-base font-normal leading-6 text-gray-500">
                            {{ issuer.description }}
                        </p>
                    </div>
                </div>
                <div class="flex flex-none items-center gap-x-4">
                    <NuxtLink
                        :to="`/wallet/${currentWallet.value}/settings/issuers/${issuer.name}`"
                        class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                    >
                        View supported credentials
                    </NuxtLink>
                </div>
            </li>
        </ol>
        <p v-if="issuers && issuers.length == 0" class="mt-2">No issuers.</p>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import { InboxArrowDownIcon } from "@heroicons/vue/24/outline";

const currentWallet = useCurrentWallet()
const issuers = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/issuers`).data;
refreshNuxtData();

function importIssuer() {}

useHead({
    title: "Issuers - walt.id",
});
</script>
