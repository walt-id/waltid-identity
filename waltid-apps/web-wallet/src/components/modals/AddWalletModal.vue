<template>
    <div>
        <CloseButton />
        <div class="flex min-h-full items-end p-4 sm:items-center sm:p-0">
            <form class="space-y-6" @submit.prevent="addWallet">
                <div>
                    <label class="block text-sm font-medium leading-6 text-gray-900 dark:text-white" for="address">
                        <span class="flex flex-row items-center">
                            <CubeTransparentIcon class="h-5 mr-1" />
                            Wallet Address
                        </span></label
                    >
                    <div class="mt-2">
                        <input
                            id="address"
                            v-model="addressInput"
                            autocomplete="address"
                            autofocus
                            class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 text-gray-900 dark:text-white placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-2 bg-white dark:bg-gray-700"
                            name="address"
                            required="true"
                            type="address"
                        />
                    </div>
                </div>

                <div class="space-y-1">
                    <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white" for="ecosystem">
                        <span class="flex flex-row items-center">
                            <Square3Stack3DIcon class="h-5 mr-1" />
                            Ecosystem
                        </span>
                    </label>
                    <!-- class="block w-full px-4 py-3 text-base text-gray-900 border border-gray-300 rounded-lg bg-current focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500" -->
                    <select
                        v-model="selectedEcosystem"
                        class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 text-gray-900 dark:text-white placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6 px-2 bg-white dark:bg-gray-700"
                        name="ecosystem-selection"
                        required="true"
                    >
                        <option v-for="ecosystem in ecosystems" :key="ecosystem" :value="ecosystem">{{ ecosystem }}</option>
                    </select>
                </div>

                <div>
                    <button
                        class="inline-flex w-full justify-center items-center rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                        type="submit"
                    >
                        Add
                        <svg v-if="isProgress" class="animate-spin ml-1.5 mr-3 h-5 w-5 text-white" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" fill="currentColor"></path>
                        </svg>
                        <PlusIcon v-else class="ml-1.5 h-5 w-5" />
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>

<script lang="ts" setup>
import CloseButton from "./CloseButton.vue";
import ActionResultModal from "~/components/modals/ActionResultModal.vue";
import useModalStore from "~/stores/useModalStore";
import { CubeTransparentIcon, PlusIcon, Square3Stack3DIcon } from "@heroicons/vue/20/solid";

const props = defineProps<{
    callback: any;
}>();
const { callback } = toRefs(props);
const store = useModalStore();
const isProgress = ref(false);
const error = ref({});
const success = ref(false);
const selectedEcosystem = ref("");
let addressInput = "";

// TODO: fetch from backend
// const {data: ecosystems, pending} = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/web3/ecosystems`)
// refreshNuxtData()
const ecosystems = ["ethereum", "tezos", "flow", "near", "algorand"];

const currentWallet = useCurrentWallet()

async function addWallet() {
    console.log(`addWallet: ${addressInput} - ${selectedEcosystem.value}`);
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/web3accounts/link`, {
        method: "POST",
        body: {
            address: addressInput,
            ecosystem: selectedEcosystem.value,
        },
    })
        .then((data) => {
            store.closeModal();
            callback.value(data);
        })
        .catch((err) => {
            store.openModal({
                component: ActionResultModal,
                props: {
                    title: "Error",
                    message: err.data,
                    isError: true,
                },
            });
        });
}
</script>
