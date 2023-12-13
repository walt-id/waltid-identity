<template>
    <CenterMain>
        <ol class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-3" role="list">
            <li v-for="method in methods" :key="method[0]" class="flex items-center justify-between gap-x-6 py-4">
                <div class="min-w-0">
                    <div class="flex items-start gap-x-3">
                        <p class="text-base font-semibold leading-6 text-gray-900">
                            {{ method[0] }}
                        </p>
                    </div>
                </div>
                <div class="flex flex-none items-center gap-x-4">
                    <button
                        class="w-32 text-center rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                        @click="generateKey(method[1][0])"
                    >
                        Generate {{ method[1][0] }}
                    </button>
                </div>
            </li>
        </ol>
        <div v-if="response && response != ''" class="mt-6 border p-4 rounded-2xl">
            <p class="text-base font-semibold">Response</p>

            <div class="mt-1 space-y-6 border-gray-900/10 pb-6 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                <p class="mt-2 flex items-center bg-green-100 p-3 rounded-xl overflow-x-scroll">
                    <CheckIcon class="w-5 h-5 mr-1 text-green-600" />
                    <span class="text-green-800"
                        >Generated key: <code>{{ response }}</code></span
                    >
                </p>
                <div class="pt-3 flex justify-end">
                    <NuxtLink :to="`/wallet/${currentWallet}/settings/keys`">
                        <button class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center">
                            <ArrowUturnLeftIcon class="h-5 pr-1" />
                            Return back
                        </button>
                    </NuxtLink>
                </div>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import {CheckIcon, ArrowUturnLeftIcon} from "@heroicons/vue/24/outline"

const loading = ref(false);
const response = ref("");

const currentWallet = useCurrentWallet()

const methods = new Map([
    ["EdDSA_Ed25519", ["Ed25519"]],
    ["ECDSA_Secp256k1", ["secp256k1"]],
    ["ECDSA_Secp256r1", ["secp256r1"]],
    ["RSA", ["RSA"]],
]);

async function generateKey(type: String) {
    loading.value = true;

    console.log(type);
    response.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/generate`, {
        method: "POST",
        params: {
            type: type,
        },
    });
    loading.value = false;
}

useHead({
    title: "Generate key - walt.id",
});
</script>

<style scoped></style>
