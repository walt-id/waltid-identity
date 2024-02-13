<template>
    <main>

        <PageOverlay :is-open="addingPresentationPolicies"
                     description="Below you can choose from a list of default policies you can apply to the Verifiable Presentation object. Some of the policies require you to configure arguments."
                     name="Add Verifiable Presentation policies"
                     @close="addingPresentationPolicies = false"
        >
            <SelectablePolicyListView
                :policies="availablePolicies"
                @added-policy="(policy: VerificationPolicyInformation) => vpPolicies.push(policy)"
            />
        </PageOverlay>

        <PageOverlay :is-open="addingCredentialPolicies"
                     description="Below you can choose from a list of default policies you can apply to each of the Verifiable Credentials within a presented Verifiable Presentation object. Some of the policies require you to configure arguments."
                     name="Add Verifiable Credential policies"
                     @close="addingCredentialPolicies = false"
        >
            <SelectablePolicyListView
                :policies="availablePolicies"
                @added-policy="(policy: VerificationPolicyInformation) => globalVcPolicies.push(policy)"
            />
        </PageOverlay>

        <PageOverlay :is-open="addingCredentials"
                     description="Select the types of Verifiable Credentials you would like to request from the user."
                     name="Add Verifiable Credential types"
                     @close="addingCredentials = false"
        >
            <div class="mt-2 flex-shrink">
                <div class="font-semibold text-lg">
                    <Icon class="h-5 w-5" name="carbon:prompt-template" />
                    Choose a template:
                </div>
                <CredentialTemplateList :actions="actions" class="overflow-y-scroll" />
            </div>
        </PageOverlay>

        <PageOverlay
            :description="`Below you can choose from a list of default policies that you can apply specifically to all presented Verifiable Credentials of type ${addingSpecificCredentialPolicies?.name ?? 'None'}.`"
            :is-open="addingSpecificCredentialPolicies != null"
            :name="`Add specific credential verification policies: ${addingSpecificCredentialPolicies?.name ?? 'None'}`"
            @close="addingSpecificCredentialPolicies = null"
        >
            <SelectablePolicyListView
                :policies="availablePolicies"
                @added-policy="(policy: VerificationPolicyInformation) => {
                    if (addingSpecificCredentialPolicies.policies == null) {
                        addingSpecificCredentialPolicies.policies = []
                    }
                    addingSpecificCredentialPolicies.policies.push(policy)
                }"
            />
        </PageOverlay>


        <PageOverlay
            :is-open="editingPolicy != null"
            :name="`Policy arguments for ${editingPolicy?.name ?? 'None'}`"
            @close="editingPolicy = null"
        >
            <div v-if="editingPolicy?.argumentType[0] == 'JSON'" class="h-12">
                <LazyInputsJsonInput v-if="editingPolicy?.argumentType[0] == 'JSON'" v-model="editingPolicy.args"></LazyInputsJsonInput>
            </div>
            <div v-else-if="editingPolicy?.argumentType[0] == 'URL'">
                <UrlInput v-model="editingPolicy!!.args" />
            </div>
            <div v-else-if="editingPolicy?.argumentType[0] == 'NUMBER'">
                <NumberInput v-model="editingPolicy!!.args" />
            </div>
            <div v-else-if="editingPolicy?.argumentType[0] == 'DID'">
                <DidInput v-model="editingPolicy!!.args" />
            </div>
            <div v-else-if="editingPolicy?.argumentType[0] == 'DID_ARRAY'">
                <DidArrayInput v-model="editingPolicy!!.args" />
            </div>
            <div v-else>
                Unknown: {{ editingPolicy?.argumentTypes[0] }}
            </div>
        </PageOverlay>

        <HttpRequestOverlay :is-open="showRequest" :request="generatedRequest" @close="showRequest = false" />

        <OidcResultDialog v-if="oidcLink" :link="oidcLink" text="Claim your credentials" @close="oidcLink = null" />

        <PageOverlay :is-open="showExplainer" description="Below you will find a description of how this request will execute."
                     name="Verification flow" @close="showExplainer = false"
        >
            <div class="p-2 shadow border">
                <p class="font-semibold">This request will ask the holder to share the following credentials:</p>
                <ul class="list-inside list-decimal">
                    <li v-for="credential of credentials">{{ credential.name }}</li>
                </ul>
                <p class="text-gray-500 mt-1">Keep in mind that the holder might not share all or any of the credential types you
                    request.</p>
                <p class="text-gray-500">The policy <span class="underline">presentation-definition</span> can verify that at minimum all
                    the requested credential types were shared by the holder.</p>
            </div>

            <div class="p-2 shadow mt-3 border">
                <p class="font-semibold">
                    Then, the following policies will be checked for on <span class="underline">Verifiable Presentation</span>:
                </p>
                <ul class="list-inside list-decimal">
                    <li v-for="policy of vpPolicies">{{ policy.name }}</li>
                </ul>

                <p class="text-gray-500 mt-1">
                    These policies are applied on the Verifiable Presentation, even if the presentation the holder shared is not containing
                    any Verifiable Credentials.
                </p>
            </div>

            <div class="p-2 shadow mt-3 border">
                <p class="font-semibold">
                    Then, a number of policies will be checked on the <span class="underline">Verifiable Credentials</span> within the
                    Verifiable Presentation:
                </p>
                <ul class="list-inside list-decimal">
                    <li v-for="credential of credentials">
                        <span class="font-semibold">{{ credential.name }}</span>
                        <div class="ml-4">
                            <div>
                                <p class="underline">Global policies:</p>
                                <ul class="list-inside list-decimal">
                                    <li v-for="policy of globalVcPolicies">{{ policy.name }}</li>
                                </ul>
                            </div>

                            <div v-if="credential.policies">
                                <p class="underline mt-2">Additionally, specific policies for type <span class="font-semibold"
                                >{{ credential.name }}</span>:</p>
                                <ul class="list-inside list-decimal">
                                    <li v-for="policy of credential.policies">{{ policy.name }}</li>
                                </ul>
                            </div>
                        </div>
                    </li>
                </ul>

                <p class="text-gray-500 mt-1">
                    These policies are applied on the Verifiable Credentials within a Verifiable Presentation.
                    Global policies are applied globally to all Verifiable Credentials within a Verifiable Presentation, while specific policies are only applied to a specific credential type.
                </p>
            </div>
        </PageOverlay>

        <div class="grid grid-cols-2 gap-10">
            <div class="border p-3">
                <p class="text-lg">Request credentials</p>
                <p class="text-gray-600">Select the type of Verifiable Credential you intend to request from the user.</p>


                <ul v-auto-animate="{ duration: 200 }"
                    class="pt-2 pb-6 basis-1/3 divide-y divide-gray-100 shadow px-3 my-1 overflow-y-scroll"
                    role="list"
                >
                    <li v-for="credential in credentials" :key="credential.id" class="flex items-center justify-between gap-x-5 py-3">
                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ credential.name }}</p>

                                <p v-if="credential.policies"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                >
                                    Special policies applied
                                </p>

                                <p v-else class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No special policies
                                </p>
                            </div>
                            <div v-if="credential.policies" class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                                <ul>
                                    <li v-for="specificPolicy of credential.policies" class="list-disc ml-3">
                                        <div class="flex items-center gap-x-2">
                                            {{ specificPolicy.name }} <span v-if="!(specificPolicy.argumentType.length == 1 && specificPolicy.argumentType[0] == 'NONE')">({{ specificPolicy.argumentType.join(", ") }})</span>
                                            <span v-if="specificPolicy.args"
                                                  class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                            >
                                                Arguments supplied
                                            </span>
                                            <span v-else-if="!specificPolicy.args && hasArguments(specificPolicy.argumentType)"
                                                  class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-yellow-600"
                                            >
                                                Missing arguments
                                            </span>
                                            <span v-else
                                                  class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset"
                                            >
                                                No arguments
                                            </span>

                                            <button class="px-1.5"
                                                    @click="editingPolicy = specificPolicy"
                                            >Edit arguments
                                            </button>
                                        </div>
                                    </li>
                                </ul>
                                <!--                                <p class="truncate">
                                                                    {{ credential.policies.map((policy) => policy.name).join(", ") }}
                                                                </p>-->
                            </div>
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="addingSpecificCredentialPolicies = credential"
                            >Edit credential specific policies
                            </button>
                            <Menu as="div" class="relative flex-none">
                                <MenuButton
                                    class="flex items-center gap-1 rounded-md bg-white px-2 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                >
                                    <span class="sr-only">Open options</span>
                                    <Icon aria-hidden="true" class="h-5 w-5" name="material-symbols:event-list-outline-rounded" />
                                </MenuButton>
                                <transition enter-active-class="transition ease-out duration-100"
                                            enter-from-class="transform opacity-0 scale-95"
                                            enter-to-class="transform opacity-100 scale-100"
                                            leave-active-class="transition ease-in duration-75"
                                            leave-from-class="transform opacity-100 scale-100" leave-to-class="transform opacity-0 scale-95"
                                >
                                    <MenuItems
                                        class="absolute right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none"
                                    >
                                        <MenuItem v-slot="{ active }">
                                            <NuxtLink
                                                :class="[active ? 'bg-gray-50' : '', 'px-3 py-1 text-sm leading-6 text-gray-900 flex items-center gap-1']"
                                                :external="true"
                                                :to="`${config.public.credentialRepository}/credentials/${credential.name.toLowerCase()}`"
                                                href="#"
                                                target="_blank"
                                            >
                                                <Icon name="carbon:repo-source-code" />
                                                View in repository<span class="sr-only">, {{ credential.name }}</span></NuxtLink>
                                        </MenuItem>
                                        <MenuItem v-slot="{ active }">
                                            <button
                                                class="px-3 py-1 text-sm leading-6 text-gray-900 flex items-center gap-1 bg-white w-full hover:bg-gray-50"
                                                @click="removeCredential(credential.id)"
                                            >
                                                <Icon name="carbon:trash-can" />
                                                Delete<span class="sr-only">, {{ credential.name }}</span></button>
                                        </MenuItem>
                                    </MenuItems>
                                </transition>
                            </Menu>
                        </div>
                    </li>
                    <li v-if="credentials.length == 0" class="flex items-center gap-1 gap-x-5 py-3">
                        <Icon name="radix-icons:value-none" />
                        No credential types to verify selected yet.
                    </li>
                </ul>

                <button
                    class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100"
                    type="button"
                    @click="addingCredentials = true"
                >Request additional credential type
                </button>
            </div>


            <div class="border p-3">
                <p class="text-lg">Verifiable Presentation Policies</p>
                <p class="text-gray-600">These policies are applied to the Verifiable Presentation (VP).</p>

                <ul v-auto-animate="{ duration: 200 }"
                    class="pt-2 pb-6 basis-1/3 divide-y divide-gray-100 shadow px-3 my-1 overflow-y-scroll"
                    role="list"
                >
                    <li v-for="policy of vpPolicies" :key="policy.id" class="flex items-center justify-between gap-x-5 py-3">
                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ policy.name }}</p>

                                <p v-if="policy.args"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                >
                                    Arguments supplied
                                </p>
                                <p v-else-if="!policy.args && hasArguments(policy.argumentType)"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-yellow-600"
                                >
                                    Missing arguments
                                </p>
                                <p v-else class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No arguments
                                </p>
                            </div>
                            <div v-if="policy.args" class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                                <p class="truncate">Arguments: {{ policy.args }}</p>
                            </div>
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                v-if="hasArguments(policy.argumentType)"
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="editingPolicy = policy"
                            >Edit policy arguments
                            </button>


                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="removeVpPolicy(policy.id)"
                            >
                                <Icon name="carbon:trash-can" />
                                Delete
                            </button>
                        </div>
                    </li>
                    <li v-if="vpPolicies.length == 0" class="flex items-center gap-1 gap-x-5 py-3">
                        <Icon name="radix-icons:value-none" />
                        No Verifiable Presentation policies applied yet.
                    </li>
                </ul>

                <button
                    class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100"
                    type="button"
                    @click="addingPresentationPolicies = true"
                >Add additional presentation policy
                </button>

            </div>


            <div class="border p-3">
                <p class="text-lg">Global Verifiable Credential Policies</p>
                <p class="text-gray-600">These policies are applied each Verifiable Credential (VC) within the Verifiable Presentation.</p>

                <ul v-auto-animate="{ duration: 200 }"
                    class="pt-2 pb-6 basis-1/3 divide-y divide-gray-100 shadow px-3 my-1 overflow-y-scroll"
                    role="list"
                >
                    <li v-for="policy of globalVcPolicies" :key="policy.id" class="flex items-center justify-between gap-x-5 py-3">
                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ policy.name }}</p>

                                <p v-if="policy.args"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                >
                                    Arguments supplied
                                </p>

                                <p v-else-if="!policy.args && hasArguments(policy.argumentType)"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-yellow-600"
                                >
                                    Missing arguments
                                </p>

                                <p v-else class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No arguments</p>
                            </div>
                            <div v-if="policy.args" class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                                <p class="truncate">Arguments: {{ policy.args }}</p>
                            </div>
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                v-if="hasArguments(policy.argumentType)"
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="editingPolicy = policy"
                            >Edit policy arguments
                            </button>


                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="removeGlobalVcPolicy(policy.id)"
                            >
                                <Icon name="carbon:trash-can" />
                                Delete
                            </button>
                        </div>
                    </li>
                    <li v-if="globalVcPolicies.length == 0" class="flex items-center gap-1 gap-x-5 py-3">
                        <Icon name="radix-icons:value-none" />
                        No global Verifiable Credential policies applied yet.
                    </li>
                </ul>

                <button
                    class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100"
                    type="button"
                    @click="addingCredentialPolicies = true"
                >Add additional global credential policy
                </button>
            </div>


            <div class="border p-3 pt-4 shadow-2xl">
                <div v-if="credentials.length >= 1" class="flex gap-3">
                    <button
                        class="rounded-md bg-indigo-600 px-2.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                        type="button"
                        @click="sendRequest"
                    >
                        Send verification request
                    </button>

                    <button
                        class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        type="button"
                        @click="showRequest = true"
                    >
                        View HTTP request
                    </button>

                    <button
                        class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        type="button"
                        @click="showExplainer = true"
                    >
                        View verification flow explainer
                    </button>
                </div>
                <div v-else class="text-gray-500 cursor-not-allowed">
                    Add a credential type to continue with sending this request.
                </div>

                <ErrorDisplay :error="verifierError" title="There was an error with this issuance:" />

                <p v-if="credentials.length >= 1 || vpPolicies.length >= 1 || globalVcPolicies.length >= 1" class="text-xs mt-4 text-gray-500">Request is: <code>{{ generatedRequest.body }}</code></p>
            </div>
        </div>
    </main>
</template>

<script lang="ts" setup>

import type { VerificationPolicyInformation } from "~/composables/verification";
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/vue";
import type { ComputedRef, Ref } from "vue";
import type { HttpRequestType } from "~/composables/network-request";
import NumberInput from "~/components/inputs/NumberInput.vue";
import DidInput from "~/components/inputs/DidInput.vue";
import UrlInput from "~/components/inputs/UrlInput.vue";
import DidArrayInput from "~/components/inputs/DidArrayInput.vue";
import { FetchError } from "ofetch";

const config = useRuntimeConfig();

const { data, pending, error, refresh } = await useFetch<Object>(`${config.public.verifier}/openid4vc/policy-list`);

const addingCredentials = ref(false);
const addingPresentationPolicies = ref(false);
const addingCredentialPolicies = ref(false);

const addingSpecificCredentialPolicies = ref(null);

const showRequest = ref(false);
const showExplainer = ref(false);

const oidcLink: Ref<string | null> = ref(null);

const editingPolicy: Ref<any> = ref(null);

const actions = [
    {
        name: "Add to request",
        icon: "carbon:list-checked",
        action: async (template: string) => {
            credentials.push({
                id: ++idx,
                name: template,
                policies: null
            });
        }
    }
];
let idx = 0;

const availablePolicies = computed(() => {
    const fetchedPolicies = Object.keys(data.value);
    return fetchedPolicies.map((name) => ({
        name: name,
        description: data.value[name].description,
        policyType: data.value[name].policyType,
        argumentType: data.value[name].argumentType
    }));
});

const vpPolicies: any[] = reactive<any[]>([]);
const globalVcPolicies: any[] = reactive<any[]>([]);

/*availablePolicies.value.map((entry) => ({
    id: ++tempIdx1,
    name: entry.name,
    args: entry.argumentType && entry.argumentType.length > 0 && entry.argumentType[0] != "NONE" ? "xy" : null,
    argTypes: entry.argumentType
})).forEach((entry) => {
    vpPolicies.push(entry)
    // globalVcPolicies.push(entry)
})*/

type VerifyCredential = {
    id: number,
    name: string,
    policies: any,
    // edited: boolean,
    // status: string
}

const credentials: VerifyCredential[] = reactive([]);

const generatedRequest: ComputedRef<HttpRequestType> = computed(() => {

    const reqRequestCredentials = credentials.map((entry) => {
        return !entry.policies ? entry.name : {
            credential: entry.name,
            policies: entry.policies?.map((specificPolicy) => {
                return !specificPolicy.args ? specificPolicy.name : {
                    policy: specificPolicy.name,
                    args: specificPolicy.args
                };
            })
        };
    });
    const reqVpPolicies = vpPolicies.map((entry) => {
        return entry.args == null ? entry.name : { policy: entry.name, args: entry.args };
    });
    const reqVcPolicies = globalVcPolicies.map((entry) => {
        return entry.args == null ? entry.name : { policy: entry.name, args: entry.args };
    });

    return {
        url: `${config.public.verifier}/openid4vc/verify`,
        method: "POST",
        body: {
            vp_policies: reqVpPolicies,
            vc_policies: reqVcPolicies,
            request_credentials: reqRequestCredentials
        },
        headers: {
            "content-type": "application/json"
        }
    };
});

const verifierError: Ref<FetchError<any> | null> = ref(null);
const verifying = ref(false);

async function sendRequest() {
    verifying.value = true;

    const req: HttpRequestType = generatedRequest.value;

    const { data, pending, error, refresh } = await useFetch<string>(req.url, {
        method: "POST",
        body: req.body,
        headers: req.headers
    });

    verifierError.value = error.value;

    oidcLink.value = data.value;

    verifying.value = false;
}

function removeCredential(id: number) {
    const rmIdx = credentials.findIndex((value) => value.id == id);
    credentials.splice(rmIdx, 1);
}

function removeVpPolicy(id: number) {
    const rmIdx = vpPolicies.findIndex((value) => value.id == id);
    vpPolicies.splice(rmIdx, 1);
}

function removeGlobalVcPolicy(id: number) {
    const rmIdx = globalVcPolicies.findIndex((value) => value.id == id);
    globalVcPolicies.splice(rmIdx, 1);
}


useHead({
    title: "Verification | walt.id Portal"
});
</script>
