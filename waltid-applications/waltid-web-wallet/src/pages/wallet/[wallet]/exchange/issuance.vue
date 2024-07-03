<template>
    <div>
        <CenterMain>
            <h1 class="mb-2 text-2xl text-center font-bold sm:mt-5">New {{ credentialCount === 1 ? "Credential" :
                "Credentials" }}</h1>
            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full"> Receiving {{ credentialCount }}
                credential(s)...
            </LoadingIndicator>

            <div v-if="failed">
                <div class="my-6 text-center">
                    <h2 class="text-xl font-bold">Failed to receive credential</h2>
                    <p class="text-red-500">{{ failMessage }}</p>
                </div>
            </div>

            <div>
                <div class="my-10">
                    <div v-if="mobileView" v-for="(group, index) in groupedCredentialTypes.keys()" :key="group.id">
                        <div v-for="credential in groupedCredentialTypes.get(group)" :key="credential"
                            :class="{ 'mt-[-85px]': index !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                        cursor-pointer duration-200 w-full sm:w-[400px]">
                            <VerifiableCredentialCard :credential="{
                                parsedDocument: {
                                    type: [credential.name],
                                    issuer: {
                                        name: issuerHost,
                                    },
                                },
                            }" :isDetailView="true" />
                        </div>
                    </div>
                    <div class="w-full flex justify-center gap-5" v-else>
                        <button v-if="credentialCount > 1" @click="index--"
                            class="mt-4 text-[#002159] font-bold bg-white" :disabled="index === 0"
                            :class="{ 'cursor-not-allowed opacity-50': index === 0 }">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                                stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M15 19l-7-7 7-7" />
                            </svg>
                        </button>
                        <VerifiableCredentialCard :credential="{
                            parsedDocument: {
                                type: [credentialTypes[index]],
                                issuer: {
                                    name: issuerHost,
                                },
                            },
                        }" class="sm:w-[400px]" />
                        <button v-if="credentialCount > 1" @click="index++"
                            class="mt-4 text-[#002159] font-bold bg-white" :disabled="index === credentialCount - 1"
                            :class="{ 'cursor-not-allowed opacity-50': index === credentialCount - 1 }">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                                stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                    d="M9 5l7 7-7 7" />
                            </svg>
                        </button>
                    </div>
                    <div v-if="!mobileView" class="text-center text-gray-500 mt-2">
                        {{ index + 1 }} of {{ credentialCount }}
                    </div>
                </div>
                <div class="sm:w-[80%] md:w-[60%] mx-auto">
                    <div class="text-sm font-bold text-gray-500">Credential Offered</div>
                    <hr class="my-2 border-gray-200" />
                    <div v-for="(group, index) in groupedCredentialTypes.keys()" :key="group.id">
                        <div v-for="credential in groupedCredentialTypes.get(group)" :key="credential">
                            <div class="text-black-800">{{ credential.name }}</div>
                            <div v-if="issuerHost" class="text-sm text-gray-400">from {{ issuerHost }}</div>
                            <hr class="my-2 border-gray-200" />
                        </div>
                    </div>
                </div>
            </div>
        </CenterMain>
        <div v-if="!failed" class="w-full sm:max-w-2xl sm:mx-auto">
            <div class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end
                        sm:gap-4">
                <button @click="acceptCredential" class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
                    Accept
                </button>
                <button @click="navigateTo(`/wallet/${walletId}`)"
                    class="w-full sm:w-44 py-3 mt-4 bg-white sm:border sm:border-gray-400 sm:rounded-xl">
                    Decline
                </button>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>

import { ref } from "vue";
import { useTitle } from "@vueuse/core";
import CenterMain from "~/components/CenterMain.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";

const index = ref(0);
const route = useRoute();
const walletId = route.params.wallet;
const mobileView = ref(window.innerWidth < 650);

const currentWallet = useCurrentWallet()
const { data: dids, pending: pendingDids } = await useLazyAsyncData(() => $fetch(`/wallet-api/wallet/${currentWallet.value}/dids`));
const selectedDid: Ref<{ did: string; default: boolean; } | null> = ref(null);
watch(dids, async (newDids: Array<{ did: string; default: boolean; }>) => {
    await nextTick();

    const newDid =
        newDids?.find((item) => {
            return item.default == true;
        }) ??
        newDids[0] ??
        null;

    selectedDid.value = newDid;
});

async function resolveCredentialOffer(request: string) {
    try {
        const response: {
            credential_issuer: string;
            credential_configuration_ids: string[];
        } = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveCredentialOffer`, {
            method: "POST",
            body: request
        });
        return response;
    } catch (e) {
        failed.value = true;
        throw e;
    }
}

const query = useRoute().query;
const request = decodeRequest(query.request as string);

const credentialOffer = await resolveCredentialOffer(request);
if (credentialOffer == null) {
    throw createError({
        statusCode: 400,
        statusMessage: "Invalid issuance request: No credential_offer",
    });
}
const issuer = credentialOffer["credential_issuer"];

let issuerHost: String;
try {
    issuerHost = new URL(issuer).host;
} catch {
    issuerHost = issuer;
}

const credential_issuer: { credential_configurations_supported: Array<{ types: Array<String>; }>; } =
    await $fetch(`${issuer}/.well-known/openid-credential-issuer`)
const credentialList = credentialOffer.credential_configuration_ids.map((id) => credential_issuer.credential_configurations_supported[id]);

let credentialTypes: String[] = [];
for (let credentialListElement of credentialList) {
    const typeList = credentialListElement["types"] as Array<String>;
    const lastType = typeList[typeList.length - 1] as String;
    credentialTypes.push(lastType);
}

const credentialCount = credentialTypes.length;

let i = 0;
const groupedCredentialTypes = groupBy(
    credentialTypes.map((item) => {
        return { id: ++i, name: item };
    }),
    (c: { name: string }) => c.name,
);

const failed = ref(false);
const failMessage = ref("Unknown error occurred.");
async function acceptCredential() {
    const did: string | null = selectedDid.value?.did ?? dids.value[0]?.did ?? null;
    if (did === null) { return; }

    try {
        await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/useOfferRequest?did=${did}`, {
            method: "POST",
            body: request,
        });
        navigateTo(`/wallet/${currentWallet.value}`);
    } catch (e: any) {
        failed.value = true;

        let errorMessage = e?.data.startsWith("{") ? JSON.parse(e.data) : e.data ?? e;
        errorMessage = errorMessage?.message ?? errorMessage;

        failMessage.value = errorMessage;

        console.log("Error: ", e?.data);
        alert("Error occurred while trying to receive credential: " + failMessage.value);

        throw e;
    }
}

const immediateAccept = ref(false);
if (query.accept) {
    immediateAccept.value = true;
    acceptCredential();
}

useTitle(`Claim credentials - walt.id`);
definePageMeta({
    layout: window.innerWidth > 650 ? "desktop-without-sidebar" : false,
});
</script>