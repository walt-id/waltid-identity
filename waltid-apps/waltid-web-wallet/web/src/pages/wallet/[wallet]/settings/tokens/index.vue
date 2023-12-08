<template>
    <CenterMain>
        <!-- add-account -->
        <div class="text-right pt-6">
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
        <div class="flex col-2">
            <!-- accounts filter -->
            <div class="relative w-full">
                <Listbox v-model="selectedAccount" aria-multiselectable="true" as="div">
                    <ListboxLabel class="block text-sm font-medium leading-6 text-gray-900">Filter by wallet address</ListboxLabel>
                    <div class="relative mt-2">
                        <ListboxButton
                            v-if="selectedAccount != null"
                            class="relative w-full cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 sm:text-sm sm:leading-6"
                        >
                            <span class="flex items-center">
                                <div class="h-5 w-5 flex-shrink-0 rounded-full">
                                    <Web3WalletIcon :ecosystem="selectedAccount.ecosystem" />
                                </div>
                                <span class="ml-3 block truncate">{{ selectedAccount.address }}</span>
                            </span>
                            <span class="pointer-events-none absolute inset-y-0 right-0 ml-3 flex items-center pr-2">
                                <ChevronUpDownIcon aria-hidden="true" class="h-5 w-5 text-gray-400" />
                            </span>
                        </ListboxButton>

                        <transition leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
                            <ListboxOptions
                                class="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm"
                            >
                                <ListboxOption v-for="account in accounts" :key="account.id" v-slot="{ active, selectedAccount }" :value="account" as="template">
                                    <li :class="[active ? 'bg-indigo-600 text-white' : 'text-gray-900', 'relative cursor-default select-none py-2 pl-3 pr-9']">
                                        <div class="flex items-center">
                                            <div class="h-5 w-5 flex-shrink-0 rounded-full">
                                                <Web3WalletIcon :ecosystem="account.ecosystem" />
                                            </div>
                                            <span :class="[selectedAccount ? 'font-semibold' : 'font-normal', 'ml-3 block truncate']">{{ account.address }}</span>
                                        </div>

                                        <span v-if="selectedAccount" :class="[active ? 'text-white' : 'text-indigo-600', 'absolute inset-y-0 right-0 flex items-center pr-4']">
                                            <CheckIcon aria-hidden="true" class="h-5 w-5" />
                                        </span>
                                    </li>
                                </ListboxOption>
                            </ListboxOptions>
                        </transition>
                    </div>
                </Listbox>
            </div>
            <!-- chain networks filter -->
            <div class="relative w-full">
                <Listbox v-model="selectedNetwork" aria-multiselectable="true" as="div">
                    <ListboxLabel class="block text-sm font-medium leading-6 text-gray-900">Filter by chain network</ListboxLabel>
                    <div class="relative mt-2">
                        <ListboxButton
                            v-if="selectedNetwork != null"
                            class="relative w-full cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 sm:text-sm sm:leading-6"
                        >
                            <span class="flex items-center">
                                <div class="h-5 w-5 flex-shrink-0 rounded-full">
                                    <Web3WalletIcon v-if="selectedNetwork" :ecosystem="selectedNetwork.ecosystem" />
                                </div>
                                <span class="ml-3 block truncate">{{ selectedNetwork.network }}</span>
                            </span>
                            <span class="pointer-events-none absolute inset-y-0 right-0 ml-3 flex items-center pr-2">
                                <ChevronUpDownIcon aria-hidden="true" class="h-5 w-5 text-gray-400" />
                            </span>
                        </ListboxButton>

                        <transition leave-active-class="transition ease-in duration-100" leave-from-class="opacity-100" leave-to-class="opacity-0">
                            <ListboxOptions
                                class="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm"
                            >
                                <ListboxOption v-for="network in networkList" :key="network.network" v-slot="{ active, selectedNetwork }" :value="network" as="template">
                                    <li :class="[active ? 'bg-indigo-600 text-white' : 'text-gray-900', 'relative cursor-default select-none py-2 pl-3 pr-9']">
                                        <div class="flex items-center">
                                            <div class="h-5 w-5 flex-shrink-0 rounded-full">
                                                <Web3WalletIcon :ecosystem="network.ecosystem" />
                                            </div>
                                            <span :class="[selectedNetwork ? 'font-semibold' : 'font-normal', 'ml-3 block truncate']">{{ network.network }}</span>
                                        </div>
                                        <span v-if="selectedNetwork" :class="[active ? 'text-white' : 'text-indigo-600', 'absolute inset-y-0 right-0 flex items-center pr-4']">
                                            <CheckIcon aria-hidden="true" class="h-5 w-5" />
                                        </span>
                                    </li>
                                </ListboxOption>
                            </ListboxOptions>
                        </transition>
                    </div>
                </Listbox>
            </div>
        </div>
        <LoadingIndicator v-if="pendingAccounts || pendingNetworks || pendingTokens">Loading</LoadingIndicator>
        <!-- nft list -->
        <div v-else-if="nftList && computeNftCount(nftList)">
            <span class="font-semibold">Your NFTs ({{ computeNftCount(nftList) }}):</span>
            <ul v-for="result in nftList" :key="result.owner.address" class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 mt-1" role="list">
                <li v-for="nft in result.tokens" :key="nft.contract" class="col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow">
                    <tile :nft="nft" :owner="result.owner" @nftDetail="openDetailPage" />
                </li>
            </ul>
        </div>
        <h3 v-else class="mt-2 text-sm font-semibold text-gray-900">No NFTs yet.</h3>
    </CenterMain>
</template>

<script lang="ts" setup>
import { CheckIcon, ChevronUpDownIcon, PlusIcon } from "@heroicons/vue/20/solid";
import CenterMain from "~/components/CenterMain.vue";
import Web3WalletIcon from "~/components/Web3WalletIcon.vue";
import AddWalletModal from "~/components/modals/AddWalletModal.vue";
import useModalStore from "~/stores/useModalStore";
import tile from "~/components/nfts/list/tile.vue";
import { Listbox, ListboxButton, ListboxLabel, ListboxOption, ListboxOptions } from "@headlessui/vue";

const store = useModalStore();
const selectedAccount = ref({});
const selectedNetwork = ref({});
// const pending = ref(false)
const { data: accounts, pending: pendingAccounts } = await useLazyAsyncData(() => $fetch(`/wallet-api/wallet/${currentWallet.value}/web3accounts`));
const { data: nftList, pending: pendingTokens } = await useLazyAsyncData(
    () =>
        $fetch(`/wallet-api/wallet/${currentWallet.value}/nft/filter`, {
            params: {
                accountId: selectedAccount.value.id,
                network: selectedNetwork.value.network,
            },
        }),
    {
        watch: [selectedAccount, selectedNetwork],
    },
);

const currentWallet = useCurrentWallet()

const { data: networkList, pending: pendingNetworks } = await useLazyAsyncData(
    () =>
        new Promise((resolve) => {
            selectedNetwork.value = {};
            resolve($fetch(`/wallet-api/wallet/${currentWallet.value}/nft/chains/${selectedAccount.value.ecosystem}`));
        }),
    {
        watch: [selectedAccount],
    },
);

function buildAccountParams(accounts) {
    let result = "";
    if (accounts) {
        result = `?accountId=${accounts.id}`;
    }
    return result;
}

function buildNetworkParams(networks) {
    let result = "";
    if (networks && networks.length > 0) {
        result = `&network=${networks.value}`;
    }
    return result;
}

function computeNftCount(filterResult) {
    let count = 0;
    if (filterResult) {
        filterResult.forEach((item) => {
            count += item.tokens.length;
        });
    }
    return count;
}

function addWallet() {
    store.openModal({
        component: AddWalletModal,
        props: {
            callback: (wallet) => {
                if (
                    accounts.value.filter(function (e) {
                        return e.id === wallet.id;
                    }).length == 0
                ) {
                    accounts.value.push(wallet);
                }
            },
        },
    });
}

function openDetailPage(nft) {
    console.log(`detail request received: ${nft.id}`);
    console.log(nft);
    navigateTo(`tokens/${selectedAccount.value.id}/${nft.chain}/${nft.contract}/${nft.id}${nft.collectionId ? "?collectionId=" + nft.collectionId : ""}`);
}

useHead({
    title: "Tokens - walt.id",
});
</script>
