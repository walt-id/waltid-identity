<template>
    <CenterMain>
        <BackButton />
        <LoadingIndicator v-if="pending">Loading DID...</LoadingIndicator>
        <div v-else>
            <div class="flex space-x-3">
                <div class="min-w-0 flex-1">
                    <p class="text-sm font-semibold text-gray-900 whitespace-pre-wrap">DID: {{ didId }}</p>
                    <p class="text-sm text-gray-500">DID document data below:</p>
                </div>
            </div>
            <div v-if="didDoc">
                <div class="mt-6 border p-4 rounded-2xl">
                    <p class="text-base font-semibold">DID information</p>
                    <div>
                        <div class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                            <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                                <label class="block font-medium text-gray-900">Identifier </label>
                                <div class="mt-1 sm:col-span-2 sm:mt-0 overflow-x-scroll">
                                    {{ didId }}
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="mt-2 flex items-center justify-end gap-x-6">
                        <button
                            class="inline-flex justify-center bg-red-600 hover:bg-red-500 focus-visible:outline-red-700 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                            @click="deleteDid"
                        >
                            <span class="inline-flex place-items-center gap-1">
                                <TrashIcon class="w-5 h-5 mr-0.5" />
                                Delete DID
                            </span>
                        </button>
                    </div>
                    <div class="mt-2 flex items-center justify-end gap-x-6">
                        <button
                            class="inline-flex justify-center bg-gray-600 hover:bg-gray-400 focus-visible:outline-gray-700 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                            @click="setDefault"
                        >
                            <span class="inline-flex place-items-center gap-1">
                                <TagIcon class="w-5 h-5 mr-0.5" />
                                Set default DID
                            </span>
                        </button>
                    </div>
                </div>
                <div class="p-3 shadow mt-3">
                    <h3 class="font-semibold mb-2">QR code</h3>
                    <qrcode-vue v-if="didDoc && JSON.stringify(didDoc).length <= 4296" :value="JSON.stringify(didDoc)" level="L" size="300"></qrcode-vue>
                    <p v-else-if="didDoc && JSON.stringify(didDoc).length">
                        Unfortunately, this DID document is too big to be viewable as QR code (DID document size is {{ didDoc.length }} characters, but the maximum a QR code can hold is 4296).
                        {{ JSON.stringify(didDoc).length }}
                    </p>
                    <p v-else>No DID document could be loaded!</p>
                </div>
                <div class="shadow p-3 mt-2 font-mono overflow-scroll">
                    <h3 class="font-semibold mb-2">JSON</h3>
                    <pre>{{ didDoc ? JSON.stringify(didDoc, null, 2) : "No DID" }}</pre>
                </div>
            </div>
            <div v-else v-if="!pending" class="p-3 shadow mt-3 bg-red-200 border-red-300 border">
                <h3 class="font-semibold text-red-500">The DID could not be loaded.</h3>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import QrcodeVue from "qrcode.vue";
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import { TagIcon, TrashIcon } from "@heroicons/vue/24/outline";

const route = useRoute();

const didId = route.params.didId;

const currentWallet = useCurrentWallet()

const { data: didDoc, pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/dids/${didId}`);
refreshNuxtData();

async function deleteDid() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids/${didId}`, {
        method: "DELETE",
    }).finally(() => {
        navigateTo(`/wallet/${currentWallet.value}/settings/dids`);
    });
}

async function setDefault() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids/default?did=${didId}`, {
        method: "POST",
    });
}

useHead({
    title: "View DID - walt.id",
});
</script>

<style scoped></style>
