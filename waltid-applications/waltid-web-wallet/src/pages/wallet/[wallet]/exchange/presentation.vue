<template>
    <div>
        <CenterMain>
            <h1 class="mb-2 text-2xl text-center font-bold">Presentation Request</h1>

            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full">
                Presenting credential(s)...
            </LoadingIndicator>

            <div v-if="matchedCredentials.length == 0">
                <span class="text-red-600 animate-pulse flex items-center gap-1 py-1">
                    <Icon name="heroicons:exclamation-circle" class="h-6 w-6" />
                    You don't have any credentials matching this presentation definition in your wallet.
                </span>
            </div>

            <div v-else class="my-10">
                <div v-if="mobileView" v-for="(credential, credentialIdx) in matchedCredentials" :key="credentialIdx">
                    <div :class="{ 'mt-[-85px]': credentialIdx !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                    cursor-pointer duration-200">
                        <VerifiableCredentialCard :credential="credential" />
                    </div>
                </div>
                <div class="w-full flex justify-center" v-else>
                    <button @click="index--" class="mt-4 text-[#002159] font-bold" v-if="index > 0">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                        </svg>
                    </button>
                    <VerifiableCredentialCard :credential="{
                        document: matchedCredentials[index].document
                    }" :isDetailView="true" />
                    <button @click="index++" class="mt-4 text-[#002159] font-bold"
                        v-if="index < matchedCredentials.length - 1">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                        </svg>
                    </button>
                </div>
                <div v-if="!mobileView" class="text-center text-gray-500 mt-2">
                    {{ index + 1 }} of {{ matchedCredentials.length }}
                </div>
            </div>
        </CenterMain>
        <div v-if="!failed && matchedCredentials.length" class="w-full sm:max-w-2xl sm:mx-auto">
            <div class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end
                        sm:gap-4">
                <button @click="acceptPresentation" class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
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
import { useTitle } from "@vueuse/core";
import { groupBy } from "~/composables/groupings";
import CenterMain from "~/components/CenterMain.vue";
import { encodeDisclosure } from "~/composables/disclosures";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";

const index = ref(0);
const failed = ref(false);
const immediateAccept = ref(false);
const mobileView = ref(window.innerWidth < 650);
const failMessage = ref("Unknown error occurred.");

const route = useRoute();
const walletId = route.params.wallet;
const currentWallet = useCurrentWallet();
async function resolvePresentationRequest(request: string) {
    try {
        const response = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolvePresentationRequest`, {
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
const request = await resolvePresentationRequest(decodeRequest(query.request as string));
const presentationUrl = new URL(request as string);
const presentationParams = presentationUrl.searchParams;

const verifierHost = new URL(presentationParams.get("response_uri") ?? presentationParams.get("redirect_uri") ?? "").host;
const presentationDefinition = presentationParams.get("presentation_definition") as string;
const matchedCredentials = await $fetch<Array<{ id: string, document: string }>>(`/wallet-api/wallet/${currentWallet.value}/exchange/matchCredentialsForPresentationDefinition`, {
    method: "POST",
    body: presentationDefinition
});

const selection = ref<{ [key: string]: boolean; }>({});
const selectedCredentialIds = computed(() => Object.entries(selection.value).filter((it) => it[1]).map((it) => it[0]))
for (let credential of matchedCredentials) {
    selection.value[credential.id] = true
}

const disclosures: Ref<{ [key: string]: any[] }> = ref({});
const encodedDisclosures = computed(() => {
    if (JSON.stringify(disclosures.value) === "{}") return null

    const m: { [key: string]: any[] } = {}
    for (let credId in disclosures.value) {
        if (m[credId] === undefined) {
            m[credId] = []
        }

        for (let disclosure of disclosures.value[credId]) {
            m[credId].push(encodeDisclosure(disclosure))
        }
    }

    return m
})

function addDisclosure(credentialId: string, disclosure: string) {
    if (disclosures.value[credentialId] === undefined) {
        disclosures.value[credentialId] = []
    }
    disclosures.value[credentialId].push(disclosure)
}

function removeDisclosure(credentialId: string, disclosure: string) {
    disclosures.value[credentialId] = disclosures.value[credentialId].filter((elem) => elem[0] != disclosure[0])
}

async function acceptPresentation() {
    const req = {
        //did: String, // todo: choose DID of shared credential // for now wallet-api chooses the default wallet did
        presentationRequest: request,
        selectedCredentials: selectedCredentialIds.value,
        disclosures: encodedDisclosures.value
    };

    const response = await fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/usePresentationRequest`, {
        method: "POST",
        body: JSON.stringify(req),
        redirect: "manual",
        headers: {
            "Content-Type": "application/json"
        }
    });

    if (response.ok) {
        const parsedResponse: { redirectUri: string } = await response.json();
        if (parsedResponse.redirectUri) {
            navigateTo(parsedResponse.redirectUri, {
                external: true
            });
        } else {
            window.alert("Presentation successful, no redirect URL supplied.");
            navigateTo(`/wallet/${currentWallet.value}`, {
                external: true
            });
        }
    } else {
        failed.value = true;
        const error: { message: string; redirectUri: string | null | undefined, errorMessage: string } = await response.json();
        failMessage.value = error.message;

        console.log("Error response: " + JSON.stringify(error));
        window.alert(error.errorMessage)

        if (error.redirectUri != null) {
            navigateTo(error.redirectUri as string, {
                external: true
            });
        }
    }
}

if (query.accept) {
    immediateAccept.value = true;
    acceptPresentation();
}

useTitle(`Present credentials - walt.id`);
definePageMeta({
    layout: window.innerWidth > 650 ? "desktop-without-sidebar" : false,
});
</script>

<style scoped></style>
