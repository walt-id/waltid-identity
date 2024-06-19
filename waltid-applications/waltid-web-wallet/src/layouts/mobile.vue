<template>
    <div class="min-h-full">
        <div class="flex flex-1 flex-col min-h-screen">
            <div class="w-full">
                <img :src="logoImage" alt="walt.id logo" class="h-8 w-auto mx-auto mt-5" />
            </div>
            <main class="flex-1 pb-8">
                <slot />
            </main>

            <div class="fixed bottom-0 inset-x-0">
                <hr class="border-t border-gray-200" aria-hidden="true" />
                <nav class="flex justify-between bg-white px-4 py-2" aria-label="Bottom navigation">
                    <NuxtLink v-for="item in navigation" :key="item.name" :to="item.href"
                        class="flex flex-col items-center text-sm text-gray-500 hover:text-gray-900">
                        <span v-html="item.icon" class="mt-1"></span>
                        <span class="mt-1">{{ item.name }}</span>
                    </NuxtLink>
                </nav>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>
import {
    CogIcon,
    ListBulletIcon,
    QuestionMarkCircleIcon,
    ShieldCheckIcon,
} from "@heroicons/vue/24/outline";
import { useCurrentWallet } from "~/composables/accountWallet";
import { useTenant } from "~/composables/tenants";

const tenant = await (useTenant()).value as any

const logoImage = tenant?.logoImage
const inWalletLogoImage = tenant?.inWalletLogoImage

const currentWallet = useCurrentWallet()

const homeSVG = '<svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" class="w-6 h-6" viewBox="0 0 16 16"><path d="M8.707 1.5a1 1 0 0 0-1.414 0L.646 8.146a.5.5 0 0 0 .708.708L2 8.207V13.5A1.5 1.5 0 0 0 3.5 15h9a1.5 1.5 0 0 0 1.5-1.5V8.207l.646.647a.5.5 0 0 0 .708-.708L13 5.793V2.5a.5.5 0 0 0-.5-.5h-1a.5.5 0 0 0-.5.5v1.293zM13 7.207V13.5a.5.5 0 0 1-.5.5h-9a.5.5 0 0 1-.5-.5V7.207l5-5z"/></svg>'
const stackOfCardsSVG = '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6"><path stroke-linecap="round" stroke-linejoin="round" d="M6 6.878V6a2.25 2.25 0 0 1 2.25-2.25h7.5A2.25 2.25 0 0 1 18 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 0 0 4.5 9v.878m13.5-3A2.25 2.25 0 0 1 19.5 9v.878m0 0a2.246 2.246 0 0 0-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0 1 21 12v6a2.25 2.25 0 0 1-2.25 2.25H5.25A2.25 2.25 0 0 1 3 18v-6c0-.98.626-1.813 1.5-2.122" /></svg>'
const profileSVG = '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" /></svg>'

const navigation = [

    {
        name: "Home",
        href: `/wallet/${currentWallet.value}`,
        icon: homeSVG,
    },
    {
        name: "Request",
        href: `/wallet/${currentWallet.value}/settings/issuers`,
        icon: stackOfCardsSVG,
    },
    {
        name: "Profile",
        href: `/wallet/${currentWallet.value}/profile`,
        icon: profileSVG,
    }
];
const secondaryNavigation = [
    { name: "Select wallet", href: "/", icon: ListBulletIcon },
    { name: "Settings", href: "/settings", icon: CogIcon },
    { name: "Help", href: "/help", icon: QuestionMarkCircleIcon },
    { name: "Privacy", href: "/help/privacy", icon: ShieldCheckIcon },
];
</script>

<style>
.router-link-exact-active {
    color: #000;
}

.pwa-toast {
    position: fixed;
    right: 0;
    bottom: 0;
    margin: 16px;
    padding: 12px;
    border: 1px solid #8885;
    border-radius: 4px;
    z-index: 1;
    text-align: left;
    box-shadow: 3px 4px 5px 0 #8885;
}

.pwa-toast .message {
    margin-bottom: 8px;
}

.pwa-toast button {
    border: 1px solid #8885;
    outline: none;
    margin-right: 5px;
    border-radius: 2px;
    padding: 3px 10px;
}
</style>
