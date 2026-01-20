<template>
  <CenterMain>
    <div class="flex justify-between items-center">
      <h1 class="text-lg font-semibold">Your keys:</h1>

      <div class="flex justify-between gap-2">
        <button
            class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
            @click="signAndverify"
        >
          <HashtagIcon
              aria-hidden="true"
              class="h-5 w-5 text-white mr-1"
          />
          <span>Sign & Verify</span>
        </button>
        <button
          class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
          @click="importKey"
        >
          <InboxArrowDownIcon
            aria-hidden="true"
            class="h-5 w-5 text-white mr-1"
          />
          <span>Import key</span>
        </button>

        <button
          class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
          @click="generateKey"
        >
          <KeyIcon aria-hidden="true" class="h-5 w-5 text-white mr-1" />
          <span>Generate key</span>
        </button>
      </div>
    </div>

    <ol
      class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-2"
      role="list"
    >
      <li
        v-for="key in keys"
        :key="key"
        class="flex items-center justify-between gap-x-6 py-4"
      >
        <div class="min-w-0">
          <div class="flex items-start gap-x-3">
            <p class="mx-2 text-base font-semibold leading-6 text-gray-900">
              {{ key.algorithm }}
            </p>
          </div>
          <div class="flex items-start gap-x-3">
            <p
              class="mx-2 overflow-x-auto text-base font-normal leading-6 text-gray-500"
            >
                {{ key.name ? key.name : key.keyId.id }}
            </p>
          </div>
        </div>
        <div class="flex flex-none items-center gap-x-4">
          <NuxtLink
            :to="`/wallet/${currentWallet}/settings/keys/${encodeURIComponent(key.keyId.id)}`"
            class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
          >
            View key
          </NuxtLink>
        </div>
      </li>
    </ol>
    <p v-if="keys && keys.length == 0" class="mt-2">No keys.</p>
    <div v-if="response && response != ''" class="mt-6 border p-4 rounded-2xl">
      <p class="text-base font-semibold">Response</p>

      <div
        class="mt-1 space-y-6 border-gray-900/10 pb-6 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0"
      >
        <p
          class="mt-2 flex items-center bg-green-100 p-3 rounded-xl overflow-x-scroll"
        >
          <CheckIcon class="w-5 h-5 mr-1 text-green-600" />
          <span class="text-green-800"
            >Created DID: <code>{{ response }}</code></span
          >
        </p>

        <div class="pt-3 flex justify-end">
          <NuxtLink :to="`/wallet/${currentWallet}/settings/dids`">
            <button
              class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center"
            >
              <ArrowUturnLeftIcon class="h-5 pr-1" />
              Return back
            </button>
          </NuxtLink>
        </div>
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import {ArrowUturnLeftIcon, CheckIcon, HashtagIcon, InboxArrowDownIcon, KeyIcon} from "@heroicons/vue/24/outline";

const currentWallet = useCurrentWallet();

function getKeyLink(keyId) {
  try {
    // If it's a full URL, extract only the key name
    const url = new URL(keyId);
    const segments = url.pathname.split('/');
    const keyName = segments[segments.length - 1]; // last segment of the path
    return `/wallet/${currentWallet}/settings/keys/${keyName}`;
  } catch (e) {
    // If it's not a URL, just use it directly
    return `/wallet/${currentWallet}/settings/keys/${keyId}`;
  }
}
const keys = await useLazyFetch(
  `/wallet-api/wallet/${currentWallet.value}/keys`,
).data;
refreshNuxtData();

function generateKey() {
  navigateTo("keys/generate");
}

function importKey() {
  navigateTo("keys/import");
}

function signAndverify() {
  navigateTo("keys/sign-verify");
}

useHead({
  title: "Keys - walt.id",
});
</script>

<style scoped></style>
