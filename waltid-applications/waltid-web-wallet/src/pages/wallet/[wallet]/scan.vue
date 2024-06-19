<template>
    <div class="bg-[#3e4c597d]">
        <div class="absolute top-3 left-3 cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
            @click="navigateTo({ path: `/wallet/${walletId}` })">X</div>
        <div class="flex flex-col justify-center items-center h-[100vh]">
            <QrCodeScanner v-if="qrCodeDisplay" @request="startRequest" />
            <ManualRequestEntry v-else @request="startRequest" />
            <toggle class="mt-10" @update:option1-selected="qrCodeDisplay = $event" :options="['QR Code', 'Manual']" />
        </div>
    </div>
</template>

<script setup>
import ManualRequestEntry from "~/components/scan/ManualRequestEntry.vue";
import { encodeRequest, fixRequest } from "~/composables/siop-requests";
import QrCodeScanner from "~/components/scan/QrCodeScanner.vue";
import toggle from "~/components/toggle.vue";

const route = useRoute();
const walletId = route.params.wallet;
const currentWallet = useCurrentWallet();

const qrCodeDisplay = ref(true);

async function startRequest(request) {
    console.log("Start request:", request);
    request = fixRequest(request);
    const type = getSiopRequestType(request);

    const encoded = encodeRequest(request);
    console.log("Using encoded request:", encoded);

    if (type === SiopRequestType.ISSUANCE) {
        await redirectByOfferType(request, encoded)//navigateTo({ path: `/wallet/${currentWallet.value}/exchange/issuance`, query: { request: encoded } });
    } else if (type === SiopRequestType.PRESENTATION) {
        await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/presentation`, query: { request: encoded } });
    } else {
        console.error("Unknown SIOP request type");
        await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/error`, query: { message: btoa("Unknown request type") } });
    }
}

function redirectByOfferType(offerUrl, encoded) {
    if (offerUrl.startsWith("openid-vc://")) {
        return navigateTo({ path: `/wallet/${currentWallet.value}/exchange/entra/issuance`, query: { request: encoded } });
    } else {
        return navigateTo({ path: `/wallet/${currentWallet.value}/exchange/issuance`, query: { request: encoded } });
    }
}

definePageMeta({
    layout: false,
});
</script>
<style scoped></style>
