<template>
  <div class="bg-[#3e4c597d] sm:bg-white">
    <div
      class="sm:hidden absolute top-3 left-3 cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
      @click="navigateTo({ path: `/wallet/${walletId}` })"
    >
      X
    </div>
    <div
      class="flex flex-col justify-center items-center sm:justify-start h-[100vh]"
    >
      <div v-if="isMobileView" class="w-full flex flex-col items-center">
        <QrCodeScanner v-if="qrCodeDisplay" @request="startRequest" />
        <ManualRequestEntry v-else @request="startRequest" />
        <toggle
          class="mt-10"
          @update:option1-selected="qrCodeDisplay = $event"
          :options="['QR Code', 'Manual']"
        />
      </div>
      <div v-else class="w-2/3 lg:w-1/3 p-4">
        <h1 class="text-2xl font-bold">Present/Receive Credential</h1>
        <p class="text-xs mb-4">Paste offer URL or scan code.</p>
        <toggle
          class="mb-3"
          @update:option1-selected="qrCodeDisplay = $event"
          :option1-selected="false"
          :options="['Scan Code', 'Offer URL']"
        />
        <QrCodeScanner v-if="qrCodeDisplay" @request="startRequest" />
        <ManualRequestEntry v-else @request="startRequest" />
      </div>
    </div>
  </div>
</template>

<script setup>
import ManualRequestEntry from "~/components/scan/ManualRequestEntry.vue";
import {encodeRequest, fixRequest, getSiopRequestType, SiopRequestType} from "@waltid-web-wallet/composables/siop-requests.ts";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import QrCodeScanner from "~/components/scan/QrCodeScanner.vue";
import toggle from "@waltid-web-wallet/components/toggle.vue";

const isMobileView = ref(window.innerWidth < 650);

const route = useRoute();
const walletId = route.params.wallet;
const currentWallet = useCurrentWallet();

const qrCodeDisplay = ref(false);

async function startRequest(request) {
  console.log("Start request:", request);
  request = fixRequest(request);
  const type = getSiopRequestType(request);

  const encoded = encodeRequest(request);
  console.log("Using encoded request:", encoded);

  if (type === SiopRequestType.ISSUANCE) {
    await redirectByOfferType(request, encoded); //navigateTo({ path: `/wallet/${currentWallet.value}/exchange/issuance`, query: { request: encoded } });
  } else if (type === SiopRequestType.PRESENTATION) {
    await navigateTo({
      path: `/wallet/${currentWallet.value}/exchange/presentation`,
      query: { request: encoded },
    });
  } else {
    console.error("Unknown SIOP request type");
    await navigateTo({
      path: `/wallet/${currentWallet.value}/exchange/error`,
      query: { message: btoa("Unknown request type") },
    });
  }
}

function redirectByOfferType(offerUrl, encoded) {
  if (offerUrl.startsWith("openid-vc://")) {
    return navigateTo({
      path: `/wallet/${currentWallet.value}/exchange/entra/issuance`,
      query: { request: encoded },
    });
  } else {
    return navigateTo({
      path: `/wallet/${currentWallet.value}/exchange/issuance`,
      query: { request: encoded },
    });
  }
}

definePageMeta({
  layout: window.innerWidth > 650 ? "desktop-without-sidebar" : false,
});
</script>
<style scoped></style>
