<template>
    <CenterMain>
        <BackButton />
        <div>
            <QrCodeScanner @request="startRequest" />

            <div class="relative my-8">
                <div aria-hidden="true" class="absolute inset-0 flex items-center">
                    <div class="w-full border-t border-gray-300" />
                </div>
                <div class="relative flex justify-center">
                    <span class="bg-white px-2 text-sm text-gray-500">or</span>
                </div>
            </div>

            <ManualRequestEntry @request="startRequest" />
        </div>
    </CenterMain>
</template>

<script setup>
import QrCodeScanner from "~/components/scan/QrCodeScanner.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import CenterMain from "~/components/CenterMain.vue";
import ManualRequestEntry from "~/components/scan/ManualRequestEntry.vue";
import { encodeRequest, fixRequest } from "~/composables/siop-requests";

const currentWallet = useCurrentWallet()

async function startRequest(request) {
    console.log("Start request:", request);
    request = fixRequest(request);
    const type = getSiopRequestType(request);

    const encoded = encodeRequest(request);
    console.log("Using encoded request:", encoded);

    if (type === SiopRequestType.ISSUANCE) {
        await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/issuance`, query: { request: encoded } });
    } else if (type === SiopRequestType.PRESENTATION) {
        await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/presentation`, query: { request: encoded } });
    } else {
        console.error("Unknown SIOP request type");
        await navigateTo({ path: `/wallet/${currentWallet.value}/exchange/error`, query: { message: btoa("Unknown request type") } });
    }
}
</script>
<style scoped></style>
