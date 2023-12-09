<template>
    <div>
        <PageHeader>
            <template v-slot:title>
                <div class="ml-3">
                    <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:leading-9">
                        Receive {{ credentialCount === 1 ? "single" : credentialCount }}
                        {{ credentialCount === 1 ? "credential" : "credentials" }}
                    </h1>
                    <p>
                        issued by <span class="underline">{{ issuerHost }}</span>
                    </p>
                </div>
            </template>

            <template v-if="!immediateAccept" v-slot:menu>
                <ActionButton
                    class="inline-flex items-center rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:scale-105 hover:animate-pulse hover:bg-red-700 focus:animate-none focus:outline focus:outline-offset-2 focus:outline-red-700"
                    display-text="Reject"
                    icon="heroicons:x-mark"
                    type="button"
                    @click="navigateTo(`/wallet/${currentWallet.value}`)"
                />

                <div class="group flex">
                    <ActionButton
                        :class="[
                            failed
                                ? 'animate-pulse bg-red-600 hover:scale-105 hover:bg-red-700 focus:outline focus:outline-offset-2 focus:outline-red-700'
                                : 'bg-green-600 hover:scale-105 hover:animate-pulse hover:bg-green-700 focus:animate-none focus:outline-green-700',
                        ]"
                        :disabled="(selectedDid === null || selectedDid === undefined) && !(dids && dids?.length === 1)"
                        :failed="failed"
                        class="inline-flex items-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus:outline focus:outline-offset-2 disabled:bg-gray-200 disabled:cursor-not-allowed"
                        display-text="Accept"
                        icon="heroicons:check"
                        type="button"
                        @click="acceptCredential"
                    />
                    <span v-if="failed" class="group-hover:opacity-100 transition-opacity bg-gray-800 px-1 text-sm text-gray-100 rounded-md absolute -translate-x-1/2 opacity-0 m-4 mx-auto">
                        {{ failMessage }}
                    </span>
                </div>
            </template>
        </PageHeader>

        <CenterMain>
            <h1 class="mb-2 text-2xl font-semibold">Issuance</h1>

            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full"> Receiving {{ credentialCount }} credential(s)... </LoadingIndicator>

            <div class="flex col-2">
                <div v-if="!pendingDids" class="relative w-full">
                    <Listbox v-if="dids?.length !== 1" v-model="selectedDid" as="div">
                        <ListboxLabel class="block text-sm font-medium leading-6 text-gray-900">Select DID: </ListboxLabel>

                        <div class="relative mt-2">
                            <ListboxButton
                                v-if="selectedDid !== null"
                                class="relative w-full cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 sm:text-sm sm:leading-6 h-9"
                            >
                                <span class="flex items-center">
                                    <p class="truncate font-bold">{{ selectedDid?.alias }}</p>
                                    <span class="ml-3 block truncate">{{ selectedDid?.did }}</span>
                                </span>
                                <span class="pointer-events-none absolute inset-y-0 right-0 ml-3 flex items-center pr-2">
                                    <ChevronUpDownIcon aria-hidden="true" class="h-5 w-5 text-gray-400" />
                                </span>
                            </ListboxButton>

                            <transition leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
                                <ListboxOptions
                                    class="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm"
                                >
                                    <ListboxOption v-for="did in dids" :key="did?.did" v-slot="{ active, selectedDid }" :value="did" as="template">
                                        <li :class="[active ? 'bg-indigo-600 text-white' : 'text-gray-900', 'relative cursor-default select-none py-2 pl-3 pr-9']">
                                            <div class="flex items-center">
                                                <p class="italic">{{ did.alias }}</p>
                                                <span :class="[selectedDid ? 'font-semibold' : 'font-normal', 'ml-3 block truncate']">{{ did.did }}</span>
                                            </div>
                                            <span v-if="selectedDid" :class="[active ? 'text-white' : 'text-indigo-600', 'absolute inset-y-0 right-0 flex items-center pr-4']">
                                                <CheckIcon aria-hidden="true" class="h-5 w-5" />
                                            </span>
                                        </li>
                                    </ListboxOption>
                                </ListboxOptions>
                            </transition>
                        </div>
                    </Listbox>

                    <div class="rounded-md bg-blue-50 p-2 mt-2">
                        <div class="flex">
                            <!--<div class="flex-shrink-0">
                                <InformationCircleIcon class="h-5 w-5 text-blue-400" aria-hidden="true" />
                            </div>-->
                            <div class="ml-3 flex-1 flex flex-col md:flex-row md:justify-between">
                                <span v-if="selectedDid != null" class="text-sm text-blue-700 max-w-xs overflow-x-scroll mr-3 truncate">
                                    Will issue to DID: {{ selectedDid.alias }} ({{ selectedDid.did }})
                                </span>
                                <button class="text-sm md:ml-6">
                                    <NuxtLink class="whitespace-nowrap font-medium text-blue-700 hover:text-blue-600"
                                              :to="`/wallet/${currentWallet}/settings/dids`">
                                        DID management
                                        <span aria-hidden="true"> &rarr;</span>
                                    </NuxtLink>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <p class="mt-10 mb-1">The following credentials will be issued:</p>

            <div aria-label="Credential list" class="h-full overflow-y-auto shadow-xl">
                <div v-for="group in groupedCredentialTypes.keys()" :key="group.id" class="relative">
                    <div class="top-0 z-10 border-y border-b-gray-200 border-t-gray-100 bg-gray-50 px-3 py-1.5 text-sm font-semibold leading-6 text-gray-900">
                        <h3>{{ group }}s:</h3>
                    </div>
                    <ul class="divide-y divide-gray-100" role="list">
                        <li v-for="credential in groupedCredentialTypes.get(group)" :key="credential" class="flex gap-x-4 px-3 py-5">
                            <CredentialIcon :credentialType="credential.name" class="h-6 w-6 flex-none rounded-full bg-gray-50"></CredentialIcon>

                            <div class="flex min-w-0 flex-row items-center">
                                <span class="text-lg font-semibold leading-6 text-gray-900">{{ credential.id }}.</span>
                                <span class="ml-1 truncate text-sm leading-5 text-gray-800">{{ credential.name }}</span>
                            </div>
                        </li>
                    </ul>
                </div>
            </div>
            <br />
            <!-- <div class="h-full overflow-y-auto shadow-xl">
              <select v-model="selectedDid">
                <option v-for="did in dids.value" :value="did">
                  {{ did }}
                </option>
              </select>
            </div> -->
        </CenterMain>
    </div>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import PageHeader from "~/components/PageHeader.vue";
import CredentialIcon from "~/components/CredentialIcon.vue";
import ActionButton from "~/components/buttons/ActionButton.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import { Listbox, ListboxButton, ListboxLabel, ListboxOption, ListboxOptions } from "@headlessui/vue";
import { CheckIcon, ChevronUpDownIcon } from "@heroicons/vue/20/solid";
import { useTitle } from "@vueuse/core";
import { ref } from "vue";

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

const query = useRoute().query;

const request = decodeRequest(query.request);
console.log("Issuance -> Using request: ", request);

const immediateAccept = ref(false);
console.log("Making issuanceUrl...");
const issuanceUrl = new URL(request);
console.log("issuanceUrl: ", issuanceUrl);

const credentialOffer = issuanceUrl.searchParams.get("credential_offer");
console.log("credentialOffer: ", credentialOffer);

if (credentialOffer == null) {
    throw createError({
        statusCode: 400,
        statusMessage: "Invalid issuance request: No credential_offer",
    });
}

const issuanceParamsJson = JSON.parse(credentialOffer);
console.log("issuanceParamsJson: ", issuanceParamsJson);

console.log("Issuer host...");
const issuer = issuanceParamsJson["credential_issuer"];

let issuerHost: String;
try {
    issuerHost = new URL(issuer).host;
} catch {
    issuerHost = issuer;
}

console.log("Issuer host:", issuerHost);
const credentialList = issuanceParamsJson["credentials"];

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
        failMessage.value = JSON.stringify(e);
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
</script>

<style scoped></style>
