<template>
  <CenterMain class="sm:mt-6 lg:ml-3">
    <div
      class="absolute sm:hidden top-0 right-0 mt-5 mr-2 bg-[#E4E7EB] rounded-full p-1"
    >
      <svg
        @click="logout"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
        stroke-width="1.5"
        stroke="currentColor"
        class="w-6 h-5"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M8.25 9V5.25A2.25 2.25 0 0 1 10.5 3h6a2.25 2.25 0 0 1 2.25 2.25v13.5A2.25 2.25 0 0 1 16.5 21h-6a2.25 2.25 0 0 1-2.25-2.25V15m-3 0-3-3m0 0 3-3m-3 3H15"
        />
      </svg>
    </div>
    <p class="text-center font-bold">{{ user.friendlyName }}</p>
    <div class="bg-white rounded-lg shadow-md p-4 mt-4">
      <div class="font-bold text-lg">DID</div>
      <p class="mt-2">
        Your public Decentralised Identifier (DID) can be openly shared for
        issuers to issue credentials.
      </p>
      <div
        class="bg-gray-100 rounded-lg p-4 mt-4 max-w-md md:max-w-lg lg:max-w-xl"
      >
        <div class="overflow-auto" v-if="dids.length > 0">
          {{ dids[0].did }}
        </div>
        <p v-else>No DID available</p>
      </div>
    </div>
  </CenterMain>
</template>

<script setup>
import {logout} from "~/composables/authentication";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import {useUserStore} from "@waltid-web-wallet/stores/user.ts";
import {storeToRefs} from "pinia";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const currentWallet = useCurrentWallet();
const dids = ref("");

async function loadDids() {
  const response = await useFetch(
    `/wallet-api/wallet/${currentWallet.value}/dids`,
  );
  if (response.data.value && Array.isArray(response.data.value)) {
    dids.value = response.data.value;
  } else {
    dids.value = [];
  }
  refreshNuxtData();
}
loadDids();

definePageMeta({
  layout: window.innerWidth > 650 ? "desktop" : "mobile",
});
</script>
