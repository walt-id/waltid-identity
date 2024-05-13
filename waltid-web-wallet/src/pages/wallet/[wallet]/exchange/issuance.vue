<template>
    <div>
        <CenterMain>
            <h1 class="mb-2 text-2xl text-center font-bold">New {{ credentialCount === 1 ? "Credential" : "Credentials"
                }}</h1>
            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full"> Receiving {{ credentialCount }}
                credential(s)...
            </LoadingIndicator>

            <div v-if="failed">
                <div class="my-6 text-center">
                    <h2 class="text-xl font-bold">Failed to receive credential</h2>
                    <p class="text-red-500">{{ failMessage }}</p>
                </div>
            </div>

            <div v-else>
                <div class="my-10">
                    <div v-for="(group, index) in groupedCredentialTypes.keys()" :key="group.id">
                        <div v-for="credential in groupedCredentialTypes.get(group)" :key="credential"
                            :class="{ 'mt-[-85px]': index !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                        cursor-pointer duration-200">
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
                </div>
            </div>
        </CenterMain>
        <div class="fixed bottom-0 w-full p-4 bg-white shadow-md">
            <button @click="acceptCredential" class="w-full py-3 mt-4 text-white bg-[#002159] rounded-xl">
                Accept
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
import CredentialIcon from "~/components/CredentialIcon.vue";
import ActionButton from "~/components/buttons/ActionButton.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";
import { Listbox, ListboxButton, ListboxLabel, ListboxOption, ListboxOptions } from "@headlessui/vue";
import { CheckIcon, ChevronUpDownIcon } from "@heroicons/vue/20/solid";
import { useTitle } from "@vueuse/core";
import { ref } from "vue";

const route = useRoute();
const walletId = route.params.wallet;
const currentWallet = useCurrentWallet()
const { data: dids, pending: pendingDids } = await useLazyAsyncData(() => $fetch(`/wallet-api/wallet/${currentWallet.value}/dids`));

const selectedDid: Ref<Object | null> = ref(null);

//TODO: fix this hack for did-dropdown default selection
watch(dids, async (newDids) => {
    await nextTick();

    const newDid: string | null =
        newDids?.find((item) => {
            return item.default == true;
        }) ??
        newDids[0] ??
        null;

    console.log("Setting new DID: " + newDid);

    selectedDid.value = newDid;
});

async function resolveCredentialOffer(request) {
    try {
        console.log("RESOLVING credential offer request", request);
        const response = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveCredentialOffer`, {
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

const request = decodeRequest(query.request);
console.log("Issuance -> Using request: ", request);

const immediateAccept = ref(false);

const credentialOffer = await resolveCredentialOffer(decodeRequest(query.request));
if (credentialOffer == null) {
    throw createError({
        statusCode: 400,
        statusMessage: "Invalid issuance request: No credential_offer",
    });
}
console.log("credentialOffer: ", credentialOffer);

console.log("Issuer host...");
const issuer = credentialOffer["credential_issuer"];

let issuerHost: String;
try {
    issuerHost = new URL(issuer).host;
} catch {
    issuerHost = issuer;
}

console.log("Issuer host:", issuerHost);
const credential_issuer = await $fetch(`${issuer}/.well-known/openid-credential-issuer`)
const credentialList = [credential_issuer.credential_configurations_supported[Object.keys(credential_issuer.credential_configurations_supported).find(key => credentialOffer.credential_configuration_ids.includes(key))]];

let credentialTypes: String[] = [];

for (let credentialListElement of credentialList) {
    console.log(`Processing: ${credentialListElement}`)
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
    (c) => c.name,
);

const failed = ref(false);
const failMessage = ref("Unknown error occurred.");

async function acceptCredential() {
    const did: string | null = selectedDid.value?.did ?? dids.value[0]?.did ?? null;

    if (did === null) {
        console.warn("NO DID AT ACCEPTCREDENTIAL");
        console.log("Selected: " + selectedDid.value);
        console.log("DIDs:" + dids.value[0]);
        return;
    }
    console.log("Issue to: " + did);
    try {
        await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/useOfferRequest?did=${did}`, {
            method: "POST",
            body: request,
        });
        navigateTo(`/wallet/${currentWallet.value}`);
    } catch (e) {
        failed.value = true;

        let errorMessage = e?.data.startsWith("{") ? JSON.parse(e.data) : e.data ?? e;
        errorMessage = errorMessage?.message ?? errorMessage;

        failMessage.value = errorMessage;

        console.log("Error: ", e?.data);
        alert("Error occurred while trying to receive credential: " + failMessage.value);

        throw e;
    }
}

if (query.accept) {
    // TODO make accept a JWT or something wallet-backend secured
    immediateAccept.value = true;
    acceptCredential();
}

/*if (query.request) {
    const request = atob(query.request)
    console.log(request)
} else {
    console.error("No request")
}*/

useTitle(`Claim credentials - walt.id`);
definePageMeta({
    layout: false
});
</script>