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

            <div v-else class="my-10 mb-40 sm:mb-10 overflow-scroll">
                <div v-if="mobileView" v-for="(credential, credentialIdx) in matchedCredentials" :key="credentialIdx">
                    <div :class="{ 'mt-[-85px]': credentialIdx !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                    cursor-pointer duration-200">
                        <VerifiableCredentialCard :credential="credential" />
                    </div>
                </div>
                <div class="w-full flex justify-center gap-5" v-else>
                    <button v-if="matchedCredentials.length > 1" @click="index--"
                        class="mt-4 text-[#002159] font-bold bg-white" :disabled="index === 0"
                        :class="{ 'cursor-not-allowed opacity-50': index === 0 }">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                        </svg>
                    </button>
                    <VerifiableCredentialCard :key="index" :credential="{
                        document: matchedCredentials[index].document
                    }" class="sm:w-[400px]" />
                    <button v-if="matchedCredentials.length > 1" @click="index++"
                        class="mt-4 text-[#002159] font-bold bg-white"
                        :disabled="index === matchedCredentials.length - 1"
                        :class="{ 'cursor-not-allowed opacity-50': index === matchedCredentials.length - 1 }">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                            stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                        </svg>
                    </button>
                </div>
                <div v-if="!mobileView" class="text-center text-gray-500 mt-2">
                    {{ index + 1 }} of {{ matchedCredentials.length }}
                </div>
                <div class="sm:w-[80%] md:w-[60%] mx-auto">
                    <div class="text-gray-500 mt-8 sm:mt-0">
                        {{ matchedCredentials.length > 1 ? 'Credentials' : 'Credential' }} to present
                    </div>
                    <hr class="mt-1 mb-2 border-gray-200" />
                    <div v-for="credential in matchedCredentials" :key="credential.id">
                        <div v-if="credential.disclosures && selection[credential.id]">
                            <div class="sm:flex justify-between items-center">
                                <div @click="toggleDisclosure(credential.id)"
                                    :class="{ 'font-semibold': disclosureModalState[credential.id] }"
                                    class="text-black-800 flex gap-3 items-center pt-2 cursor-pointer">
                                    {{
                                        (
                                            credential.parsedDocument ?? parseJwt(credential.document).vc ??
                                            parseJwt(credential.document)
                                        ).type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                                    }}
                                    <svg v-if="disclosureModalState[credential.id]" width="16" height="16"
                                        viewBox="0 0 17 10" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path d="M16 1L8.5 8.5L1 1" stroke="black" stroke-width="1.5"
                                            stroke-linecap="round" stroke-linejoin="round" />
                                    </svg>
                                    <svg v-else width="16" height="16" viewBox="0 0 10 18" fill="none"
                                        xmlns="http://www.w3.org/2000/svg">
                                        <path d="M1.25 1.5L8.75 9L1.25 16.5" stroke="#323F4B" stroke-width="1.5"
                                            stroke-linecap="round" stroke-linejoin="round" />
                                    </svg>
                                </div>
                                <div>
                                    <div v-if="disclosureModalState[credential.id]" class="flex items-center gap-2">
                                        Disclose All
                                        <input type="checkbox"
                                            class="h-4 w-4 rounded border-gray-300 text-primary-400 focus:ring-primary-500"
                                            @click="disclosures[credential.id] = $event.target?.checked ? parseDisclosures(credential.disclosures as string) : []"
                                            :checked="disclosures[credential.id]?.length === parseDisclosures(credential.disclosures)?.length" />
                                    </div>
                                    <div v-else>
                                        {{ disclosures[credential.id] === undefined ?
                                            'Disclosing 0 of ' + parseDisclosures(credential.disclosures).length +
                                            ' attributes' : disclosures[credential.id]?.length ===
                                                parseDisclosures(credential.disclosures).length ? 'Disclosing all attributes'
                                                : `Disclosing ${disclosures[credential.id].length}
                                        of ${parseDisclosures(credential.disclosures).length} attributes` }}
                                    </div>
                                </div>
                            </div>
                            <div v-if="disclosureModalState[credential.id]">
                                <div class="flex items-center mt-1">
                                    <div class="flex-1 border-t border-gray-300"></div>
                                    <div class="mx-3 text-gray-400">Attributes to disclose</div>
                                    <div class="flex-1 border-t border-gray-300"></div>
                                </div>
                                <div class="mt-1 divide-y px-10 divide-gray-100">
                                    <div v-for="(disclosure, disclosureIdx) in parseDisclosures(credential.disclosures)"
                                        :key="disclosureIdx" class="relative flex items-start py-1">
                                        <div class="min-w-0 flex-1 text-sm leading-6">
                                            <label :for="`disclosure-${credential.id}-${disclosure[0]}`"
                                                class="select-none text-gray-900">
                                                <span class="text-black-800">
                                                    {{
                                                        disclosure[1].charAt(0).toUpperCase() + disclosure[1].slice(1)
                                                    }}
                                                </span>
                                            </label>
                                        </div>
                                        <div class="ml-3 flex h-6 items-center">
                                            <input :id="`disclosure-${credential.id}-${disclosure[0]}`"
                                                :name="`disclosure-${disclosure[0]}`"
                                                class="h-4 w-4 rounded border-gray-300 text-primary-400 focus:ring-primary-500"
                                                type="checkbox"
                                                :checked="disclosures[credential.id] && disclosures[credential.id].find((elem: Array<string>) => elem[0] === disclosure[0])"
                                                @click="$event.target?.checked ? addDisclosure(credential.id, disclosure) : removeDisclosure(credential.id, disclosure)" />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="text-black-800" v-else>
                            {{
                                (
                                    credential.parsedDocument ?? parseJwt(credential.document).vc ??
                                    parseJwt(credential.document)
                                ).type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                            }}
                        </div>
                        <hr class="my-2 border-gray-200" />
                    </div>
                </div>
            </div>
        </CenterMain>
        <div v-if="!failed && matchedCredentials.length" class="w-full sm:max-w-2xl sm:mx-auto">
            <div class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end
                        sm:gap-4">
                <button @click="acceptPresentation" class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
                    {{ matchedCredentials.length > 1 ? 'Disclose All' : 'Disclose' }}
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
import { parseJwt } from "@waltid-web-wallet/utils/jwt.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import { parseDisclosures } from "@waltid-web-wallet/composables/disclosures.ts";
import { usePresentation } from "@waltid-web-wallet/composables/presentation.ts";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";

const immediateAccept = ref(false);
const mobileView = ref(window.innerWidth < 650);

const route = useRoute();
const query = route.query;
const walletId = route.params.wallet;

const { matchedCredentials, disclosures, selection, index, disclosureModalState, toggleDisclosure, addDisclosure, removeDisclosure, acceptPresentation, failed } = await usePresentation(query);

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
