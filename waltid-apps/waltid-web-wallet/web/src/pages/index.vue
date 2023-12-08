<template>
    <div>
        <WalletPageHeader />
        <CenterMain>
            <div>
                <h2 class="text-lg font-semibold">Select wallet</h2>

                <ul v-if="wallets">
                    <li v-for="wallet in wallets?.wallets" class="flex items-center justify-between gap-x-6 py-5">
                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ wallet.name }}</p>
                                <p class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">{{ wallet.permission }}</p>
                            </div>
                            <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                                <p class="whitespace-nowrap">
                                    Added on <time :datetime="wallet.addedOn">{{ wallet.addedOn }}</time>
                                </p>
                                <svg viewBox="0 0 2 2" class="h-0.5 w-0.5 fill-current">
                                    <circle cx="1" cy="1" r="1" />
                                </svg>
                                <p class="whitespace-nowrap">
                                    Created on <time :datetime="wallet.createdOn">{{ wallet.createdOn }}</time>
                                </p>
                            </div>
                        </div>
                        <div class="flex flex-none items-center gap-x-4">
                            <button @click="setWallet(wallet.id)" class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                            >View wallet</button>
                        </div>
                    </li>
                </ul>
                <LoadingIndicator v-else/>
            </div>
        </CenterMain>
    </div>
</template>

<script setup>
import { PlusIcon } from "@heroicons/vue/24/outline";
import WalletPageHeader from "~/components/WalletPageHeader.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import CenterMain from "~/components/CenterMain.vue";
import { listWallets } from "~/composables/accountWallet";

const config = useRuntimeConfig();


useHead({
    title: "Wallet selection - walt.id",
});

const wallets = await listWallets()

if (process.client) {
    //const worker = new Worker()

    /*
        const wb = new Workbox('/sw/worker.js')

        const registerServiceWorker = async () => {
            if ("serviceWorker" in navigator) {
                try {
                    console.log("Registering service worker...")
                    await navigator.serviceWorker
                        .register("/sw/worker.js")
                        .then(initialiseState);
                } catch (error) {
                    console.error(`Registration failed with ${error}`);
                }
            } else {
                console.warn('Service workers are not supported in this browser.');
            }
        };

        registerServiceWorker();

         */

    function initialiseState() {
        console.log("Service worker initializing...");

        try {
            // Check if desktop notifications are supported
            if (!("showNotification" in ServiceWorkerRegistration.prototype)) {
                // console.warn('Notifications aren\'t supported.');
                // return;
                throw new Error("Notifications aren't supported.");
            }

            // Check if user has disabled notifications
            // If a user has manually disabled notifications in his/her browser for
            // your page previously, they will need to MANUALLY go in and turn the
            // permission back on. In this statement you could show some UI element
            // telling the user how to do so.
            if (Notification.permission === "denied") {
                // console.warn('The user has blocked notifications.');
                // return;
                throw new Error("The user has blocked notifications.");
            }

            // Check if push API is supported
            if (!("PushManager" in window)) {
                // console.warn('Push messaging isn\'t supported.');
                // return;
                throw new Error("Push messaging isn't supported.");
            }

            navigator.serviceWorker.ready.then(function (serviceWorkerRegistration) {
                // Get the push notification subscription object
                serviceWorkerRegistration.pushManager
                    .getSubscription()
                    .then(function (subscription) {
                        // If this is the user's first visit we need to set up
                        // a subscription to push notifications
                        if (!subscription) {
                            subscribe();

                            return;
                        }

                        // Update the server state with the new subscription
                        sendSubscriptionToServer(subscription);
                    })
                    .catch(function (err) {
                        // Handle the error - show a notification in the GUI
                        console.warn("Error during getSubscription()", err);
                    });
            });
        } catch (ex) {
            console.warn(ex);
        }
    }

    initialiseState();

    function subscribe() {
        navigator.serviceWorker.ready.then(function (serviceWorkerRegistration) {
            const options = {
                userVisibleOnly: true,
                applicationServerKey: "BEwCeP8fKSCYFQQAEARb5PQ_dbHjsROMcRJh25e8UWIpJGmWMCg18LIQ2AHiJto3fOmhsTiU6rmnTEWhlbK7fM8",
            };

            serviceWorkerRegistration.pushManager
                .subscribe(options)
                .then(function (subscription) {
                    // Update the server state with the new subscription
                    return sendSubscriptionToServer(subscription);
                })
                .catch(function (e) {
                    if (Notification.permission === "denied") {
                        console.warn("Permission for Notifications was denied");
                    } else {
                        console.error("Unable to subscribe to push:", e);
                    }
                });
        });
    }

    function sendSubscriptionToServer(subscription) {
        console.log("Send subscription to server...");

        // Get public key and user auth from the subscription object
        var key = subscription.getKey ? subscription.getKey("p256dh") : "";
        var auth = subscription.getKey ? subscription.getKey("auth") : "";

        return fetch("/wallet-api/push/subscription", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                endpoint: subscription.endpoint,
                // Take byte[] and turn it into a base64 encoded string suitable for
                // POSTing to a server over HTTP
                key: key ? btoa(String.fromCharCode.apply(null, new Uint8Array(key))) : "",
                auth: auth ? btoa(String.fromCharCode.apply(null, new Uint8Array(auth))) : "",
            }),
        });
    }
}

definePageMeta({
    title: "Select your wallet - walt.id",
    layout: "default-reduced-nav",
})

</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
    display: none;
}

.scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
