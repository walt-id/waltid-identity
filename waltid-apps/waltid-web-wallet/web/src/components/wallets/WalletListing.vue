<template>
    <ul v-if="wallets">
        <li v-for="wallet in wallets" class="flex items-center justify-between gap-x-6 py-5">
            <Icon class="h-7 w-7" name="heroicons:wallet" />
            <div class="flex items-center justify-between flex-grow">

                <div class="min-w-0">
                    <div class="flex items-start gap-x-3">
                        <p class="text-base font-semibold leading-6 text-gray-900">{{ wallet.name }}</p>
                        <p class="rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset">
                            {{ wallet.permission
                            }}</p>
                    </div>
                    <div class="mt-1 flex items-center gap-x-2 text-sm leading-5 text-gray-500">
                        <p class="whitespace-nowrap">
                            Added on
                            <time :datetime="wallet.addedOn">{{ wallet.addedOn }}</time>
                        </p>
                        <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                            <circle cx="1" cy="1" r="1" />
                        </svg>
                        <p class="whitespace-nowrap">
                            Created on
                            <time :datetime="wallet.createdOn">{{ wallet.createdOn }}</time>
                        </p>
                    </div>
                </div>
                <div class="flex flex-none items-center gap-x-4">
                    <button
                        class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-base font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        @click="setWallet(wallet.id)"
                    >
                        Select wallet
                        <Icon class="h-5 w-5" name="heroicons:chevron-right" />
                    </button>
                </div>
            </div>
        </li>
    </ul>
    <LoadingIndicator v-else />
    <p v-if="wallets && wallets.length == 0" class="mt-2">No wallets.</p>
</template>

<script lang="ts" setup>
import { listWallets } from "~/composables/accountWallet";
import type WalletListing from "~/components/wallets/WalletListing.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";

const wallets = (await listWallets())?.value?.wallets;

defineProps<{
    useUrl: (wallet: WalletListing) => string;
}>();

</script>

<style scoped>

</style>
