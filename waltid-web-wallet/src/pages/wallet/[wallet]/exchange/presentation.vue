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
                <div v-for="(credential, credentialIdx) in matchedCredentials" :key="credentialIdx">
                    <div :class="{ 'mt-[-85px]': credentialIdx !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                    cursor-pointer duration-200">
                        <VerifiableCredentialCard :credential="credential" />
                    </div>
                </div>
            </div>
        </CenterMain>
        <div class="fixed bottom-0 w-full p-4 bg-white shadow-md">
            <button @click="acceptPresentation" class="w-full py-3 mt-4 text-white bg-[#002159] rounded-xl">
                Share
            </button>
            <button @click="navigateTo(`/wallet/${walletId}`)" class="w-full py-3 mt-4 bg-white">
                Decline
            </button>
        </div>
    </div>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import PageHeader from "~/components/PageHeader.vue";
import ActionButton from "~/components/buttons/ActionButton.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import { groupBy } from "~/composables/groupings";
import { useTitle } from "@vueuse/core";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";

import { Disclosure, DisclosureButton, DisclosurePanel } from "@headlessui/vue";
import { encodeDisclosure, parseDisclosures } from "~/composables/disclosures";

const route = useRoute();
const walletId = route.params.wallet;
const currentWallet = useCurrentWallet();

async function resolvePresentationRequest(request) {
    try {
        console.log("RESOLVING request", request);
        const response = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolvePresentationRequest`, {
            method: "POST",
            body: request
        });
        console.log(response);
        return response;
    } catch (e) {
        failed.value = true;
        throw e;
    }
}


const query = useRoute().query;

const request = await resolvePresentationRequest(decodeRequest(query.request));
console.log("Decoded request: " + request);

const presentationUrl = new URL(request);
const presentationParams = presentationUrl.searchParams;

const verifierHost = new URL(presentationParams.get("response_uri") ?? presentationParams.get("redirect_uri") ?? "").host;
console.log("verifierHost: ", verifierHost);

const presentationDefinition = presentationParams.get("presentation_definition");
console.log("presentationDefinition: ", presentationDefinition);

let inputDescriptors = JSON.parse(presentationDefinition)["input_descriptors"];
console.log("inputDescriptors: ", inputDescriptors);

let i = 0;
let groupedCredentialTypes = groupBy(
    inputDescriptors.map((item) => {
        return { id: ++i, name: item.id };
    }),
    (c) => c.name
);
console.log("groupedCredentialTypes: ", groupedCredentialTypes);

const immediateAccept = ref(false);

const failed = ref(false);
const failMessage = ref("Unknown error occurred.");


const matchedCredentials = await $fetch<Array<Object>>(`/wallet-api/wallet/${currentWallet.value}/exchange/matchCredentialsForPresentationDefinition`, {
    method: "POST",
    body: presentationDefinition
});

const selection = ref({});
const selectedCredentialIds = computed(() => Object.entries(selection.value).filter((it) => it[1]).map((it) => it[0]))

for (let credential of matchedCredentials) {
    selection.value[credential.id] = true
}

/*if (matchedCredentials.value.length == 1) {
    selection[matchedCredentials[0].id] = true
}*/

const disclosures = ref({});
//const encodedDisclosures = computed(() => Object.keys(disclosures.value).map((cred) => disclosures.values[cred].map((disclosure) => encodeDisclosure(disclosure))))
const encodedDisclosures = computed(() => {
    if (JSON.stringify(disclosures.value) === "{}") return null

    const m = {}
    for (let credId in disclosures.value) {
        if (m[credId] === undefined) {
            m[credId] = []
        }

        for (let disclosure of disclosures.value[credId]) {
            console.log("DISC ", disclosure)
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
        console.log("Response: " + response);
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
        const error: { message: string; redirectUri: string | null | undefined } = await response.json();
        failMessage.value = error.message;

        console.log("Error response: " + JSON.stringify(error));
        window.alert(error.errorMessage)

        if (error.redirectUri != null) {
            navigateTo(error.redirectUri as string, {
                external: true
            });
        }
        //console.log("Policy verification failed: ", err)

        //let sessionId = presentationUrl.searchParams.get('state');
        //window.location.href = `https://portal.walt.id/success/${sessionId}`;

        //window.alert(err)
        //throw err
    }
}

if (query.accept) {
    // TODO make accept a JWT or something wallet-backend secured
    immediateAccept.value = true;
    acceptPresentation();
}

useTitle(`Present credentials - walt.id`);
definePageMeta({
    layout: false
});
</script>

<style scoped></style>
