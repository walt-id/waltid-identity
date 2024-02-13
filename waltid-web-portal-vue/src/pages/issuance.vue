<template>
    <PageOverlay :isOpen="editing != null" :name="`Editor: ${editing?.name ?? 'None'}`"
                 description="Below you can edit the credential data and mapping for the selected credential. Your changes are applied in the current issuance process and the HTTP request generation."
                 @close="editing = null"
    >
        <p class="font-semibold">Credential data</p>
        <ClientOnly>
            <LazyMonacoEditor v-if="editing != null" v-model="editing!!.data" class="h-60" lang="json" />
        </ClientOnly>

        <p class="font-semibold">Credential mapping</p>
        <ClientOnly>
            <LazyMonacoEditor v-if="editing != null" v-model="editing!!.data" class="h-60" lang="json" />
        </ClientOnly>
    </PageOverlay>

    <OidcResultDialog v-if="oidcLink" :link="oidcLink" text="Claim your credentials" @close="oidcLink = null" />


    <PageOverlay :is-open="showRequest" description="Below you can find the HTTP request generated with the options you applied."
                 name="HTTP Request Viewer" @close="showRequest = false"
    >
        <div>
            <p>
                HTTP: <span class="font-semibold">{{ currentIssuanceRequest.method }}</span> <code><span>{{ currentIssuanceRequest.url
                }}</span></code>
            </p>
            <p>
                Body:
                <ClientOnly>
                    <highlightjs :code="`${JSON.stringify(currentIssuanceRequest.body, null, 4)}`" class="" language="json" />
                </ClientOnly>
            </p>
        </div>

        <hr />

        <p class="font-semibold">As curl request:</p>

        <p>
            <code class="">
                {{ fetchToCurl(currentIssuanceRequest) }}
            </code>
        </p>
    </PageOverlay>

    <PageOverlay :is-open="openSettings" description="Below you can edit various settings used during the issuance process." name="Settings"
                 @close="openSettings = false"
    >
        <div>
            <label class="block text-sm font-semibold leading-6 text-gray-900" for="comment">Issuance key</label>
            <div class="mt-1">
                <ClientOnly>
                    <LazyMonacoEditor v-if="openSettings" :model-value="issuanceKey" class="h-40" lang="json" />
                </ClientOnly>
            </div>
        </div>

        <div>
            <label class="block text-sm font-semibold leading-6 text-gray-900" for="comment">Issuance DID</label>
            <div class="mt-1">
                <input v-model="issuanceDid"
                       class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                >
            </div>
        </div>
    </PageOverlay>

    <main class="flex flex-col">

        <div>
            <p class="font-semibold flex items-center gap-1">
                <Icon class="h-5" name="heroicons:list-bullet" />
                Selected credentials for issuance:
            </p>
            <ul v-auto-animate="{ duration: 200 }" class="pt-2 pb-6 basis-1/3 divide-y divide-gray-100 shadow px-3 my-1 overflow-y-scroll"
                role="list"
            >
                <li v-for="credential in credentials" :key="credential.id" class="flex items-center justify-between gap-x-5 py-3">
                    <div class="min-w-0">
                        <div class="flex items-start gap-x-3">
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ credential.name }}</p>
                            <p :class="[statuses[credential.status], 'rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset']">
                                {{ credential.status }}</p>
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
                    <div class="flex flex-none items-center gap-x-4">
                        <button
                            class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                            @click="editCredential(credential.id)"
                        >Edit credential<span class="sr-only">, {{ credential.name }}</span></button
                        >
                        <Menu as="div" class="relative flex-none">
                            <MenuButton class="-m-2.5 block p-2.5 text-gray-500 hover:text-gray-900 bg-white">
                                <span class="sr-only">Open options</span>
                                <Icon aria-hidden="true" class="h-5 w-5" name="heroicons:ellipsis-vertical" />
                            </MenuButton>
                            <transition enter-active-class="transition ease-out duration-100"
                                        enter-from-class="transform opacity-0 scale-95"
                                        enter-to-class="transform opacity-100 scale-100" leave-active-class="transition ease-in duration-75"
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
                    No credentials to issue selected yet.
                </li>
                <li v-if="credentials.length == 0" class="gap-x-5 py-3">
                    Select a credential template below to start.
                </li>
            </ul>
        </div>

        <div class="flex justify-end mt-2 gap-2">
            <button
                class="rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 flex items-center gap-0.5"
                type="button" @click="openSettings = true"
            >
                <Icon class="h-4 w-4" name="heroicons:cog-6-tooth" />
                Settings
            </button>

            <button v-if="issuing"
                    class="rounded-md inline-flex items-center bg-green-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-green-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                    disabled
                    type="button"
            >
                <Spinner class="p-1 mr-1" />
                Issuing...
            </button>
            <div v-else-if="credentials.length >= 1" class="flex gap-2">
                <button
                    class="rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 flex items-center gap-0.5"
                    @click="showRequest = true"
                >
                    <Icon class="h-4 w-8" name="logos:curl" />
                    Show request
                </button>
                <button
                    class="rounded-md bg-green-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-green-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                    type="button"
                    @click="sendIssueRequest"
                >
                    <span v-if="credentials.length >= 2">Issue {{ credentials.length }} credentials <span class="text-xs">(<span
                        class="underline"
                    >Batch Issuance</span>)</span></span>
                    <span v-else>Issue single credentials</span>
                </button>
            </div>
            <span v-else>
                <button
                    class="flex items-center gap-1 rounded-md bg-gray-500 px-3 py-2 text-sm font-semibold text-gray-100 shadow-sm hover:bg-green-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                    disabled
                    type="button"
                >
                    <Icon class="h-5 w-5" name="heroicons:exclamation-circle" />
                Select a credential to continue.
                </button>
            </span>
        </div>


        <div v-if="issuanceError" class="rounded-md bg-red-50 p-4 my-2">
            <div class="flex">
                <div class="flex-shrink-0">
                    <Icon aria-hidden="true" class="h-5 w-5 text-red-400" name="heroicons:x-circle" />
                </div>
                <div class="ml-3">
                    <h3 class="text-sm font-medium text-red-800">There was an error with this issuance:</h3>
                    <div class="mt-2 text-sm text-red-700">
                        <ul class="list-disc space-y-1 pl-5" role="list">
                            <li>{{ issuanceError.name }}: {{ issuanceError.statusCode }} {{ issuanceError.statusMessage }}</li>
                            <li>Message: {{ issuanceError.message }}</li>
                            <li v-if="issuanceError.response">Response: {{ issuanceError.response }}</li>

                            <li v-if="issuanceError.data">
                                {{ issuanceError.data?.startsWith("{") ? JSON.parse(issuanceError.data)?.message ?? JSON.parse(issuanceError.data) : issuanceError.data
                                }}
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>

        <div class="mt-2 flex-shrink">
            <div class="font-semibold text-lg">
                <Icon class="h-5 w-5" name="carbon:prompt-template" />
                Choose a template:
            </div>
            <CredentialTemplateList :actions="actions" class="overflow-y-scroll" />
        </div>
    </main>
</template>

<script lang="ts" setup>
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/vue";
import { FetchError } from "ofetch";
import fetchToCurl from "fetch-to-curl";
import type { Ref } from "vue";
import type { IssuanceRequest } from "~/composables/network-request";

const config = useRuntimeConfig();

const editing: Ref<EditableCredential | null> = ref(null);

const openSettings = ref(false);
const issuanceKey = ref(JSON.stringify({
    "type": "local",
    "jwk": "{\"kty\":\"OKP\",\"d\":\"mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI\",\"crv\":\"Ed25519\",\"kid\":\"Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8\",\"x\":\"T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM\"}"
}, null, 4));
const issuanceDid = ref("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp");

const showRequest = ref(false);

const oidcLink: Ref<string | null> = ref(null);

const issuanceError: Ref<FetchError<any> | null> = ref(null);

const issuing = ref(false);

const currentIssuanceRequest: Ref<IssuanceRequest> = computed(() => {
    const batch = credentials.length > 1;

    let url: string = `${config.public.issuer}/openid4vc/jwt/` + (batch ? "issueBatch" : "issue");

    const credentialRequestBodies = credentials.map((credential) => ({
            issuanceKey: JSON.parse(issuanceKey.value),
            issuerDid: issuanceDid.value,
            vc: JSON.parse(credential.data),
            mapping: JSON.parse(credential.mapping)
        })
    );

    return {
        url: url,
        method: "POST",
        body: batch ? credentialRequestBodies : credentialRequestBodies[0],
        headers: {
            "content-type": "application/json"
        }
    };
});

async function sendIssueRequest() {
    issuing.value = true;

    const req: IssuanceRequest = currentIssuanceRequest.value;

    const { data, pending, error, refresh } = await useFetch<string>(req.url, {
        method: "POST",
        body: req.body,
        headers: req.headers
    });

    issuanceError.value = error.value;

    oidcLink.value = data.value;

    issuing.value = false;
}

function editCredential(id: number) {
    editing.value = credentials.find((value) => value.id == id) ?? null;
}

function removeCredential(id: number) {
    const rmIdx = credentials.findIndex((value) => value.id == id);
    credentials.splice(rmIdx, 1);
}

type EditableCredential = {
    id: number,
    name: string,
    data: string,
    mapping: string,
    edited: boolean,
    status: string
}

const actions = [
    {
        name: "Issue",
        icon: "carbon:certificate-check",
        action: async (template: string) => {

            const { data: credentialData } = await useFetch<string>(`${config.public.credentialRepository}/api/vc/${template}`);
            const { data: mappingData } = await useFetch<string>(`${config.public.credentialRepository}/api/mapping/${template}`);

            credentials.push(
                {
                    id: ++idx,
                    name: template,
                    data: JSON.stringify(credentialData.value!!, null, 4),
                    mapping: JSON.stringify(mappingData.value!!, null, 4),
                    edited: false,
                    status: "Template"
                }
            );
        }
    }
];

let idx = 0;

const statuses = {
    Edited: "text-green-700 bg-green-50 ring-green-600/20",
    Template: "text-blue-500 bg-gray-50 ring-blue-600/10",
    Custom: "text-yellow-800 bg-yellow-50 ring-yellow-600/20"
};

const credentials: EditableCredential[] = reactive([]);

useHead({
    title: "Issuance | walt.id Portal"
});
</script>
