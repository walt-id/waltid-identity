<template>
  <CenterMain>
    <WalletListing
      v-if="wallets && wallets.length > 1"
      :wallets="wallets"
      :use-url="walletUrlFunction"
    />
    <LoadingIndicator v-else>Loading wallets...</LoadingIndicator>
  </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import WalletListing from "@waltid-web-wallet/components/wallets/WalletListing.vue";
import {listWallets, setWallet, type WalletListing as WalletListingType} from "@waltid-web-wallet/composables/accountWallet.ts";

const queryRequest = new URL("http://example.invalid" + useRoute().fullPath)
  .search; // new URL(window.location.href).search
console.log("queryRequest: ", queryRequest);

const walletRequestUrl = "openid-initiate-issuance://" + queryRequest;
console.log("walletRequestUrl: ", walletRequestUrl);
const encodedWalletRequestUrl = btoa(walletRequestUrl);
console.log("encodedWalletRequestUrl: ", encodedWalletRequestUrl);

const wallets = (await listWallets())?.value?.wallets;

const walletUrlFunction = (wallet: WalletListingType) =>
  `/wallet/${wallet.id}/exchange/issuance?request=${encodedWalletRequestUrl}`;

if (wallets && wallets.length == 1) {
  const wallet = wallets[0];
  setWallet(wallet.id, undefined);
  navigateTo(walletUrlFunction(wallets[0]));
}
</script>
