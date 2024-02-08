<template>
    <CenterMain>
        <BackButton />
        <div>
            <h2 class="text-lg font-semibold leading-7 text-gray-900">Export key: {{ keyId }}</h2>
            <p class="mt-1 max-w-2xl text-sm leading-6 text-gray-600">We allow you to export your keypair, however, make sure you keep it safe, so that you cannot be impersonated.</p>
            Be careful of what information of this page you share.
        </div>

        <div v-if="loadPrivateKey && !exportedKey">
            <p class="font-semibold">Never share your private key!</p>

            <button
                class="bg-yellow-500 hover:bg-yellow-400 focus-visible:outline-yellow-600 inline-flex justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                @click="loadExportedKey"
            >
                <span class="inline-flex place-items-center gap-1">
                    <ExclamationTriangleIcon class="w-5 h-5" />
                    Export private key
                </span>
            </button>
        </div>

        <div v-if="exportedKey" class="my-2">
            <span v-if="loadPrivateKey" class="font-bold text-red-600">NEVER SHARE YOUR PRIVATE KEY.</span>

            <div class="my-2">
                <pre v-if="format == 'JWK'">{{ JSON.stringify(JSON.parse(exportedKey), null, 2) }}</pre>
                <pre v-else>{{ exportedKey }}</pre>
            </div>

            <qrcode-vue v-if="exportedKey.length <= 4296" :value="exportedKey" level="L" size="300"></qrcode-vue>
            <p v-else>
                Unfortunately, this Verifiable Credential is too big to be viewable as QR code (credential size is {{ exportedKey.length }} characters, but the maximum a QR code can hold is 4296).
            </p>

            <span v-if="loadPrivateKey" class="font-bold text-red-600">NEVER SHARE YOUR PRIVATE KEY.</span>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import { ExclamationTriangleIcon } from "@heroicons/vue/24/outline";
import QrcodeVue from "qrcode.vue";

const route = useRoute();

const format = route.query.format;
const loadPrivateKey = route.query.loadPrivateKey == "true";

const exportedKey = ref("");

const keyId = route.params.keyId;

const currentWallet = useCurrentWallet()

async function loadExportedKey() {
    const data = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/load/${keyId}?format=${format}&loadPrivateKey=${loadPrivateKey}`);

    exportedKey.value = JSON.stringify(data);
}

if (process.client && !loadPrivateKey) {
    loadExportedKey();
}
</script>

<style scoped></style>
