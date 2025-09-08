<template>
  <CenterMain>
    <div>
      <h1 class="block text-lg font-semibold leading-6 text-gray-900">
        Import your DIDs
      </h1>

      <div class="mt-4">
        <label for="did" class="block text-sm font-medium text-gray-700">
          DID
        </label>
        <input
            id="did"
            v-model="did"
            type="text"
            placeholder="did:web:example.com:123..."
            class="block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm
                 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400
                 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
        />
      </div>

      <div class="mt-4">
        <label for="keys" class="block text-sm font-medium text-gray-700">
        Associated key (PEM or JSON)
        </label>
        <textarea
            id="keys"
            v-model="keys"
            rows="6"
            placeholder='-----BEGIN PRIVATE KEY----- ... or JSON {"kty": "OKP", ...}'
            class="block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm
                 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400
                 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
        />
      </div>

      <div class="mt-4">
        <label for="alias" class="block text-sm font-medium text-gray-700">
          Alias
        </label>
        <input
            id="alias"
            v-model="alias"
            type="text"
            placeholder="My DID Alias"
            class="block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm
                 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400
                 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
        />
      </div>

      <div class="mt-6 flex justify-end">
        <button
            class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600
                 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm
                 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
            @click="importDid"
        >
          <DocumentPlusIcon class="h-5 w-5 text-white mr-1" aria-hidden="true" />
          <span>Import DID</span>
        </button>
      </div>

      <div v-if="responseMessage" class="mt-4 p-3 rounded-md shadow-sm"
           :class="responseError ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'">
        {{ responseMessage }}
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import { useCurrentWallet } from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import { DocumentPlusIcon } from "@heroicons/vue/24/outline";

const did = ref("");
const keys = ref("");
const alias = ref("");
const responseMessage = ref("");
const responseError = ref(false);
const currentWallet = useCurrentWallet();

async function importDid() {
  try {
    let parsedKeys: string | object = keys.value;
    try {
      parsedKeys = JSON.parse(keys.value);
    } catch {
      parsedKeys = keys.value;
    }
    const payload = {
      did: did.value,
      keys: parsedKeys,
      alias: alias.value,
    };


   await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids/import`, {
      method: "POST",
      body: payload,
    });

    responseMessage.value = `DID imported successfully`;
  } catch (e) {
    console.error("Failed to import DID:", e);
    responseError.value = true;
    responseMessage.value = `Failed to import DID: ${e?.data?.message || e.message}`;
  }
}
</script>
