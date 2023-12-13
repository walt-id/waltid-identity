<template>
    <div class="flex flex-col items-center">
        <!-- Menu bar -->
        <div v-if="noError" class="text-center">
            <p class="font-semibold">QR code scanner</p>
            <p>Scan your credential presentation requests and issuance offers:</p>
        </div>
        <div v-if="videoStarted && noError">
            <div class="inline-flex flex-row justify-between gap-3 p-2">
                <WaltButton :icon="ChevronUpDownIcon"> Choose camera </WaltButton>
                <WaltButton :icon="ArrowPathRoundedSquareIcon"> Switch direction </WaltButton>
                <WaltButton :icon="LightBulbIcon"> Flashlight </WaltButton>
                <WaltButton>Inverted</WaltButton>
                <div class="flex place-content-center place-items-center">
                    <LightBulbIcon class="w-5 h-5 absolute text-gray-800" />
                    <XMarkIcon class="w-8 h-8 absolute text-gray-600" />
                </div>
            </div>
        </div>

        <!-- Loading indicator -->
        <LoadingIndicator v-if="isLoading || (!videoStarted && noError)">Camera initializing...</LoadingIndicator>

        <!-- Video feed -->
        <div :class="[(!videoStarted && isLoading) || !noError ? 'hidden' : '']" class="flex place-content-center place-items-center">
            <VideoCameraIcon v-if="!scanned" class="absolute h-7 w-7 animate-ping" />
            <video id="scanner-video" ref="scannerVideo" class="border"></video>
        </div>

        <!-- Error indicator -->
        <div v-if="!noError" class="bg-white px-4 py-5 sm:px-6 shadow">
            <div class="flex flex-row gap-1.5 place-items-center">
                <VideoCameraSlashIcon class="w-5 h-5" />
                <p class="font-semibold">{{ error.title }}</p>
            </div>
            <p>{{ error.message }}</p>
        </div>
    </div>
</template>

<script setup>
import QrScanner from "qr-scanner";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import { ArrowPathRoundedSquareIcon, ChevronUpDownIcon, LightBulbIcon, VideoCameraIcon, VideoCameraSlashIcon, XMarkIcon } from "@heroicons/vue/24/outline";
import WaltButton from "~/components/buttons/WaltButton.vue";
import { isSiopRequest } from "~/composables/siop-requests";

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
        console.log("Starting video");
        console.log("Camera list", await QrScanner.listCameras());
        try {
            console.log("Creating QR scanner...");
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
            console.log("Starting QR scanner...");
            isLoading.value = false;
            await qrScanner.start();
            videoStarted.value = true;
            console.log("Started QR scanner!");
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

/*async function startVideo(tries = 0) {
    if (tries >= 1) {
        console.error("QR Scanner: Giving up after too many tries.")
        throwError({
            title: "Could not start camera",
            message: "Could not initialize your camera. Please make sure you have accepted the camera permission in your browser."
        })
        return
    }

    if (await QrScanner.hasCamera()) {
        console.log("Starting video")
        console.log("Camera list", await QrScanner.listCameras())
        try {
            console.log("Creating QR scanner...")
            qrScanner = new QrScanner(scannerVideo.value,
                result => {
                    scanned.value = true
                    console.log(result)
                    const scannedText = result.data

                    if (isSiopRequest(scannedText)) {
                        qrScanner.stop()
                        emit("request", scannedText)
                    } else {
                        console.log("Invalid QR")
                    }
                },
                {
                    highlightScanRegion: true,
                    highlightCodeOutline: true,
                    returnDetailedScanResult: true
                });
            console.log("Starting QR scanner...")
            isLoading.value = false
            await qrScanner.start();
            videoStarted.value = true
            console.log("Started QR scanner!")
        } catch (exception) {
            console.error("QR Error:", exception)
            console.log("Restarting...")
            window.setTimeout(async () => {
                await startVideo(++tries)
            }, 500)
        }
    } else {
        throwError({
            title: "No camera",
            message: "You do not have any camera available."
        })
    }
}*/

if (process.client) {
    onMounted(async () => {
        await startVideo();
    });
}

//
//console.log(await QrScanner.listCameras(true))

/*
qrScanner.hasFlash(); // check whether the browser and used camera support turning the flash on; async.
qrScanner.isFlashOn(); // check whether the flash is on
qrScanner.turnFlashOn(); // turn the flash on if supported; async
qrScanner.turnFlashOff(); // turn the flash off if supported; async
qrScanner.toggleFlash(); // toggle the flash if supported; async.
 */
</script>
<!--<script setup>
import CenterMain from "~/components/CenterMain.vue";

function logErrors(promise) {
    promise.catch(console.error)
}

function paintOutline(detectedCodes, ctx) {
    for (const detectedCode of detectedCodes) {
        const [firstPoint, ...otherPoints] = detectedCode.cornerPoints

        ctx.strokeStyle = "red";

        ctx.beginPath();
        ctx.moveTo(firstPoint.x, firstPoint.y);
        for (const {x, y} of otherPoints) {
            ctx.lineTo(x, y);
        }
        ctx.lineTo(firstPoint.x, firstPoint.y);
        ctx.closePath();
        ctx.stroke();
    }
}
</script>-->

<style scoped></style>
