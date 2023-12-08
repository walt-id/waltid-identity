<template>
    <CenterMain>
        <h1 class="font-semibold">Profile</h1>
        <p>Username: {{ user.email }}</p>
        <!-- add-wallet -->
        <div class="items-center">
            <div class="mt-4">
                <button
                    class="inline-flex items-center rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                    type="button"
                    @click="addWallet"
                >
                    <PlusIcon aria-hidden="true" class="-ml-0.5 mr-1.5 h-5 w-5" />
                    Add wallet address
                </button>
            </div>
        </div>
        <LoadingIndicator v-if="pending">Loading</LoadingIndicator>
        <span v-else-if="wallets && wallets.length > 0" class="font-semibold">Your connected addresses ({{ wallets.length }}):</span>
        <h3 v-else class="font-semibold">No connected addresses</h3>
        <!-- wallet-list -->
        <ul class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-2" role="list">
            <li v-for="wallet in wallets" :key="wallet.id" class="flex items-center justify-between gap-x-6 py-4">
                <div class="flex w-full">
                    <Web3WalletIcon :ecosystem="wallet.ecosystem" class="px-2" />
                    <div class="flex truncate w-full">
                        <span class="text-base font-normal">{{ wallet.address }}</span>
                    </div>
                    <div class="flex flex-row space-x-1 items-center">
                        <!-- connect -->
                        <button
                            :class="{ 'bg-red-500 hover:bg-red-600': wallet.owner }"
                            class="w-full rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                            type="button"
                            @click="wallet.owner ? disconnectWallet(wallet.id) : connectWallet(wallet.id)"
                        >
                            <span v-if="wallet.owner">Disconnect</span>
                            <span v-else>Connect</span>
                        </button>
                        <!-- unlink -->
                        <button
                            :class="{ 'opacity-25': wallet.owner }"
                            :disabled="wallet.owner"
                            class="w-full rounded-md bg-indigo-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                            type="button"
                            @click="unlinkWallet(wallet.id)"
                        >
                            <span>Unlink</span>
                        </button>
                    </div>
                </div>
            </li>
        </ul>
    </CenterMain>
</template>

<script setup>
import CenterMain from "~/components/CenterMain.vue";
import { PlusIcon } from "@heroicons/vue/20/solid";
import Web3WalletIcon from "~/components/Web3WalletIcon.vue";
import AddWalletModal from "~/components/modals/AddWalletModal.vue";
import ConnectWalletModal from "~/components/modals/ConnectWalletModal.vue";
import useModalStore from "~/stores/useModalStore";
import { useUserStore } from "~/stores/user";
import { storeToRefs } from "pinia";

const modalStore = useModalStore();
const userStore = useUserStore();

const currentWallet = useCurrentWallet()

const { user } = storeToRefs(userStore);
const { data: wallets, pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/web3accounts`);

function addWallet() {
    modalStore.openModal({
        component: AddWalletModal,
        props: {
            callback: (wallet) => {
                if (
                    wallets.value.filter(function (e) {
                        return e.id === wallet.id;
                    }).length == 0
                ) {
                    wallets.value.push(wallet);
                }
            },
        },
    });
}

function connectWallet(walletId) {
    console.log(`Connecting wallet: ${walletId}`);
    modalStore.openModal({
        component: ConnectWalletModal,
    });
    //TODO
}

async function disconnectWallet(walletId) {
    console.log(`Disconnecting wallet: ${walletId}`);
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/web3accounts/disconnect`, {
        method: "POST",
        body: walletId,
    })
        .then((response) => {
            // refresh list
            refreshNuxtData();
        })
        .catch((err) => {
            console.error(`Error disconnecting wallet: ${err}`);
        });
}

async function unlinkWallet(walletId) {
    console.log(`Unlinking wallet: ${walletId}`);
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/web3accounts/unlink`, {
        method: "POST",
        body: walletId,
    })
        .then((response) => {
            // refresh list
            refreshNuxtData();
        })
        .catch((err) => {
            console.error(`Error unlinking wallet: ${err}`);
        });
}
</script>

<style scoped></style>
