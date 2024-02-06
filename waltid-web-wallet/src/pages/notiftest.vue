<template>Notif test</template>

<script setup>
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
        const key = subscription.getKey ? subscription.getKey("p256dh") : "";
        const auth = subscription.getKey ? subscription.getKey("auth") : "";

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
</script>

<style scoped></style>
