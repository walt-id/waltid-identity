import {useUserStore} from "@waltid-web-wallet/stores/user.ts";
import {storeToRefs} from "pinia";
import { tokenManager } from "./tokenManager";

export async function logout() {
  const { status, data, signIn, signOut } = useAuth();
  const userStore = useUserStore();
  const { user } = storeToRefs(userStore);

  console.log("User data is: " + JSON.stringify(user.value));

  const userWasOidc = user.value.oidcSession;

  // Clear tokens using token manager
  tokenManager.clearTokens();
  
  // Clear all localStorage
  localStorage.clear();
  console.log("logout");
  user.value = {};

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
