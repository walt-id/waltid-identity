<template>
    <main>

        <PageOverlay :is-open="addingPresentationPolicies" name="Add Verifiable Presentation policies"
                     @close="addingPresentationPolicies = false"
        >
            <SelectablePolicyListView
                :policies="availablePolicies"
                @added-policy="(policy: VerificationPolicyInformation) => vpPolicies.push(policy)"
            />
        </PageOverlay>

        <PageOverlay :is-open="addingCredentialPolicies" name="Add Verifiable Credential policies" @close="addingCredentialPolicies = false"
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

                                <p v-if="credentials.policies"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                >
                                    {{ credential.policies.map((policy) => policy.name).join(", ") }}
                                </p>

                                <p v-else class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No special policies</p>
                            </div>
                            <!--                    <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                            &lt;!&ndash;                        <p class="whitespace-nowrap">
                                                        Due on
                                                        <time :datetime="credential.dueDateTime">{{ credential.dueDate }}</time>
                                                    </p>
                                                    <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                                                        <circle cx="1" cy="1" r="1" />
                                                    </svg>
                                                    <p class="truncate">Created by {{ credential.createdBy }}</p>&ndash;&gt;
                                                </div>-->
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="editCredential(credential.id)"
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

                <button class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100" type="button"
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
                                    {{ policy.args }}
                                </p>

                                <p v-else class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No arguments</p>
                            </div>
                            <!--                    <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                            &lt;!&ndash;                        <p class="whitespace-nowrap">
                                                        Due on
                                                        <time :datetime="credential.dueDateTime">{{ credential.dueDate }}</time>
                                                    </p>
                                                    <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                                                        <circle cx="1" cy="1" r="1" />
                                                    </svg>
                                                    <p class="truncate">Created by {{ credential.createdBy }}</p>&ndash;&gt;
                                                </div>-->
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="editCredential(policy.id)"
                            >Edit policy arguments
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
                                            <button
                                                class="px-3 py-1 text-sm leading-6 text-gray-900 flex items-center gap-1 bg-white w-full hover:bg-gray-50"
                                                @click="removeCredential(policy.id)"
                                            >
                                                <Icon name="carbon:trash-can" />
                                                Delete<span class="sr-only">, {{ policy.name }}</span></button>
                                        </MenuItem>
                                    </MenuItems>
                                </transition>
                            </Menu>
                        </div>
                    </li>
                    <li v-if="vpPolicies.length == 0" class="flex items-center gap-1 gap-x-5 py-3">
                        <Icon name="radix-icons:value-none" />
                        No credential types to verify selected yet.
                    </li>
                </ul>

                <button class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100" type="button"
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
                    <li v-for="credential in credentials" :key="credential.id" class="flex items-center justify-between gap-x-5 py-3">
                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ credential.name }}</p>

                                <p v-if="credentials.policies"
                                   class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset text-green-600"
                                >
                                    {{ credential.policies.map((policy) => policy.name).join(", ") }}</p>

                                <p class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                                    No special policies</p>
                            </div>
                            <!--                    <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                            &lt;!&ndash;                        <p class="whitespace-nowrap">
                                                        Due on
                                                        <time :datetime="credential.dueDateTime">{{ credential.dueDate }}</time>
                                                    </p>
                                                    <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                                                        <circle cx="1" cy="1" r="1" />
                                                    </svg>
                                                    <p class="truncate">Created by {{ credential.createdBy }}</p>&ndash;&gt;
                                                </div>-->
                        </div>
                        <div class="flex flex-none items-center gap-x-2">
                            <button
                                class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                @click="editCredential(credential.id)"
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

                <button class="w-full mt-2 rounded-md bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 shadow-sm hover:bg-indigo-100" type="button"
                        @click="addingCredentialPolicies = true"
                >Add additional global credential policy
                </button>
            </div>



            <div class="border p-3">
                <div v-if="credentials.length >= 1" class="flex gap-3">
                    <button
                        class="rounded-md bg-indigo-600 px-2.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                        type="button"
                    >
                        Send verification request
                    </button>

                    <button
                        class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        type="button"
                    >
                        View HTTP request
                    </button>

                    <button
                        class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        type="button"
                    >
                        View verification flow
                    </button>
                </div>
                <div v-else class="text-gray-500 cursor-not-allowed">
                    Add a credential type to continue with sending this request.
                </div>

                Request is: {{ generatedRequest }}
            </div>
        </div>


        <!--        Fetched: {{ availablePolicies }}-->


        <!--            <div>
            <ul class="list-disc">
                            <li v-for="(name, idx) of Object.keys(data)" :key="name">
                                <TestVerificationComponent :name="name" :information="data[name] as VerificationPolicyInformation"/>
                            </li>
                        </ul>
                        <button @click="refreshNuxtData">Update</button>
        </div>-->

        <!--        <hr class="mt-3 mb-3"/>

                <div class="mt-2 flex-shrink">
                    <div class="font-semibold text-lg">
                        <Icon class="h-5 w-5" name="carbon:prompt-template" />
                        Choose a template:
                    </div>
                    <CredentialTemplateList :actions="actions" class="overflow-y-scroll" />
                </div>-->
    </main>
</template>

<script lang="ts" setup>

import type { VerificationPolicyInformation } from "~/composables/verification";
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/vue";
import type { ComputedRef } from "vue";
import type { IssuanceRequest } from "~/composables/network-request";

const config = useRuntimeConfig();

const { data, pending, error, refresh } = await useFetch<Object>(`${config.public.verifier}/openid4vc/policy-list`);

const addingCredentials = ref(false);
const addingPresentationPolicies = ref(false);
const addingCredentialPolicies = ref(false);

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

let tempIdx1 = 0
let tempIdx2 = 0

availablePolicies.value.map((entry) => ({
    id: ++tempIdx1,
    name: entry.name,
    args: entry.argumentType && entry.argumentType.length > 0 && entry.argumentType[0] != "NONE" ? "xy" : null,
    argTypes: entry.argumentType
})).forEach((entry) => {
    vpPolicies.push(entry)
    globalVcPolicies.push(entry)
})

type VerifyCredential = {
    id: number,
    name: string,
    policies: any,
    // edited: boolean,
    // status: string
}

const credentials: VerifyCredential[] = reactive([]);

const generatedRequest: ComputedRef<IssuanceRequest> = computed(() => {

    const reqRequestCredentials = credentials
    const reqVpPolicies = vpPolicies.map((entry) => {
        return entry.args == null ? entry.name : { policy: entry.name, args: entry.args }
    })
    const reqVcPolicies = globalVcPolicies.map((entry) => {
        return entry.args == null ? entry.name : { policy: entry.name, args: entry.args }
    })

    return {
        url: `${config.public.verifier}/openid4vc/verify`,
        method: "POST",
        body: {
            vp_policies: reqVpPolicies,
            vc_policies: reqVcPolicies,
            request_credentials: reqRequestCredentials
        },
        headers: {

        }
    }
})


function removeCredential(id: number) {
    const rmIdx = credentials.findIndex((value) => value.id == id);
    credentials.splice(rmIdx, 1);
}


useHead({
    title: "Verification | walt.id Portal"
});
</script>
