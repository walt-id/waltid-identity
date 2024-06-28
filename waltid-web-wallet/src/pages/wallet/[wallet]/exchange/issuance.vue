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
                    display-text="Reject" icon="heroicons:x-mark" type="button"
                    @click="navigateTo(`/wallet/${currentWallet}`)" />

                <div class="group flex">
                    <ActionButton :class="[
                        failed
                            ? 'animate-pulse bg-red-600 hover:scale-105 hover:bg-red-700 focus:outline focus:outline-offset-2 focus:outline-red-700'
                            : 'bg-green-600 hover:scale-105 hover:animate-pulse hover:bg-green-700 focus:animate-none focus:outline-green-700',
                    ]" :disabled="(selectedDid === null || selectedDid === undefined) && !(dids && dids?.length === 1)"
                        :failed="failed"
                        class="inline-flex items-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus:outline focus:outline-offset-2 disabled:bg-gray-200 disabled:cursor-not-allowed"
                        display-text="Accept" icon="heroicons:check" type="button" @click="acceptCredential" />
                    <span v-if="failed"
                        class="group-hover:opacity-100 transition-opacity bg-gray-800 px-1 text-sm text-gray-100 rounded-md absolute -translate-x-1/2 opacity-0 m-4 mx-auto">
                        {{ failMessage }}
                    </span>
                </div>
            </template>
        </PageHeader>

        <CenterMain>
            <h1 class="mb-2 text-2xl font-semibold">Issuance</h1>

            <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full"> Receiving {{ credentialCount }}
                credential(s)...
            </LoadingIndicator>

            <div class="flex col-2">
                <div v-if="!pendingDids" class="relative w-full">
                    <Listbox v-if="dids?.length !== 1" v-model="selectedDid" as="div">
                        <ListboxLabel class="block text-sm font-medium leading-6 text-gray-900">Select DID:
                        </ListboxLabel>

                        <div class="relative mt-2">
                            <ListboxButton v-if="selectedDid !== null"
                                class="relative w-full cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 sm:text-sm sm:leading-6 h-9">
                                <span class="flex items-center">
                                    <p class="truncate font-bold">{{ selectedDid?.alias }}</p>
                                    <span class="ml-3 block truncate">{{ selectedDid?.did }}</span>
                                </span>
                                <span
                                    class="pointer-events-none absolute inset-y-0 right-0 ml-3 flex items-center pr-2">
                                    <ChevronUpDownIcon aria-hidden="true" class="h-5 w-5 text-gray-400" />
                                </span>
                            </ListboxButton>

                            <transition leave-active-class="transition ease-in duration-100"
                                leave-from-class="opacity-100" leave-to-class="opacity-0">
                                <ListboxOptions
                                    class="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm">
                                    <ListboxOption v-for="did in dids" :key="did?.did" v-slot="{ active, selectedDid }"
                                        :value="did" as="template">
                                        <li
                                            :class="[active ? 'bg-indigo-600 text-white' : 'text-gray-900', 'relative cursor-default select-none py-2 pl-3 pr-9']">
                                            <div class="flex items-center">
                                                <p class="italic">{{ did.alias }}</p>
                                                <span
                                                    :class="[selectedDid ? 'font-semibold' : 'font-normal', 'ml-3 block truncate']">{{
                                                        did.did }}</span>
                                            </div>
                                            <span v-if="selectedDid"
                                                :class="[active ? 'text-white' : 'text-indigo-600', 'absolute inset-y-0 right-0 flex items-center pr-4']">
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
                                <span v-if="selectedDid != null"
                                    class="text-sm text-blue-700 max-w-xs overflow-x-scroll mr-3 truncate">
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
                    <div
                        class="top-0 z-10 border-y border-b-gray-200 border-t-gray-100 bg-gray-50 px-3 py-1.5 text-sm font-semibold leading-6 text-gray-900">
                        <h3>{{ group }}s:</h3>
                    </div>
                    <ul class="divide-y divide-gray-100" role="list">
                        <li v-for="credential in groupedCredentialTypes.get(group)" :key="credential"
                            class="flex gap-x-4 px-3 py-5">
                            <CredentialIcon :credentialType="credential.name"
                                class="h-6 w-6 flex-none rounded-full bg-gray-50">
                            </CredentialIcon>

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


// import { resolvePresentationRequest} from "~/pages/wallet/[wallet]/exchange/presentation.vue";

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
const credential_issuer = await $fetch(`${issuer}/.well-known/openid-credential-issuer`, {
    headers: {"ngrok-skip-browser-warning": "true"}
  } )
console.log(credential_issuer.credential_configurations_supported)
console.log(credential_issuer.credential_configurations_supported)
console.log(credential_issuer.credential_configurations_supported)

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


    const credentialOffer = await resolveCredentialOffer(request);
    if (credentialOffer == null) {
        throw createError({
            statusCode: 400,
            statusMessage: "Invalid issuance request: No credential_offer",
        });
    }
    // Add here an If/Else statement to handle authorize code and preautorized code, we have a weird error in model, thats why the following statement:
    if (JSON.stringify(credentialOffer).includes(`"pre-authorized_code":null`) &&  JSON.stringify(credentialOffer).includes(`"issuer_state":null`)){ //then its preauthorized
        console.log("its preauthorized flow");
        try {
            await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/useOfferRequestPreAuth?did=${did}`, {
                mode: 'cors',
                redirect: 'follow',
                method: "POST",
                body: request,
            });
            navigateTo(`/wallet/${currentWallet.value}`);
        } catch (e) {
            if (e.data.includes("vp_token")) {
                console.log("It is a VP Token request using Dynamic Credential Requst: ", e?.data);
                let req = e?.data.split("dynamicCredentialRequest=")[1];
                let req2 = req.split('"})]')[0].split('"')[1];
                console.log(`REQ: ${req}`);
                console.log(`REQ2: ${req2}`);

                // let reqHardCode = "openid://localhost/?state=4b9cae8d-51da-4c14-ad05-5563e5e22103&client_id=http%3A%2F%2Flocalhost%3A7002&redirect_uri=http%3A%2F%2Flocalhost%3A7002%2Fdirect_post&response_type=vp_token&response_mode=direct_post&scope=openid&nonce=ddd77a8d-0301-48a0-a296-674a60e7e0e4&request=eyJ0eXBlIjoiand0IiwiYWxnIjoiRVMyNTYiLCJraWQiOiJWenBpWEZrVi1VdXZhSXB2RkRUS2N6NHMySGlidlIxVEZLTzFLXzlKWUtJIn0.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjcwMDIiLCJhdWQiOiJkaWQ6a2V5OnoyZG16RDgxY2dQeDhWa2k3SmJ1dU1tRllyV1BnWW95dHlrVVozZXlxaHQxajlLYnE4ZFFDWnF6YW1Db0NHWnJYbWtxd0hWWXdiNGZmWlN5N0MzZ2pYY3B1TXNBTDloc212RVpDQXdNaUZKNlN3OTlpajhSY1NCa0NuYXhuWUtiR3ZNUkVDVnJwRk1hZkZQZG9Vc2JvSkh4cWg2VG16c0hpaUtMdVV4THo3OFFZNFpZRmUiLCJub25jZSI6ImRkZDc3YThkLTAzMDEtNDhhMC1hMjk2LTY3NGE2MGU3ZTBlNCIsInN0YXRlIjoiNGI5Y2FlOGQtNTFkYS00YzE0LWFkMDUtNTU2M2U1ZTIyMTAzIiwicHJlc2VudGF0aW9uX2RlZmluaXRpb24iOnsiaWQiOiI3MGZjN2ZhYi04OWMwLTQ4MzgtYmE3Ny00ODg2ZjQ3YzM3NjEiLCJpbnB1dF9kZXNjcmlwdG9ycyI6W3siaWQiOiJlM2Q3MDBhYS0wOTg4LTRlYjYtYjljOS1lMDBmNGIyN2YxZDgiLCJjb25zdHJhaW50cyI6eyJmaWVsZHMiOlt7InBhdGgiOlsiJC50eXBlIl0sImZpbHRlciI6eyJjb250YWlucyI6eyJjb25zdCI6IlZlcmlmaWFibGVQb3J0YWJsZURvY3VtZW50QTEifSwidHlwZSI6ImFycmF5In19XX19XSwiZm9ybWF0Ijp7Imp3dF92YyI6eyJhbGciOlsiRVMyNTYiXX0sImp3dF92cCI6eyJhbGciOlsiRVMyNTYiXX19fSwiY2xpZW50X2lkIjoiaHR0cDovL2xvY2FsaG9zdDo3MDAyIiwicmVkaXJlY3RfdXJpIjoiaHR0cDovL2xvY2FsaG9zdDo3MDAyL2RpcmVjdF9wb3N0IiwicmVzcG9uc2VfdHlwZSI6InZwX3Rva2VuIiwicmVzcG9uc2VfbW9kZSI6ImRpcmVjdF9wb3N0Iiwic2NvcGUiOiJvcGVuaWQifQ.ub1RXOjXp9SBSH8VARnEja7FhcQeDGOXt_J1Kq_thdlg0VckZbYkje30TunsiF1UOrayQ2MLOpr3JU48e1TeLQ&presentation_definition=%7B%22id%22%3A%22Q9OmuRSSF0O%22%2C%22input_descriptors%22%3A%5B%7B%22id%22%3A%22VerifiablePortableDocumentA1%22%2C%22format%22%3A%7B%22jwt_vc%22%3A%7B%22alg%22%3A%5B%22ES256%22%5D%7D%7D%2C%22constraints%22%3A%7B%22fields%22%3A%5B%7B%22path%22%3A%5B%22%24.type%22%5D%2C%22filter%22%3A%7B%22type%22%3A%22array%22%2C%22pattern%22%3A%22VerifiablePortableDocumentA1%22%7D%7D%5D%7D%7D%5D%7D"
                // let reqBase64HardCode = "b3BlbmlkNHZwOi8vYXV0aG9yaXplP3Jlc3BvbnNlX3R5cGU9dnBfdG9rZW4mY2xpZW50X2lkPWh0dHBzJTNBJTJGJTJGdmVyaWZpZXIucG9ydGFsLndhbHQtdGVzdC5jbG91ZCUyRm9wZW5pZDR2YyUyRnZlcmlmeSZyZXNwb25zZV9tb2RlPWRpcmVjdF9wb3N0JnN0YXRlPXA1RTlycTlHNExXVCZwcmVzZW50YXRpb25fZGVmaW5pdGlvbl91cmk9aHR0cHMlM0ElMkYlMkZ2ZXJpZmllci5wb3J0YWwud2FsdC10ZXN0LmNsb3VkJTJGb3BlbmlkNHZjJTJGcGQlMkZwNUU5cnE5RzRMV1QmY2xpZW50X2lkX3NjaGVtZT1yZWRpcmVjdF91cmkmY2xpZW50X21ldGFkYXRhPSU3QiUyMmF1dGhvcml6YXRpb25fZW5jcnlwdGVkX3Jlc3BvbnNlX2FsZyUyMiUzQSUyMkVDREgtRVMlMjIlMkMlMjJhdXRob3JpemF0aW9uX2VuY3J5cHRlZF9yZXNwb25zZV9lbmMlMjIlM0ElMjJBMjU2R0NNJTIyJTdEJm5vbmNlPWI0YzUyYzBlLTZiNjItNDU5YS1hMmFhLTZmZjZhYjU2NmI3ZCZyZXNwb25zZV91cmk9aHR0cHMlM0ElMkYlMkZ2ZXJpZmllci5wb3J0YWwud2FsdC10ZXN0LmNsb3VkJTJGb3BlbmlkNHZjJTJGdmVyaWZ5JTJGcDVFOXJxOUc0TFdU"
                
                const encoded = encodeRequest(req2);
                console.log("Using encoded request:", encoded);
                await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/presentation`, query: { request: encoded } });

            } else {
                failed.value = true;

                let errorMessage = e?.data.startsWith("{") ? JSON.parse(e.data) : e.data ?? e;
                
                errorMessage = errorMessage?.message ?? errorMessage;
                
                console.log("Error: ", e?.data);
                
                failMessage.value = errorMessage;     

                alert("Error occurred while trying to receive credential: " + failMessage.value);

                throw e;
            }

            // navigateTo(`/wallet/${currentWallet.value}/exchange/presentation/request=${reqBase64}`);

            // try {
            //         console.log("RESOLVING request", req);
            //         const response = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolvePresentationRequest`, {
            //             method: "POST",
            //             body: req
            //         });
            //         console.log(response);
            //         alert("Dynamic Credentia Request in use");
            //         navigateTo(`/wallet/${currentWallet.value}/exchange/presentation?request=${req}}`);
            //         return response;
            //     } catch (e) {
            //         failed.value = true;
            //         throw e;
            //     }        
    
        }
    } else if (JSON.stringify(credentialOffer).includes(`"pre-authorized_code":null`)) { //its authorized
        console.log("its authorized flow");
        navigateTo(`/wallet-api/wallet/${currentWallet.value}/exchange/useOfferRequestAuth?did=${did}&offer=${request}`, { external: true });
    } else {
        throw createError({
            statusCode: 400,
            statusMessage: "Invalid issuance request: No credential_offer",
        }); 
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
