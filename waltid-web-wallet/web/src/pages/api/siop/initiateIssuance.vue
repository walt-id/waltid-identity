<template>
    <CenterMain>
        <WalletListing v-if="wallets && wallets.length > 1" :wallets="wallets" :use-url="walletUrlFunction"/>
        <LoadingIndicator v-else>Loading wallets...</LoadingIndicator>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import WalletListing from "~/components/wallets/WalletListing.vue";

const queryRequest = new URL("http://example.invalid" + useRoute().fullPath).search // new URL(window.location.href).search
console.log("queryRequest: ", queryRequest)

const walletRequestUrl = "openid-initiate-issuance://" + queryRequest;
console.log("walletRequestUrl: ", walletRequestUrl);
const encodedWalletRequestUrl = btoa(walletRequestUrl);
console.log("encodedWalletRequestUrl: ", encodedWalletRequestUrl);

const wallets = (await listWallets())?.value?.wallets;

const walletUrlFunction = (wallet: WalletListing) => `/wallet/${wallet.id}/exchange/issuance?request=${encodedWalletRequestUrl}`

if (wallets && wallets.length == 1) {
    const wallet = wallets[0]
    setWallet(wallet.id, undefined)
    navigateTo(walletUrlFunction(wallets[0]))
}
</script>
