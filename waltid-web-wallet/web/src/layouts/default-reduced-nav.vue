<template>
    <div class="min-h-full">
        <ClientOnly>
            <div v-if="$pwa?.needRefresh" class="pwa-toast" role="alert">
                <div class="message">New content available, click on reload button to update.</div>
                <button @click="$pwa.updateServiceWorker()">Reload</button>
            </div>
            <div v-if="$pwa?.showInstallPrompt && !$pwa?.offlineReady && !$pwa?.needRefresh" class="pwa-toast" role="alert">
                <div class="message">
                    <span> Install PWA </span>
                </div>
                <button @click="$pwa.install()">Install</button>
                <button @click="$pwa.cancelInstall()">Cancel</button>
            </div>
        </ClientOnly>

        <TransitionRoot :show="sidebarOpen" as="template">
            <Dialog as="div" class="relative z-40 lg:hidden" @close="sidebarOpen = false">
                <TransitionChild
                    as="template"
                    enter="transition-opacity ease-linear duration-300"
                    enter-from="opacity-0"
                    enter-to="opacity-100"
                    leave="transition-opacity ease-linear duration-300"
                    leave-from="opacity-100"
                    leave-to="opacity-0"
                >
                    <div class="fixed inset-0 bg-gray-600 bg-opacity-75" />
                </TransitionChild>

                <div class="fixed inset-0 z-40 flex">
                    <TransitionChild
                        as="template"
                        enter="transition ease-in-out duration-300 transform"
                        enter-from="-translate-x-full"
                        enter-to="translate-x-0"
                        leave="transition ease-in-out duration-300 transform"
                        leave-from="translate-x-0"
                        leave-to="-translate-x-full"
                    >
                        <DialogPanel class="relative flex w-full max-w-xs flex-1 flex-col bg-blue-600 pb-4 pt-5">
                            <TransitionChild
                                as="template"
                                enter="ease-in-out duration-300"
                                enter-from="opacity-0"
                                enter-to="opacity-100"
                                leave="ease-in-out duration-300"
                                leave-from="opacity-100"
                                leave-to="opacity-0"
                            >
                                <div class="absolute right-0 top-0 -mr-12 pt-2">
                                    <button
                                        class="ml-1 flex h-10 w-10 items-center justify-center rounded-full focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white"
                                        type="button"
                                        @click="sidebarOpen = false"
                                    >
                                        <span class="sr-only">Close sidebar</span>
                                        <XMarkIcon aria-hidden="true" class="h-6 w-6 text-white" />
                                    </button>
                                </div>
                            </TransitionChild>
                            <div class="flex flex-shrink-0 items-center px-4">
                                <img alt="" class="h-14 w-auto" :src="inWalletLogoImage" />
                            </div>

                            <nav aria-label="Sidebar" class="mt-5 h-full flex-shrink-0 divide-y divide-blue-800 overflow-y-auto">
                                <div class="mt-6 pt-6">
                                    <div class="space-y-1 px-2">
                                        <NuxtLink
                                            v-for="item in secondaryNavigation"
                                            :key="item.name"
                                            :to="item.href"
                                            class="group flex items-center rounded-md px-2 py-2 text-base font-medium text-white hover:bg-blue-700 hover:text-white"
                                        >
                                            <component :is="item.icon" aria-hidden="true" class="mr-4 h-6 w-6 text-blue-200" />
                                            {{ item.name }}
                                        </NuxtLink>
                                    </div>
                                </div>
                            </nav>
                        </DialogPanel>
                    </TransitionChild>
                    <div aria-hidden="true" class="w-14 flex-shrink-0">
                        <!-- Dummy element to force sidebar to shrink to fit close icon -->
                    </div>
                </div>
            </Dialog>
        </TransitionRoot>

        <!-- Static sidebar for desktop -->
        <div class="hidden lg:fixed lg:inset-y-0 lg:flex lg:w-64 lg:flex-col">
            <!-- Sidebar component, swap this element with another sidebar if you like -->
            <div class="flex flex-grow flex-col overflow-y-auto bg-blue-600 pb-4 pt-5" style="color: rgb(37, 99, 235)">
                <div class="flex flex-shrink-0 items-center px-4">
                    <img alt="" class="h-14 w-auto" :src="inWalletLogoImage" />
                </div>
                <nav aria-label="Sidebar" class="mt-5 flex flex-1 flex-col divide-y divide-blue-800 overflow-y-auto">
                    <div class="mt-2 pt-2">
                        <div class="space-y-1 px-2">
                            <NuxtLink
                                v-for="item in secondaryNavigation"
                                :key="item.name"
                                :to="item.href"
                                class="group flex items-center rounded-md px-2 py-2 text-base font-medium leading-6 text-white hover:bg-blue-700 hover:text-white"
                            >
                                <component :is="item.icon" aria-hidden="true" class="mr-4 h-6 w-6 text-blue-200" />
                                {{ item.name }}
                            </NuxtLink>
                        </div>
                    </div>
                </nav>
            </div>
        </div>

        <div class="flex flex-1 flex-col lg:pl-64">
            <div class="flex h-16 flex-shrink-0 border-b border-gray-200 bg-white">
                <button class="border-r border-gray-200 px-4 text-gray-400 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-500 lg:hidden" type="button" @click="sidebarOpen = true">
                    <span class="sr-only">Open sidebar</span>
                    <Bars3CenterLeftIcon aria-hidden="true" class="h-6 w-6" />
                </button>
                <!-- Search bar -->
                <div class="flex flex-1 justify-between px-4 sm:px-6 lg:mx-auto lg:max-w-6xl lg:px-8">
                    <div class="flex flex-1">
                        <form action="#" class="flex w-full md:ml-0" method="GET">
                            <label class="sr-only" for="search-field">Search</label>
                            <div class="relative w-full text-gray-400 focus-within:text-gray-600">
                                <div aria-hidden="true" class="pointer-events-none absolute inset-y-0 left-0 flex items-center">
                                    <MagnifyingGlassIcon aria-hidden="true" class="h-5 w-5" />
                                </div>
                                <input
                                    id="search-field"
                                    class="block h-full w-full border-transparent py-2 pl-8 pr-3 text-gray-900 focus:border-transparent focus:outline-none focus:ring-0 sm:text-sm"
                                    name="search-field"
                                    placeholder="Search for credentials"
                                    type="search"
                                />
                            </div>
                        </form>
                    </div>
                    <div class="ml-4 flex items-center md:ml-6">
                        <button
                            class="rounded-full bg-white p-1 text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                            type="button"
                            @click="reloadData"
                        >
                            <span class="sr-only">Refresh</span>
                            <ArrowPathIcon :class="[refreshing ? 'animate-spin' : '']" aria-hidden="true" class="h-6 w-6" />
                        </button>
                        <button class="rounded-full bg-white p-1 text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2" type="button">
                            <span class="sr-only">View notifications</span>
                            <BellIcon aria-hidden="true" class="h-6 w-6" />
                        </button>

                        <!-- Profile dropdown -->
                        <Menu as="div" class="relative ml-3">
                            <div>
                                <MenuButton
                                    class="flex max-w-xs items-center rounded-full bg-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 lg:rounded-md lg:p-2 lg:hover:bg-gray-50"
                                >
                                    <img alt="" class="h-8 w-8 rounded-full" src="/svg/walt-s.svg" />
                                    <!-- src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2&w=256&h=256&q=80"/> -->
                                    <span class="ml-3 hidden text-sm font-medium text-gray-700 lg:block">{{ user.email }}</span>
                                    <ChevronDownIcon aria-hidden="true" class="ml-1 hidden h-5 w-5 flex-shrink-0 text-gray-400 lg:block" />
                                </MenuButton>
                            </div>
                            <transition
                                enter-active-class="transition ease-out duration-100"
                                enter-from-class="transform opacity-0 scale-95"
                                enter-to-class="transform opacity-100 scale-100"
                                leave-active-class="transition ease-in duration-75"
                                leave-from-class="transform opacity-100 scale-100"
                                leave-to-class="transform opacity-0 scale-95"
                            >
                                <MenuItems class="absolute right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none">
                                    <MenuItem v-slot="{ active }">
                                        <NuxtLink :class="[active ? 'bg-gray-100' : '', 'block px-4 py-2 text-sm text-gray-700']" to="/profile">Your Profile </NuxtLink>
                                    </MenuItem>
                                    <MenuItem v-slot="{ active }">
                                        <NuxtLink :class="[active ? 'bg-gray-100' : '', 'block px-4 py-2 text-sm text-gray-700']" to="/settings">Settings </NuxtLink>
                                    </MenuItem>
                                    <MenuItem v-slot="{ active }">
                                        <button :class="[active ? 'bg-gray-100' : '', 'block px-4 py-2 text-sm text-gray-700']" class="w-full text-left" @click="logout">Logout</button>
                                    </MenuItem>
                                </MenuItems>
                            </transition>
                        </Menu>
                    </div>
                </div>
            </div>

            <main class="flex-1 pb-8">
                <slot />
            </main>
        </div>
    </div>
</template>

<script lang="ts" setup>
import { ref } from "vue";
import { Dialog, DialogPanel, Menu, MenuButton, MenuItem, MenuItems, TransitionChild, TransitionRoot } from "@headlessui/vue";
import { ArrowPathIcon, Bars3CenterLeftIcon, BellIcon, CogIcon, ListBulletIcon, QuestionMarkCircleIcon, ShieldCheckIcon, XMarkIcon } from "@heroicons/vue/24/outline";
import { ChevronDownIcon, MagnifyingGlassIcon } from "@heroicons/vue/20/solid";
import { useUserStore } from "~/stores/user";
import { storeToRefs } from "pinia";
import * as nearAPI from "near-api-js";
import { useTenant } from "~/composables/tenants";

const tenant = await (useTenant()).value
const name = tenant?.name
const logoImg = tenant?.logoImage
const inWalletLogoImage = tenant?.inWalletLogoImage

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const { status, data, signIn, signOut } = useAuth();
const { connect, keyStores, WalletConnection } = nearAPI;

async function logout() {
    const connectionConfig = {
        networkId: "testnet",
        keyStore: new keyStores.BrowserLocalStorageKeyStore(),
        nodeUrl: "https://rpc.testnet.near.org",
        walletUrl: "https://wallet.testnet.near.org",
        helperUrl: "https://helper.testnet.near.org",
        explorerUrl: "https://explorer.testnet.near.org",
    };

    // connect to NEAR
    const nearConnection = await connect(connectionConfig);

    const walletConnection = new WalletConnection(nearConnection, "waltid");
    localStorage.clear();
    console.log("logout");
    user.value = {};
    walletConnection.signOut();

    await signOut({ callbackUrl: "/login" });
}

const refreshing = ref(false);

async function reloadData() {
    refreshing.value = true;
    console.log("Refreshing data");
    refreshNuxtData().then(() => {
        refreshing.value = false;
        console.log("Refreshed data");
    });
}

const secondaryNavigation = [
    { name: "Select wallet", href: "/", icon: ListBulletIcon },
    { name: "Settings", href: "/settings", icon: CogIcon },
    { name: "Help", href: "/help", icon: QuestionMarkCircleIcon },
    { name: "Privacy", href: "/help/privacy", icon: ShieldCheckIcon },
];
const statusStyles = {
    success: "bg-green-100 text-green-800",
    processing: "bg-yellow-100 text-yellow-800",
    failed: "bg-gray-100 text-gray-800",
};

const sidebarOpen = ref(false);
</script>

<style>
.router-link-exact-active {
    @apply font-semibold;
    @apply bg-blue-500;
    @apply hover:bg-blue-500;
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
