<template>
    <div
        class="flex flex-1 flex-col justify-center px-4 py-12 sm:px-6 lg:flex-none lg:px-20 xl:px-24 lg:bg-white lg:bg-opacity-50 sm:h-full sm:p-6">
        <div class="sm:h-full w-full">
            <div v-if="credentials && credentials.length > 0" class="sm:flex items-center gap-4 mb-5 hidden">
                <h1 class="text-3xl font-bold text-black">Credentials</h1>
                <NuxtLink :to="`/wallet/${walletId}/scan`">
                    <button
                        class="rounded-full px-6 py-1 text-white bg-[#01337D] shadow-lg hover:bg-[#002159] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#002159]">
                        Present
                    </button>
                </NuxtLink>
                <NuxtLink :to="`/wallet/${walletId}/scan`">
                    <button
                        class="rounded-full px-6 py-1 text-white bg-[#01337D] shadow-lg hover:bg-[#002159] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#002159]">
                        Receive
                    </button>
                </NuxtLink>
            </div>
            <ul v-if="credentials && credentials.length > 0"
                class="relative grid grid-cols-1 gap-y-4 sm:grid-cols-2 lg:grid-cols-3 sm:gap-x-4 sm:gap-y-6"
                role="list">
                <li v-for="(credential, index) in credentials" :key="credential.id"
                    :class="{ 'mt-[-85px] sm:mt-0': index !== 0 }" class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105
                        cursor-pointer duration-200">
                    <NuxtLink :to="`/wallet/${walletId}/credentials/` + encodeURIComponent(credential.id)">
                        <VerifiableCredentialCard :credential="credential" />
                    </NuxtLink>
                </li>
            </ul>

            <LoadingIndicator v-else-if="pending">Loading credentials...</LoadingIndicator>

            <div v-else class="h-full flex flex-col items-center justify-center">
                <!-- <h3 class="text-2xl font-bold text-[#01337D]">Wallet is Inactive</h3> -->
                <p class="mt-2 text-md text-black">You don’t have any credentials yet!</p>
                <NuxtLink :to="`/wallet/${walletId}/scan`">
                    <button
                        class="mt-8 px-4 py-2 text-white bg-[#01337D] rounded-lg shadow-lg hover:bg-[#002159] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#002159]">
                        Receive Credential
                    </button>
                </NuxtLink>
            </div>
        </div>
    </div>

    <div v-if="credentials && credentials.length > 0" class="fixed bottom-20 right-5 sm:hidden">
        <NuxtLink :to="`/wallet/${walletId}/scan`">
            <button
                class="flex items-center justify-center h-14 w-14 rounded-full bg-black text-white shadow-lg hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
                <img :src="scannerSVG" alt="Scan QR code" class="h-6 w-6" />
            </button>
        </NuxtLink>
    </div>
</template>

<script setup>
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";
import scannerSVG from "~/public/svg/scanner.svg";

const route = useRoute();
const walletId = route.params.wallet;

const { data: credentials, pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${walletId}/credentials?showDeleted=false&showPending=false`);
refreshNuxtData();

definePageMeta({
    title: "Wallet dashboard - walt.id",
    layout: window.innerWidth > 650 ? "desktop" : "mobile",
});
</script>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
    display: none;
}

.scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
