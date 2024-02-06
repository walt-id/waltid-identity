<template>
    <div class="flex flex-col w-full items-center justify-between space-x-3 p-6">
        <!-- nft name -->
        <div class="grid grid-cols-3 gap-4 content-stretch">
            <div>
                <h3 class="truncate text-sm text-gray-900 font-semibold">{{ nft.name }}</h3>
            </div>
            <div>
                <Web3WalletIcon :ecosystem="owner.ecosystem" />
            </div>
        </div>
        <!-- thumbnail -->
        <nftArt :art="nft.art" />
        <div class="flex-1 truncate w-full">
            <!-- token-id -->
            <p class="mt-1 text-sm text-gray-500 flex flex-row items-center">
                <span class="font-mono text-xs">ID:</span> <span class="scrollbar-hide w-5/6">{{ nft.id }}</span>
            </p>
            <!-- nft-description -->
            <p class="mt-1 text-sm text-gray-500 flex flex-row items-center">
                <span class="scrollbar-hide w-5/6"><span class="font-mono">Description</span>: {{ nft.description }}</span>
            </p>
            <!-- nft-holder -->
            <p class="mt-1 text-sm text-gray-500 flex flex-row items-center">
                <span class="scrollbar-hide w-5/6"><span class="font-mono">Owner</span>: <cryptoAddress :text="owner.address" /></span>
            </p>
            <!-- redeemed-status -->
            <div v-if="isRedeemable(nft)">
                <p class="mt-1 text-sm text-gray-500 flex flex-row items-center">
                    <span class="scrollbar-hide w-5/6"><span class="font-mono">Redeemed</span>: {{ nft.id }}</span>
                </p>
            </div>
        </div>
    </div>
    <!-- Buttons -->
    <div>
        <div class="-mt-px flex divide-x divide-gray-200" @click="emitDetailRequest">
            <div class="flex w-0 flex-1">
                <p
                    class="group relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-bl-lg border border-transparent py-4 text-sm font-semibold text-gray-900 hover:bg-blue-500 hover:text-white duration-150"
                >
                    <DocumentTextIcon aria-hidden="true" class="h-5 w-5 text-gray-400 group-hover:text-gray-200" />
                    <span>View</span>
                </p>
            </div>
        </div>
    </div>
</template>

<script setup>
import nftArt from "~/components/nfts/nft-art.vue";
import Web3WalletIcon from "~/components/Web3WalletIcon.vue";
import cryptoAddress from "~/components/nfts/cryptoAddress.vue";
import { DocumentTextIcon } from "@heroicons/vue/20/solid";

const emit = defineEmits(["nftDetail"]);

const props = defineProps({
    nft: {
        type: Object,
        required: true,
    },
    owner: {
        type: Object,
        required: true,
    },
});
const nft = ref(props.nft);

function isRedeemable(nft) {
    return false;
}

function emitDetailRequest() {
    emit("nftDetail", props.nft);
}
</script>
