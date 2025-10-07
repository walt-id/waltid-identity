<template>
  <div>
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
          <div
            v-if="mobileView"
            v-for="(group, index) in groupedCredentialTypes.keys()"
            :key="group.id"
          >
            <div
              v-for="credential in groupedCredentialTypes.get(group)"
              :key="credential"
              :class="{ 'mt-[-85px]': index !== 0 }"
              class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105 cursor-pointer duration-200 w-full sm:w-[400px]"
            >
              <VerifiableCredentialCard
                :credential="{
                  parsedDocument: {
                    type: [credential.name],
                    issuer: {
                      name: issuerHost,
                    },
                  },
                }"
                :isDetailView="true"
              />
            </div>
          </div>
          <div class="w-full flex justify-center gap-5" v-else>
            <button
              v-if="credentialCount > 1"
              @click="index--"
              class="mt-4 text-[#002159] font-bold bg-white"
              :disabled="index === 0"
              :class="{ 'cursor-not-allowed opacity-50': index === 0 }"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-6 w-6"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M15 19l-7-7 7-7"
                />
              </svg>
            </button>
            <VerifiableCredentialCard
              :key="index"
              :credential="{
                parsedDocument: {
                  type: [credentialTypes[index]],
                  issuer: {
                    name: issuerHost,
                  },
                },
              }"
              class="sm:w-[400px]"
            />
            <button
              v-if="credentialCount > 1"
              @click="index++"
              class="mt-4 text-[#002159] font-bold bg-white"
              :disabled="index === credentialCount - 1"
              :class="{
                'cursor-not-allowed opacity-50': index === credentialCount - 1,
              }"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-6 w-6"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  stroke-width="2"
                  d="M9 5l7 7-7 7"
                />
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
          <div
            v-for="(group, index) in groupedCredentialTypes.keys()"
            :key="group.id"
          >
            <div
              v-for="credential in groupedCredentialTypes.get(group)"
              :key="credential"
            >
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
        class="fixed sm:relative bottom-0 w-full p-4 bg-white shadow-md sm:shadow-none sm:flex sm:justify-end sm:gap-4"
      >
        <button
          @click="acceptCredential"
          class="w-full sm:w-44 py-3 mt-4 text-white bg-[#002159] rounded-xl"
        >
          Accept
        </button>
        <button
          @click="navigateTo(`/wallet/${walletId}`)"
          class="w-full sm:w-44 py-3 mt-4 bg-white sm:border sm:border-gray-400 sm:rounded-xl"
        >
          Decline
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {ref} from "vue";
import {useTitle} from "@vueuse/core";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";
import {useIssuance} from "@waltid-web-wallet/composables/issuance.ts";

const query = useRoute().query;
const immediateAccept = ref(false);

const index = ref(0);
const route = useRoute();
const walletId = route.params.wallet;
const mobileView = ref(window.innerWidth < 650);

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
