import { useUserStore } from "@waltid-web-wallet/stores/user.ts";
import { storeToRefs } from "pinia";

export async function logout() {

    const userStore = useUserStore();
    const { user } = storeToRefs(userStore);

    /*const connectionConfig = {
        networkId: "testnet",
        keyStore: new keyStores.BrowserLocalStorageKeyStore(),
        nodeUrl: "https://rpc.testnet.near.org",
        walletUrl: "https://wallet.testnet.near.org",
        helperUrl: "https://helper.testnet.near.org",
        explorerUrl: "https://explorer.testnet.near.org",
    };

    // connect to NEAR
    const nearConnection = await connect(connectionConfig);

    const walletConnection = new WalletConnection(nearConnection, "waltid");*/

    console.log("User data is: " + JSON.stringify(user.value))

    const userWasOidc = user.value.oidcSession

    localStorage.clear();
    console.log("logout");
    user.value = {};
    // walletConnection.signOut();

    console.log("OIDC logout: " + userWasOidc)

    $fetch("/wallet-api/auth/logout", {method: "POST"}).then(() => {
        if (!userWasOidc) {
            navigateTo("/login");
        } else {
            navigateTo("/wallet-api/auth/logout-oidc");
        }
    });
}
