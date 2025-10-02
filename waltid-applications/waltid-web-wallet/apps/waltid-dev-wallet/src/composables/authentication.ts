import {useUserStore} from "@waltid-web-wallet/stores/user.ts";
import {storeToRefs} from "pinia";

export async function logout() {
  const { status, data, signIn, signOut } = useAuth();
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

  console.log("User data is: " + JSON.stringify(user.value));

  const userWasOidc = user.value.oidcSession;

  localStorage.clear();
  console.log("logout");
  user.value = {};
  // walletConnection.signOut();

  console.log("OIDC logout: " + userWasOidc);

  if (!userWasOidc) {
    await signOut({ callbackUrl: "/login" }).then((x) => {});
  } else {
    await signOut({
      callbackUrl: "/wallet-api/auth/logout-oidc",
      external: true,
    });
  }
}
