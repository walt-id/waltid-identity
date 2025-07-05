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
          You don't have any credentials matching this presentation definition
          in your wallet.
        </span>
      </div>

      <div v-else class="my-10 mb-40 sm:mb-10 overflow-scroll">
        <div v-if="mobileView" v-for="(credential, credentialIdx) in matchedCredentials" :key="credentialIdx">
          <div :class="{ 'mt-[-85px]': credentialIdx !== 0 }"
            class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105 cursor-pointer duration-200">
            <VerifiableCredentialCard :credential="credential" />
          </div>
        </div>
        <div class="w-full flex justify-center gap-5" v-else>
          <button v-if="matchedCredentials.length > 1" @click="index--" class="mt-4 text-[#002159] font-bold bg-white"
            :disabled="index === 0" :class="{ 'cursor-not-allowed opacity-50': index === 0 }">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
              stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <VerifiableCredentialCard :key="index" :credential="{
            document: matchedCredentials[index].document,
          }" class="sm:w-[400px]" />
          <button v-if="matchedCredentials.length > 1" @click="index++" class="mt-4 text-[#002159] font-bold bg-white"
            :disabled="index === matchedCredentials.length - 1" :class="{
              'cursor-not-allowed opacity-50':
                index === matchedCredentials.length - 1,
            }">
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
            {{
              matchedCredentials.length > 1 ? "Credentials" : "Credential"
            }}
            to present
          </div>
          <hr class="mt-1 mb-2 border-gray-200" />
          <div v-for="credentialType in Object.keys(groupedCredentialsByType)">
            <div v-if="groupedCredentialsByType[credentialType].length > 1"
              class="border border-[#E4E7EB] rounded-xl p-4 mb-4">
              <div class="text-[#616E7C]">
                You have {{ groupedCredentialsByType[credentialType].length }} matching credentials of the same type;
                please choose one.
              </div>
              <div v-for="credential in groupedCredentialsByType[credentialType]" class="mt-2 flex gap-2">
                <input type="radio" :id="`${credentialType}-grouped-credential-${credential.id}`"
                  :checked="selection[credential.id]"
                  @click="groupedCredentialsByType[credentialType].forEach(c => selection[c.id] = c.id === credential.id)"
                  class="mt-1 h-4 w-4 text-[#0573F0]" />
                <CredentialDisclosure :credential="credential" :disclosureModalState="disclosureModalState"
                  :disclosures="disclosures" :selection="selection" :toggleDisclosure="toggleDisclosure"
                  :addDisclosure="addDisclosure" :removeDisclosure="removeDisclosure" />
              </div>
            </div>
            <div v-else>
              <CredentialDisclosure :credential="groupedCredentialsByType[credentialType][0]"
                :disclosureModalState="disclosureModalState" :disclosures="disclosures" :selection="selection"
                :toggleDisclosure="toggleDisclosure" :addDisclosure="addDisclosure"
                :removeDisclosure="removeDisclosure" />
            </div>
          </div>
        </div>
      </div>
    </CenterMain>
    <div v-if="!failed && matchedCredentials.length" class="w-full sm:max-w-2xl sm:mx-auto">
      <div
        class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end sm:gap-4">
        <button @click="acceptPresentation" class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
          {{ matchedCredentials.length > 1 ? "Disclose All" : "Disclose" }}
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
import { usePresentation } from "@waltid-web-wallet/composables/presentation.ts";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";

const immediateAccept = ref(false);
const mobileView = ref(window.innerWidth < 650);

const route = useRoute();
const query = route.query;
const walletId = route.params.wallet;

const {
  disclosures,
  selection,
  disclosureModalState,
  toggleDisclosure,
  addDisclosure,
  removeDisclosure,
  matchedCredentials,
  index,
  acceptPresentation,
  failed,
} = await usePresentation(query);
const groupedCredentialsByType = computed(() => {
  const groups: Record<string, {
    id: string;
    document: string;
    parsedDocument?: string;
    disclosures?: string;
  }[]> = {};
  for (const credential of matchedCredentials) {
    const types = (credential.parsedDocument ?? parseJwt(credential.document).vc ?? parseJwt(credential.document)).type;
    const typeKey = Array.isArray(types) && types.length > 0 ? types.at(-1) : "unknown";
    if (!groups[typeKey]) {
      groups[typeKey] = [];
    }
    groups[typeKey].push(credential);
  }
  return groups;
});
watch(groupedCredentialsByType, (newValue) => {
  for (const type in newValue) {
    if (newValue[type].length > 1) {
      for (const credential of newValue[type]) {
        selection.value[credential.id] = false;
      }
      selection.value[newValue[type][0].id] = true;
    }
  }
}, { immediate: true });

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
