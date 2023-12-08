<template>
    <CenterMain>
        <BackButton />
        <LoadingIndicator v-if="pending">{{ $t("loading") }}</LoadingIndicator>
        <div v-else>
            <!-- title -->
            <h2 class="q-sans-md" style="color: #008cc8">{{ nft.name }}</h2>
            <!-- token-art -->
            <div class="flex flex-wrap mt-4 p-8 m-auto items-center justify-center">
                <nftArt :art="nft.art" :thumbnail="false" />
                <!-- <qrcode-vue v-if="isQrCodeActive" :value="nft" level="L" size="300"></qrcode-vue> -->
            </div>
            <!-- redeem button -->
            <div>
                <!-- <div v-if="showRedeem" class="col-2">
                  <a class="inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-gray-500 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-offset-0"
                    href="#" @click="redeem" :disabled="isRedeemed || redeemInProgress">
                    <i class="bi bi-upc-scan me-2"></i><span v-if="showQR">{{ $t('nft.closeQr') }}</span><span v-if="!showQR">{{ $t('nft.showQr') }}</span>
                    <span v-if="!isRedeemed && !redeemInProgress"><i class="bi bi-upc-scan me-2"></i>{{ $t('nft.redeem') }}</span>
                    <span v-if="isRedeemed && !redeemInProgress"><i class="bi bi-upc-scan me-2"></i>{{ $t('nft.redeemed') }}</span>
                    <div v-if="redeemInProgress" class="spinner-border" role="status">
                        <span class="sr-only"></span>
                    </div>
                  </a>
                </div> -->
                <!-- show qr -->
                <div v-if="isRedeemable">
                    <button
                        :disabled="isRedeemed"
                        class="inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-gray-500 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-offset-0"
                        @click="toggleShowQR"
                    >
                        <!-- <img aria-hidden="true" class="h-5 w-5" fill="currentColor" src="/svg/tezos.svg" /> -->
                        <i class="bi bi-upc-scan me-2"></i><span v-if="showQR">Close QR</span><span v-if="!showQR">Show QR</span>
                    </button>
                </div>
            </div>
            <!-- token properties -->
            <div class="text-left pt-2">
                <div v-if="isRedeemable && isRedeemed" class="alert alert-info mt-3 mx-3">
                    <em>Already redeemed</em>
                </div>
                <span class="col-12 px-3 font-semiblold">
                    <h5>Description</h5>
                    <p>{{ nft.description }}</p>
                </span>
                <span class="col-12 px-3 font-semiblold">
                    <h5>Contract address</h5>
                    <p>{{ nft.contract }}</p>
                </span>
                <span class="col-12 px-3 font-semiblold">
                    <h5>TokenId</h5>
                    <p>{{ nft.id }}</p>
                </span>
                <span class="col-12 px-3 font-semiblold">
                    <h5>Standard</h5>
                    <p>{{ nft.type }}</p>
                </span>
                <span class="col-12 px-3 font-semiblold">
                    <h5>Blockchain</h5>
                    <p>{{ chain.toUpperCase() }}</p>
                </span>
            </div>
            <!-- token chain explorer url -->
            <div v-if="isNotNullOrEmpty(explorer?.url)">
                <a
                    :href="explorer?.url"
                    class="inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-gray-500 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-offset-0"
                    target="_blank"
                >
                    <span class="ml-1 text-gray-800 font-semibold h-5">View on blockchain explorer</span>
                </a>
            </div>
            <!-- token marketplace url -->
            <div v-if="isNotNullOrEmpty(marketplace?.url)">
                <a
                    :href="marketplace?.url"
                    class="inline-flex w-full justify-center rounded-md bg-white px-3 py-2 text-gray-500 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:outline-offset-0"
                    target="_blank"
                >
                    <span class="ml-1 text-gray-800 font-semibold h-5">View on {{ marketplace?.name }}</span></a
                >
            </div>
        </div>
    </CenterMain>
</template>

<script setup>
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import nftArt from "~/components/nfts/nft-art.vue";
import { isNotNullOrEmpty } from "~/composables/useNftMedia";

const route = useRoute();

const accountId = route.params.accountId;
const chain = route.params.chain;
const contract = route.params.contract;
const tokenId = route.params.tokenId;
const collectionId = route.query.collectionId;

const isQrCodeActive = ref(false);

const currentWallet = useCurrentWallet()

const {
    data: nft,
    pending,
    refresh,
    error,
} = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/nft/detail/${accountId}/${chain.toUpperCase()}/${contract}/${tokenId}${collectionId ? "?collectionId=" + collectionId : ""}`);
const { data: marketplace } = useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/nft/marketplace/${chain}/${contract}/${tokenId}`);
const { data: explorer } = useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/nft/explorer/${chain}/${contract}`);
refreshNuxtData();

useHead({
    title: "NFT details - walt.id",
});

function showRedeem() {
    return route.query.redeem == "true";
}

function isRedeemable() {
    return !showRedeem && nft.attributes && nft.attributes.find((a) => a.traitType == "redeemed") != null;
}

function isRedeemed() {
    return nft.attributes.find((a) => a.traitType == "redeemed" && a.value == "true") != null;
}

function toggleShowQR() {
    isQrCodeActive.value = !isQrCodeActive.value;
}
</script>

<style scoped></style>
