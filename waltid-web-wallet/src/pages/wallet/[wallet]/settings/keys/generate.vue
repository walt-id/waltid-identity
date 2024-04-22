<template>
  <CenterMain>
    <div class="mt-1 border p-4 rounded-2xl">
      <p class="text-base font-semibold">Generate key</p>
      <div>
        <div
            class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
          <div>
            <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
              <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5"
                     for="format">KMS</label>
              <div class="mt-2 sm:col-span-2 sm:mt-0">
                <select id="format" v-model="data.keyGenerationRequest.type"
                        @change="data.keyGenerationRequest.config = {}"
                        class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                        name="format">
                  <option v-for="option in options" :key="option.keyGenerationRequest[1]"
                          :value="option.keyGenerationRequest[1]">
                    {{ option.keyGenerationRequest[0] }}
                  </option>
                </select>
              </div>
            </div>
            <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
              <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="format">Key
                type</label>
              <div class="mt-2 sm:col-span-2 sm:mt-0">
                <select id="format" v-model="data.type"
                        class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                        name="format">
                  <option
                      v-for="keyType in options.find(option => option.keyGenerationRequest[1] == data.keyGenerationRequest.type)?.keyType"
                      :key="keyType[1]" :value="keyType[1]">
                    {{ keyType[0] }}
                  </option>
                </select>
              </div>
            </div>
          </div>
        </div>
        <div
            v-if="options.find(option => option.keyGenerationRequest[1] == data.keyGenerationRequest.type)?.config?.length"
            class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-gray-900/10 sm:border-t sm:pb-0">
          <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2"
               v-for="config in options.find(option => option.keyGenerationRequest[1] == data.keyGenerationRequest.type)?.config">
            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5">
              {{ config.charAt(0).toUpperCase() + config.slice(1) }}
            </label>
            <template v-if="config === 'signingKeyPem'">
        <textarea v-model="data.keyGenerationRequest.config[config]"
                  class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                  rows="4"></textarea>
            </template>
            <template v-else>
              <input v-model="data.keyGenerationRequest.config[config]"
                     class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                     type="text">
            </template>
          </div>
        </div>
      </div>
    </div>
    <div class="mt-2 flex items-center justify-end gap-x-6">
      <button
          class="inline-flex justify-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
          @click="generateKey">
                <span class="inline-flex place-items-center gap-1">
                    <KeyIcon v-if="!loading" class="w-5 h-5 mr-1"/>
                    <InlineLoadingCircle v-else class="mr-1"/>
                    Generate key
                </span>
      </button>
    </div>
    <div v-if="response && response != ''" class="mt-6 border p-4 rounded-2xl">
      <p class="text-base font-semibold">Response</p>

      <div
          class="mt-1 space-y-6 border-gray-900/10 pb-6 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
        <p class="mt-2 flex items-center bg-green-100 p-3 rounded-xl overflow-x-scroll">
          <CheckIcon class="w-5 h-5 mr-1 text-green-600"/>
          <span class="text-green-800">Generated key: <code>{{ response }}</code></span>
        </p>
        <div class="pt-3 flex justify-end">
          <NuxtLink :to="`/wallet/${currentWallet}/settings/keys`">
            <button
                class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center">
              <ArrowUturnLeftIcon class="h-5 pr-1"/>
              Return back
            </button>
          </NuxtLink>
        </div>
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import {ArrowUturnLeftIcon, CheckIcon, KeyIcon} from "@heroicons/vue/24/outline"
import InlineLoadingCircle from "~/components/loading/InlineLoadingCircle.vue";

const loading = ref(false);
const response = ref("");

const options = ref([
  {
    "keyGenerationRequest": ["JWK", "jwk"],
    "keyType": [["EdDSA_Ed25519", "Ed25519"], ["ECDSA_Secp256k1", "secp256k1"], ["ECDSA_Secp256r1", "secp256r1"], ["RSA", "RSA"]],
    "config": []
  },
  {
    "keyGenerationRequest": ["TSE", "tse"],
    "keyType": [["EdDSA_Ed25519", "Ed25519"], ["RSA", "RSA"]],
    "config": ["server", "accessKey"]
  },
  {
    "keyGenerationRequest": ["OCI REST API", "oci-rest-api"],
    "keyType": [["ECDSA_Secp256r1", "secp256r1"], ["RSA", "RSA"]],
    "config": ["tenancyOcid", "userOcid", "fingerprint", "cryptoEndpoint", "managementEndpoint", "signingKeyPem"]
  }
,
  {
    "keyGenerationRequest": ["OCI", "oci"],
    "keyType": [["ECDSA_Secp256r1", "secp256r1"]],
    "config": ["vaultId", "compartmentId"]
  }

])


const data = reactive({
  keyGenerationRequest: {
    type: options.value[0].keyGenerationRequest[1],
    config: {}
  },
  type: options.value[0].keyType[0][1]
});

const currentWallet = useCurrentWallet()

async function generateKey() {


  const body = {
    backend: data.keyGenerationRequest.type,
    keyType: data.type,
    config: toRaw(data.keyGenerationRequest.config)
  }

  loading.value = true;

  response.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/generate`, {
    method: "POST",
    body: body,
    headers: {
      "Content-Type": "application/json"
    }
  }).catch((e) => {
    alert(e.message);
  });
  loading.value = false;
}

useHead({
  title: "Generate key - walt.id",
});
</script>

<style scoped></style>
