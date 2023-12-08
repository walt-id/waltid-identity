<template>
    <div>
        <PageHeader>
            <template v-slot:title>
                <div class="ml-3">
                    <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:leading-9">Present</h1>
                    <p>
                        requested by <span class="underline">{{ verifierHost }}</span>
                    </p>
                </div>
            </template>

            <template v-if="!immediateAccept" v-slot:menu>
                <ActionButton
                    class="inline-flex focus:outline focus:outline-red-700 focus:outline-offset-2 items-center rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-red-700 hover:scale-105 hover:animate-pulse focus:animate-none"
                    display-text="Reject"
                    icon="heroicons:x-mark"
                    type="button"
                    @click="navigateTo(`/wallet/${currentWallet.value}`)"
                />

                <div class="group flex">
                    <ActionButton
                        :class="[
                            failed
                                ? 'bg-red-600 animate-pulse focus:outline focus:outline-red-700 focus:outline-offset-2 hover:bg-red-700 hover:scale-105'
                                : 'bg-green-600 focus:outline-green-700 hover:bg-green-700 hover:scale-105 hover:animate-pulse focus:animate-none',
                        ]"
                        :failed="failed"
                        class="inline-flex focus:outline focus:outline-offset-2 items-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm"
                        display-text="Accept"
                        icon="heroicons:check"
                        type="button"
                        @click="acceptPresentation"
                    />
                    <!-- tooltip -->
                    <span v-if="failed"
                          class="group-hover:opacity-100 transition-opacity bg-gray-800 px-1 text-sm text-gray-100 rounded-md absolute -translate-x-1/2 opacity-0 m-4 mx-auto"
                    >
                        {{ failMessage }}
                    </span>
                </div>
            </template>
        </PageHeader>
        <CenterMain>
            <h1 class="text-2xl font-semibold mb-2">Presentation</h1>

            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full"> Presenting credential(s)...</LoadingIndicator>

            <!--            <p class="mb-1">The following credentials will be presented:</p>-->

            <fieldset class="mt-3">
                <legend class="text-base font-semibold leading-6 text-gray-900">Select credentials to present:</legend>

                <div>Selected: {{ selectedCredentialIds.length }}</div>

                <div class="mt-2 divide-y divide-gray-200 border-b border-t border-gray-200">
                    <div v-for="(credential, credentialIdx) in matchedCredentials" :key="credentialIdx"
                         class="relative flex items-start py-4"
                    >
                        <div class="mr-3 pt-2 flex h-6 items-center">
                            <input :id="`credential-${credential.id}`" v-model="selection[credential.id]"
                                   :name="`credential-${credential.id}`"
                                   class="h-6 w-6 rounded border-gray-300 text-primary-400 focus:ring-primary-400"
                                   type="checkbox"
                            />
                        </div>

                        <div class="min-w-0 flex-1 text-sm leading-6">
                            <div class="font-medium select-none text-gray-900">
                                <label :for="`credential-${credential.id}`">
                                    <VerifiableCredentialCard
                                        :class="[selection[credential.id] == true ? 'shadow-xl shadow-primary-400' : 'shadow-2xl']"
                                        :credential="credential.parsedDocument"
                                        :show-id="true"
                                    />

                                </label>
                            </div>
                            <div v-if="credential.disclosures && selection[credential.id]" class="mt-6 border rounded-xl p-2">
                                <fieldset>
                                    <legend class="text-base font-semibold leading-6 text-gray-900">Selectively disclosable attributes
                                    </legend>
                                    <div class="mt-1 divide-y divide-gray-200 border-b border-t border-gray-200">
                                        <div v-for="(disclosure, disclosureIdx) in parseDisclosures(credential.disclosures)"
                                             :key="disclosureIdx" class="relative flex items-start py-1"
                                        >
                                            <div class="min-w-0 flex-1 text-sm leading-6">
                                                <label :for="`disclosure-${credential.id}-${disclosure[0]}`"
                                                       class="select-none font-medium text-gray-900"
                                                >
                                                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                                                        <div class="min-w-[19vw]">{{ disclosureIdx + 1 }}. <span class="font-semibold">{{ disclosure[1] }}</span></div>
                                                        <div class="grow-0">
                                                            {{ disclosure[2] }}
                                                        </div>
                                                    </div>
                                                </label>
                                            </div>
                                            <div class="ml-3 flex h-6 items-center">
                                                <input :id="`disclosure-${credential.id}-${disclosure[0]}`"
                                                       :name="`disclosure-${disclosure[0]}`"
                                                       class="h-4 w-4 rounded border-gray-300 text-primary-400 focus:ring-primary-500"
                                                       type="checkbox"
                                                       @click="$event.target.checked ? addDisclosure(credential.id, disclosure) : removeDisclosure(credential.id, disclosure) "
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </fieldset>

                            </div>

                        </div>
                    </div>
                </div>
            </fieldset>

            <Disclosure>
                <DisclosureButton class="py-2">
                    <ButtonsWaltButton class="bg-gray-400 text-white">View presentation definition JSON</ButtonsWaltButton>
                </DisclosureButton>
                <DisclosurePanel class="text-gray-500 overflow-x-scroll pb-2">
                    <pre>{{ presentationDefinition }}</pre>
                </DisclosurePanel>
            </Disclosure>

        </CenterMain>
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
import { encodeDisclosure, parseDisclosures } from "../../../../composables/disclosures";


const currentWallet = useCurrentWallet();

async function resolvePresentationRequest(request) {
    try {
        console.log("RESOLVING request", request);
        const response = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolvePresentationRequest`, { method: "POST", body: request });
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

const verifierHost = new URL(presentationParams.get("response_uri") ?? "").host;
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


const matchedCredentials = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/matchCredentialsForPresentationDefinition`, {
    method: "POST",
    body: presentationDefinition
});

const selection = ref({});
const selectedCredentialIds = computed(() => Object.entries(selection.value).filter((it) => it[1]).map((it) => it[0]))

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
        //did: String, // todo: choose DID of shared credential
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
</script>

<style scoped></style>
