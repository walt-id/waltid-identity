console.log("walt.id wallet service worker run");

self.addEventListener("install", () => {
    console.log("Service worker installing");
    //self.skipWaiting();
});

self.addEventListener("push", function (event) {
    console.log("Push message received.");
    console.log(event.data.json());

    // Defaults
    let notificationTitle = "Hello";
    const notificationOptions = {
        body: "Thanks for sending this push msg.",
        //icon: './images/logo-192x192.png',
        //badge: './images/badge-72x72.png',
        data: {
            url: "https://docs.walt.id/v/web-wallet/wallet-kit/readme",
        },
        actions: [
            {
                action: "accept-action",
                type: "button",
                title: "✅ Accept",
            },
            {
                action: "reject-action",
                type: "button",
                title: "❌ Reject",
            },
        ],
    };

    // Overwrite defaults with data
    if (event.data) {
        const notificationJson = event.data.json();

        notificationTitle = notificationJson.title;
        notificationOptions.body = notificationJson.body;

        const action = btoa(notificationJson.action).replaceAll("=", "");

        if (notificationJson.type === "issuance") {
            notificationOptions.data.url = "http://localhost:3000/exchange/issuance?request=" + action;
        } else if (notificationJson.type === "verification") {
            notificationOptions.data.url = "http://localhost:3000/exchange/presentation?request=" + action;
        }
    }

    event.waitUntil(self.registration.showNotification(notificationTitle, notificationOptions));
});

self.addEventListener("notificationclick", function (event) {
    console.log("Notification clicked.");
    event.notification.close();

    let clickResponsePromise = Promise.resolve();

    if (event.action === "accept-action") {
        clickResponsePromise = clients.openWindow(event.notification.data.url + "&accept=true");
    } else if (event.action === "reject-action") {
        return;
    } else if (event.notification.data && event.notification.data.url) {
        clickResponsePromise = clients.openWindow(event.notification.data.url);
    }

    event.waitUntil(clickResponsePromise);
});
