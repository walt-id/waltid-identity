<template>
    <div class="min-h-full">
        <div class="flex h-20 flex-shrink-0 border-b border-gray-200 bg-white justify-between items-center px-8">
            <NuxtLink :to="`/wallet/${currentWallet}`">
                <div class="flex items-center px-4 gap-4">
                    <img src="/images/pcg-black-logo.png" alt="walt.id logo" class="h-10" />
                    <div>
                        <div class="text-[#002159] text-bold text-[20px]">ID WALLET</div>
                    </div>
                </div>
            </NuxtLink>
            <div class="hidden sm:block w-full max-w-xs md:max-w-md">
                <form action="#" class="flex w-full md:ml-0" method="GET">
                    <div class="relative w-full text-gray-400 focus-within:text-gray-600">
                        <div aria-hidden="true"
                            class="pointer-events-none absolute inset-y-0 left-0 flex items-center mr-2 text-gray-400 pl-2">
                            <MagnifyingGlassIcon aria-hidden="true" class="h-5 w-5" />
                        </div>
                        <input id="search-field"
                            class="block h-full w-full border-gray-300 rounded-2xl py-2 text-gray-900 focus:border-gray-300 focus:outline-none focus:ring-0 sm:text-sm pl-8"
                            name="search-field" placeholder="Find credentials" type="search" />
                    </div>
                </form>
            </div>
            <div class="ml-4 flex items-center md:ml-6">
                <!-- Profile dropdown -->
                <Menu as="div" class="relative ml-3">
                    <div>
                        <MenuButton
                            class="flex max-w-xs items-center rounded-full bg-gray-100 text-sm focus:outline-none sm:rounded-2xl sm:p-1">
                            <svg width="33" height="33" viewBox="0 0 33 33" fill="none"
                                xmlns="http://www.w3.org/2000/svg">
                                <circle cx="16.5" cy="16.5" r="16.5" fill="#CBD2D9" />
                                <path
                                    d="M20.6256 10.2949C20.6256 11.3235 20.1909 12.3099 19.4172 13.0373C18.6435 13.7646 17.5942 14.1732 16.5 14.1732C15.4058 14.1732 14.3565 13.7646 13.5828 13.0373C12.8091 12.3099 12.3744 11.3235 12.3744 10.2949C12.3744 9.26639 12.8091 8.27995 13.5828 7.55265C14.3565 6.82534 15.4058 6.41675 16.5 6.41675C17.5942 6.41675 18.6435 6.82534 19.4172 7.55265C20.1909 8.27995 20.6256 9.26639 20.6256 10.2949ZM8.25 24.8956C8.28535 22.8607 9.17009 20.9204 10.7134 19.4931C12.2568 18.0658 14.335 17.2659 16.5 17.2659C18.665 17.2659 20.7432 18.0658 22.2866 19.4931C23.8299 20.9204 24.7146 22.8607 24.75 24.8956C22.1618 26.0112 19.3473 26.587 16.5 26.5834C13.556 26.5834 10.7616 25.9794 8.25 24.8956Z"
                                    stroke="black" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
                            </svg>
                            <span class="ml-1 hidden text-sm font-medium text-gray-700 lg:block">{{ user.friendlyName
                            }}</span>
                            <ChevronDownIcon aria-hidden="true"
                                class="ml-1 hidden h-5 w-5 flex-shrink-0 text-gray-400 lg:block" />
                        </MenuButton>
                    </div>
                    <transition enter-active-class="transition ease-out duration-100"
                        enter-from-class="transform opacity-0 scale-95" enter-to-class="transform opacity-100 scale-100"
                        leave-active-class="transition ease-in duration-75"
                        leave-from-class="transform opacity-100 scale-100"
                        leave-to-class="transform opacity-0 scale-95">
                        <MenuItems
                            class="absolute right-0 z-10 mt-2 w-48 origin-top-right rounded-md bg-white py-1 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none">
                            <MenuItem v-slot="{ active }">
                            <NuxtLink :class="[active ? 'bg-gray-100' : '', 'block px-4 py-2 text-sm text-gray-700']"
                                :to="profileUrl">Profile</NuxtLink>
                            </MenuItem>
                            <MenuItem v-slot="{ active }">
                            <button :class="[active ? 'bg-gray-100' : '', 'block px-4 py-2 text-sm text-gray-700']"
                                class="w-full text-left" @click="logout">Logout</button>
                            </MenuItem>
                        </MenuItems>
                    </transition>
                </Menu>
            </div>
        </div>

        <main class="pb-8 w-full">
            <slot />
        </main>
    </div>
</template>

<script lang="ts" setup>
import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/vue";
import { ChevronDownIcon, MagnifyingGlassIcon } from "@heroicons/vue/20/solid";
import { useUserStore } from "@waltid-web-wallet/stores/user.ts";
import { storeToRefs } from "pinia";
import { useCurrentWallet } from "@waltid-web-wallet/composables/accountWallet.ts";
import { useTenant } from "@waltid-web-wallet/composables/tenants.ts";
import { logout } from "~/composables/authentication";

const tenant = await (useTenant()).value
const name = tenant?.name
const logoImg = tenant?.logoImage
const inWalletLogoImage = tenant?.inWalletLogoImage

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const currentWallet = useCurrentWallet()
const profileUrl = `/wallet/${currentWallet.value}/profile`
</script>
