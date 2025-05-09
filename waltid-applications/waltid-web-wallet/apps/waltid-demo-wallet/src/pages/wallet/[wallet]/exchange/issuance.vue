<template>
  <div v-if="hidePinInputScreen">
    <CenterMain>
      <h1 class="mb-2 text-2xl text-center font-bold sm:mt-5">
        New {{ credentialCount === 1 ? "Credential" : "Credentials" }}
      </h1>
      <LoadingIndicator v-if="immediateAccept" class="my-6 mb-12 w-full">
        Receiving {{ credentialCount }}
        credential(s)...
      </LoadingIndicator>

      <div v-if="failed">
        <div class="my-6 text-center">
          <h2 class="text-xl font-bold">Failed to receive credential</h2>
          <p class="text-red-500">{{ failMessage }}</p>
        </div>
      </div>

      <div>
        <div class="my-10">
          <div v-if="mobileView" v-for="(group, index) in groupedCredentialTypes.keys()" :key="group.id">
            <div v-for="credential in groupedCredentialTypes.get(group)" :key="credential"
              :class="{ 'mt-[-85px]': index !== 0 }"
              class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105 cursor-pointer duration-200 w-full sm:w-[400px]">
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
          <div class="w-full flex justify-center gap-5" v-else>
            <button v-if="credentialCount > 1" @click="index--" class="mt-4 text-[#002159] font-bold bg-white"
              :disabled="index === 0" :class="{ 'cursor-not-allowed opacity-50': index === 0 }">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <VerifiableCredentialCard :key="index" :credential="{
              parsedDocument: {
                type: [credentialTypes[index]],
                issuer: {
                  name: issuerHost,
                },
              },
            }" class="sm:w-[400px]" />
            <button v-if="credentialCount > 1" @click="index++" class="mt-4 text-[#002159] font-bold bg-white"
              :disabled="index === credentialCount - 1" :class="{
                'cursor-not-allowed opacity-50': index === credentialCount - 1,
              }">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
          <div v-if="!mobileView" class="text-center text-gray-500 mt-2">
            {{ index + 1 }} of {{ credentialCount }}
          </div>
        </div>
        <div class="sm:w-[80%] md:w-[60%] mx-auto">
          <div class="text-sm font-bold text-gray-500">Credential Offered</div>
          <hr class="my-2 border-gray-200" />
          <div v-for="(group, index) in groupedCredentialTypes.keys()" :key="group.id">
            <div v-for="credential in groupedCredentialTypes.get(group)" :key="credential">
              <div class="text-black-800">{{ credential.name }}</div>
              <div v-if="issuerHost" class="text-sm text-gray-400">
                from {{ issuerHost }}
              </div>
              <hr class="my-2 border-gray-200" />
            </div>
          </div>
        </div>
      </div>
    </CenterMain>
    <div v-if="!failed" class="w-full sm:max-w-2xl sm:mx-auto">
      <div
        class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end sm:gap-4">
        <button @click="acceptCredential" class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
          Accept
        </button>
        <button @click="navigateTo(`/wallet/${walletId}`)"
          class="w-full sm:w-44 py-3 mt-4 bg-white sm:border sm:border-gray-400 sm:rounded-xl">
          Decline
        </button>
      </div>
    </div>
  </div>
  <div v-else>
    <CenterMain class="flex flex-col items-center">
      <h1 class="mb-2 text-2xl text-center font-bold sm:mt-5">Pin Verification</h1>
      <svg class="my-5" width="59" height="59" viewBox="0 0 59 59" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M57 20.4672V23.0632C57 24.2272 56.6899 25.3695 56.1024 26.3689C55.5149 27.3682 54.6721 28.1874 53.6633 28.7394L35.3921 38.7336M2 20.4672V23.0632C1.99996 24.2272 2.31012 25.3695 2.89759 26.3689C3.48505 27.3682 4.3279 28.1874 5.33667 28.7394L23.608 38.7336M23.608 38.7336L26.4905 37.1577C27.4154 36.6516 28.4495 36.3867 29.5 36.3867C30.5505 36.3867 31.5846 36.6516 32.5095 37.1577L35.3949 38.7336L48.5385 45.9256M23.608 38.7336L10.4615 45.9256M57 50.553C57 52.2629 56.3314 53.9027 55.1413 55.1117C53.9511 56.3208 52.337 57 50.6538 57H8.34615C6.66305 57 5.04888 56.3208 3.85875 55.1117C2.66861 53.9027 2 52.2629 2 50.553V20.0202C1.99996 18.8562 2.31012 17.7139 2.89759 16.7146C3.48505 15.7152 4.3279 14.896 5.33667 14.344L26.4905 2.77102C27.4154 2.26493 28.4495 2 29.5 2C30.5505 2 31.5846 2.26493 32.5095 2.77102L53.6633 14.344C54.6717 14.8958 55.5143 15.7145 56.1017 16.7133C56.6891 17.7121 56.9995 18.8538 57 20.0174V50.553Z"
          stroke="url(#paint0_linear_2452_16774)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
        <defs>
          <linearGradient id="paint0_linear_2452_16774" x1="2" y1="2.88997" x2="60.3275" y2="6.75259"
            gradientUnits="userSpaceOnUse">
            <stop offset="0.00970874" stop-color="#0573F0" />
            <stop offset="1" stop-color="#03449E" />
          </linearGradient>
        </defs>
      </svg>
      <div class="font-bold text-[#323F4B] mt-2">Enter Pin</div>
      <div class="text-sm text-[#3E4C59] mt-1 text-center">
        <div>Please provide the pin you received per mail to</div>
        <div>complete the issuance.</div>
      </div>
      <div class="mt-5">
        <div>Pin</div>
        <input class="px-4 py-3 border border-[#CBD2D9] rounded-md w-[300px] sm:w-[400px]" />
      </div>
      <div class="w-full sm:max-w-2xl sm:mx-auto sm:mt-5">
        <div class="fixed sm:relative bottom-0 left-0 w-full p-4 bg-white sm:flex sm:justify-center sm:gap-4">
          <button @click="() => { hidePinInputScreen = true }"
            class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl">
            Submit Pin
          </button>
          <button @click="navigateTo(`/wallet/${walletId}`)"
            class="w-full sm:w-44 py-3 mt-4 bg-white sm:border sm:border-gray-400 sm:rounded-xl">
            Cancel
          </button>
        </div>
      </div>
    </CenterMain>
  </div>
</template>

<script lang="ts" setup>
import { ref } from "vue";
import { useTitle } from "@vueuse/core";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";
import { useIssuance } from "@waltid-web-wallet/composables/issuance.ts";

const query = useRoute().query;
const immediateAccept = ref(false);

const index = ref(0);
const route = useRoute();
const walletId = route.params.wallet;
const mobileView = ref(window.innerWidth < 650);

const hidePinInputScreen = ref(false);

const {
  acceptCredential,
  failed,
  failMessage,
  credentialTypes,
  issuerHost,
  credentialCount,
  groupedCredentialTypes,
} = await useIssuance(query);

if (query.accept) {
  immediateAccept.value = true;
  acceptCredential();
}

useTitle(`Claim credentials - walt.id`);
definePageMeta({
  layout: window.innerWidth > 650 ? "desktop-without-sidebar" : false,
});
</script>
