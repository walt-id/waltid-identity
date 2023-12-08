<template>
    <CenterMain>
        <div>
            <h1 class="block text-lg font-semibold leading-6 text-gray-900">
                <label for="key"> Import your key in PEM or JWK format:</label>
            </h1>

            <div class="mt-1">
                <textarea
                    id="key"
                    v-model="keyText"
                    class="block w-full px-3 rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    name="key"
                    rows="4"
                />
            </div>

            <div class="mt-2 flex justify-end">
                <button
                    class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="importKey"
                >
                    <DocumentPlusIcon aria-hidden="true" class="h-5 w-5 text-white mr-1" />
                    <span>Import key</span>
                </button>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import { DocumentPlusIcon } from "@heroicons/vue/24/outline";

const keyText = ref("");
const currentWallet = useCurrentWallet()

async function importKey() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/import`, {
        method: "POST",
        body: keyText.value,
    });
    navigateTo(`/wallet/${currentWallet.value}/settings/keys`);
}
</script>

<style scoped></style>
