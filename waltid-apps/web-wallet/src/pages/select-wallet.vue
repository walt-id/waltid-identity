<template>
    <CenterMain>
        <div class="flex justify-between items-center">
            <h1 class="text-lg font-semibold">Select wallet:</h1>

            <div class="flex justify-between gap-2">

            </div>
        </div>
        <button
            class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
        >
            <InboxArrowDownIcon aria-hidden="true" class="h-5 w-5 text-white mr-1" />
            <span>Create new wallet</span>
        </button>
        <WalletListing :use-url="(wallet) => `/wallet/${wallet.id}`"/>
    </CenterMain>
</template>

<script lang="ts" setup>

import { InboxArrowDownIcon } from "@heroicons/vue/24/outline";
import CenterMain from "~/components/CenterMain.vue";
import WalletListing from "~/components/wallets/WalletListing.vue";

//const wallets = (await listWallets()).value.wallets

// const wallets = [{
//     name: "Wallet of X",
//     id: "xabc",
//     createdOn: "2023-11-15"
// }];
</script>

<style scoped>

</style>
