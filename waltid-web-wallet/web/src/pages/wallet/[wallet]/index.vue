<template>
    <div>
        <WalletPageHeader />
        <CenterMain>
            <div>
                <span v-if="credentials && credentials.length > 0" class="font-semibold"> Your credentials ({{ credentials.length }}):</span>

                <LoadingIndicator v-else-if="pending">Loading credentials...</LoadingIndicator>

                <div v-else class="text-center pt-6">
                    <svg aria-hidden="true" class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path
                            d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            stroke-width="2"
                            vector-effect="non-scaling-stroke"
                        />
                    </svg>
                    <h3 class="mt-2 text-sm font-semibold text-gray-900">No credentials yet</h3>
                    <p class="mt-1 text-sm text-gray-500">Get started filling your wallet by receiving some credentials!</p>
                    <div class="mt-4">
                        <NuxtLink
                            class="inline-flex items-center rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                            :to="`/wallet/${currentWallet}/settings/issuers`"
                            type="button"
                        >
                            <PlusIcon aria-hidden="true" class="-ml-0.5 mr-1.5 h-5 w-5" />
                            Request credentials
                        </NuxtLink>
                    </div>
                </div>

                <ul class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 mt-6" role="list">
                    <li
                        v-for="credential in credentials"
                        :key="credential.id"
                        class="col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow w-[96%] transform hover:scale-105 cursor-pointer duration-200"
                    >
                        <NuxtLink :to="`/wallet/${walletId}/credentials/` + encodeURIComponent(credential.id)">
                            <VerifiableCredentialCard :credential="credential"/>
                        </NuxtLink>
                    </li>
                </ul>
            </div>
        </CenterMain>
    </div>
</template>

<script setup>
import { PlusIcon } from "@heroicons/vue/24/outline";
import WalletPageHeader from "~/components/WalletPageHeader.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import CenterMain from "~/components/CenterMain.vue";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";

const config = useRuntimeConfig();

const route = useRoute()
const currentWallet = useCurrentWallet()

const walletId = route.params.wallet

const { data: credentials, pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${walletId}/credentials`);
refreshNuxtData();

function credentialTypeOf(credential) {
    return credential.type[credential.type.length - 1];
}

useHead({
    title: "Wallet dashboard - walt.id",
});

if (process.client) {
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
    title: "Wallet dashboard - walt.id",
    layout: "default",
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
