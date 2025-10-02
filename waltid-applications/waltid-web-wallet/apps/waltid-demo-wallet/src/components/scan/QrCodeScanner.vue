<template>
    <div class="flex flex-col items-center">
        <!-- Video feed -->
        <div :class="[(!videoStarted && isLoading) || !noError ? 'hidden' : '']"
            class="flex place-content-center place-items-center mb-6">
            <VideoCameraIcon v-if="!scanned" class="absolute h-7 w-7 animate-ping" />
            <video id="scanner-video" ref="scannerVideo" class="border"></video>
        </div>

        <!-- Menu bar -->
        <div v-if="noError" class="text-center sm:hidden">
            <p class="font-semibold text-[#E6F6FF]">Scan Code</p>
            <p class="text-[#E6F6FF]">Scan QR codes to receive or present credentials</p>
        </div>

        <!-- Loading indicator -->
        <LoadingIndicator class="text-[#E6F6FF]" v-if="isLoading || (!videoStarted && noError)">Camera initializing...
        </LoadingIndicator>

        <!-- Error indicator -->
        <div v-if="!noError" class="px-4 py-5 sm:px-6">
            <div class="flex flex-row gap-1.5 place-items-center">
                <VideoCameraSlashIcon class="w-5 h-5 text-[#E6F6FF] sm:text-black" />
                <p class="font-semibold text-[#E6F6FF] sm:text-black sm:text-sm">{{ error.title }}</p>
            </div>
            <p class="text-[#E6F6FF] sm:text-black sm:text-sm">{{ error.message }}</p>
        </div>
    </div>
</template>

<script setup>
import QrScanner from "qr-scanner";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import {VideoCameraIcon, VideoCameraSlashIcon} from "@heroicons/vue/24/outline";
import {isSiopRequest} from "@waltid-web-wallet/composables/siop-requests.ts";

const emit = defineEmits(["request"]);

const isLoading = ref(true);
const videoStarted = ref(false);

const scanned = ref(false);

const error = ref({});
const noError = ref(true);

const scannerVideo = ref(null);
let qrScanner = null;

function throwError(newError) {
    isLoading.value = false;
    error.value = newError;
    noError.value = false;
    console.error(error.value.title);
    console.error(error.value.message);
}

async function startVideo() {
    if (await QrScanner.hasCamera()) {
        try {
            qrScanner = new QrScanner(
                scannerVideo.value,
                (result) => {
                    scanned.value = true;
                    console.log(result);
                    const scannedText = result.data;

                    if (isSiopRequest(scannedText)) {
                        qrScanner.stop();
                        emit("request", scannedText);
                    } else {
                        console.log("Invalid QR");
                    }
                },
                {
                    highlightScanRegion: true,
                    highlightCodeOutline: true,
                    returnDetailedScanResult: true,
                },
            );
            isLoading.value = false;
            await qrScanner.start();
            videoStarted.value = true;
        } catch (exception) {
            throwError({
                title: "Could not start camera",
                message: "Could not initialize your camera. Please make sure you have accepted the camera permission in your browser.",
            });
        }
    } else {
        throwError({
            title: "No camera",
            message: "You do not have any camera available.",
        });
    }
}

if (process.client) {
    onMounted(async () => {
        await startVideo();
    });
}
</script>

<style scoped></style>
