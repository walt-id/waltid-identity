<template>
    <CenterMain>
        <BackButton />
        <div class="mt-2">
            <h2 class="text-lg font-semibold leading-7 text-gray-900">Create {{ props.method.toUpperCase() }} DID (did:{{ props.method }}):</h2>

            <div class="mt-1 border p-4 rounded-2xl">
                <p class="text-base font-semibold">DID parameters</p>
                <div>
                    <div class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                            <label class="block font-medium text-gray-900">Key id</label>
                            <div class="mt-1 sm:col-span-2 sm:mt-0">
                                <input
                                    id="keyId"
                                    v-model="keyId"
                                    class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                    name="keyId"
                                    placeholder="(optional)"
                                    type="text"
                                />
                            </div>
                            <label class="block font-medium text-gray-900">Alias</label>
                            <div class="mt-1 sm:col-span-2 sm:mt-0">
                                <input
                                    id="alias"
                                    v-model="alias"
                                    class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                    name="keyId"
                                    placeholder="(optional)"
                                    type="text"
                                />
                            </div>
                        </div>

                        <slot></slot>
                        <!--
                        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                            <label class="block font-medium text-gray-900">Algorithm</label>
                            <div class="mt-1 sm:col-span-2 sm:mt-0">EdDSA_Ed25519</div>
                        </div>-->
                    </div>
                </div>

                <div class="mt-2 flex items-center justify-end gap-x-6">
                    <button
                        class="inline-flex justify-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                        @click="createDid"
                    >
                        <span class="inline-flex place-items-center gap-1">
                            <KeyIcon v-if="!loading" class="w-5 h-5 mr-1" />
                            <InlineLoadingCircle v-else class="mr-1" />
                            Create did:{{ props.method }}
                        </span>
                    </button>
                </div>
            </div>

            <div v-if="response && response != ''" class="mt-6 border p-4 rounded-2xl">
                <p class="text-base font-semibold">Response</p>

                <div class="mt-1 space-y-6 border-gray-900/10 pb-6 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                    <p class="mt-2 flex items-center bg-green-100 p-3 rounded-xl overflow-x-scroll">
                        <CheckIcon class="w-5 h-5 mr-1 text-green-600" />
                        <span class="text-green-800"
                            >Created DID: <code>{{ response }}</code></span
                        >
                    </p>

                    <div class="pt-3 flex justify-end">
                        <NuxtLink :to="`/wallet/${currentWallet}/settings/dids`">
                            <button class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center">
                                <ArrowUturnLeftIcon class="h-5 pr-1" />
                                Return back
                            </button>
                        </NuxtLink>
                    </div>
                </div>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import { CheckIcon, KeyIcon } from "@heroicons/vue/24/solid";
import { ArrowUturnLeftIcon } from "@heroicons/vue/24/outline";
import BackButton from "~/components/buttons/BackButton.vue";
import InlineLoadingCircle from "~/components/loading/InlineLoadingCircle.vue";

const props = defineProps({
    method: {
        type: String,
        required: true,
    },
    didParams: {
        type: null,
        required: false,
        default: null,
    },
});

const loading = ref(false);
const response = ref("");

const keyId = ref("");
const alias = ref("");

const currentWallet = useCurrentWallet()

async function createDid() {
    loading.value = true;

    console.log(props.didParams);
    const postBodyCreatorParams = props.didParams ?? {};

    let merged = { ...{ keyId: keyId.value }, ...postBodyCreatorParams, ...{ alias: alias.value } };
    console.log(merged);

    const query = new URLSearchParams(merged).toString();

    response.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids/create/${props.method}?${query}`, {
        method: "POST",
    });
    loading.value = false;
}

useHead({
    title: `Create did:${props.method} - walt.id`,
});
</script>

<style scoped></style>
