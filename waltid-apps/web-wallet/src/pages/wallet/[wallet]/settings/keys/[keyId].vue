<template>
    <CenterMain>
        <BackButton />
        <div>
            <h2 class="text-lg font-semibold leading-7 text-gray-900">Key: {{ keyId }}</h2>
            <p class="mt-1 max-w-2xl text-sm leading-6 text-gray-600">We allow you to export your keypair, however, make sure you keep it safe, so that you cannot be impersonated.</p>
            Be careful of what information of this page you share.
            <span class="font-semibold">Never share your private key!</span>
        </div>

        <div class="mt-6 border p-4 rounded-2xl">
            <p class="text-base font-semibold">Key information</p>
            <div>
                <div class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Key id</label>
                        <!-- <div class="mt-1 sm:col-span-2 sm:mt-0">{{ key.keyId.id }}</div> -->
                    </div>

                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Algorithm</label>
                        <!-- <div class="mt-1 sm:col-span-2 sm:mt-0">{{ key.algorithm }}</div> -->
                    </div>

                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-2">
                        <label class="block font-medium text-gray-900">Crypto provider </label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">
                            <!-- {{ key.cryptoProvider }} -->
                        </div>
                    </div>
                </div>
            </div>

            <div class="mt-2 flex items-center justify-end gap-x-6">
                <button
                    class="inline-flex justify-center bg-red-600 hover:bg-red-500 focus-visible:outline-red-700 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="deleteKey"
                >
                    <span class="inline-flex place-items-center gap-1">
                        <TrashIcon class="w-5 h-5 mr-0.5" />
                        Delete key
                    </span>
                </button>
            </div>
        </div>

        <div class="mt-6 border p-4 rounded-2xl">
            <div>
                <p class="text-base font-semibold">Export key</p>
                <div class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                        <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5">Private key </label>
                        <div class="mt-2 sm:col-span-2 sm:mt-0">
                            <SwitchGroup as="div" class="flex items-center">
                                <Switch
                                    v-model="enableLoadPrivateKey"
                                    :class="[
                                        enableLoadPrivateKey ? 'bg-blue-600' : 'bg-gray-200',
                                        'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2',
                                    ]"
                                >
                                    <span
                                        :class="[
                                            enableLoadPrivateKey ? 'translate-x-5' : 'translate-x-0',
                                            'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                                        ]"
                                        aria-hidden="true"
                                    />
                                </Switch>
                                <SwitchLabel as="span" class="ml-3 text-sm inline-flex gap-1 place-items-center">
                                    <ExclamationTriangleIcon class="w-5 h-5" />
                                    <span class="font-medium text-gray-900">Load private key</span>
                                    {{ " " }}
                                    <span class="text-gray-500">(make sure you have no screenshares running)</span>
                                </SwitchLabel>
                            </SwitchGroup>
                        </div>
                    </div>

                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                        <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="format">Format </label>
                        <div class="mt-2 sm:col-span-2 sm:mt-0">
                            <select
                                id="format"
                                v-model="format"
                                class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                name="format"
                            >
                                <option value="JWK">JWK</option>
                                <option value="PEM">PEM</option>
                            </select>

                            <p class="mt-3 text-sm leading-6 text-gray-600">
                                Depending on the program you want to import your key in, you can choose different formats to export your key(/pair) with.
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div class="mt-2 flex items-center justify-end gap-x-6">
                <button
                    :class="[enableLoadPrivateKey ? 'bg-yellow-500 hover:bg-yellow-400 focus-visible:outline-yellow-600' : 'bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600']"
                    class="inline-flex justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="exportKey"
                >
                    <span class="inline-flex place-items-center gap-1">
                        <ExclamationTriangleIcon v-if="enableLoadPrivateKey" class="w-5 h-5 mr-0.5" />
                        <ArrowUpOnSquareIcon v-else class="w-5 h-5 mr-0.5" />
                        Export
                    </span>
                </button>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import { ArrowUpOnSquareIcon, ExclamationTriangleIcon, TrashIcon } from "@heroicons/vue/24/outline";
import { ref } from "vue";
import { Switch, SwitchGroup, SwitchLabel } from "@headlessui/vue";

const route = useRoute();

const keyId = route.params.keyId;

const format = ref("JWK");
const enableLoadPrivateKey = ref(false);

const currentWallet = useCurrentWallet()

const key = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/load/${keyId}`);
refreshNuxtData();

function exportKey() {
    navigateTo(`export/${keyId}?format=${format.value}&loadPrivateKey=${enableLoadPrivateKey.value}`);
}

async function deleteKey() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/${keyId}`, {
        method: "DELETE",
    }).finally(() => {
        navigateTo(`/wallet/${currentWallet.value}/settings/keys`);
    });
}

useHead({
    title: "View key - walt.id",
});
</script>

<style scoped></style>
