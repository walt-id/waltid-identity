<template>

    <Disclosure v-slot="{ open }" as="nav" class="bg-white shadow">
        <div class="mx-auto max-w-7xl px-2 sm:px-6 lg:px-8">
            <div class="relative flex h-16 justify-between">
                <div class="absolute inset-y-0 left-0 flex items-center sm:hidden">
                    <!-- Mobile menu button -->
                    <DisclosureButton
                        class="relative inline-flex items-center justify-center rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500"
                    >
                        <span class="absolute -inset-0.5" />
                        <span class="sr-only">Open main menu</span>
                        <Icon v-if="!open" aria-hidden="true" class="block h-6 w-6" name="heroicons:bars-3" />
                        <Icon v-else aria-hidden="true" class="block h-6 w-6" name="heroicons:x-mark" />
                    </DisclosureButton>
                </div>
                <div class="flex flex-1 items-center justify-center sm:items-stretch sm:justify-start">
                    <div class="flex flex-shrink-0 items-center">
                        <img alt="Your Company" class="h-12 w-auto" src="/waltid.svg" />
                    </div>
                    <div class="hidden sm:ml-6 sm:flex sm:space-x-8">
                        <!-- Current: "border-indigo-500 text-gray-900", Default: "border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700" -->

                        <NuxtLink v-for="link in navigation" :to="link.link"
                                  class="inline-flex items-center gap-2 border-b-2 border-transparent px-1 pt-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700"
                        >
                            <Icon v-if="link.icon" :name="link.icon" class="w-5 h-5" />
                            {{ link.title }}
                        </NuxtLink>
                    </div>
                </div>
                <div class="absolute inset-y-0 right-0 flex items-center pr-2 sm:static sm:inset-auto sm:ml-6 sm:pr-0">
                    <div class="relative ml-3">
                        <div>
                            <div
                                class="relative flex rounded-full bg-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                            >
                                <span class="absolute -inset-1.5" />
                                <span class="font-semibold">Credential Demo Portal</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <DisclosurePanel class="sm:hidden">
            <div class="space-y-1 pb-4 pt-2">
                <DisclosureButton v-for="link in navigation" as="div"
                                  class="block border-l-4 border-transparent py-2 pl-3 pr-4 text-base font-medium text-gray-500 hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700"
                                  href="#"
                >
                    <NuxtLink :to="link.link"
                              class="inline-flex items-center gap-2 border-b-2 border-transparent px-1 pt-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700"
                    >
                        <Icon v-if="link.icon" :name="link.icon" class="w-5 h-5" />
                        {{ link.title }}
                    </NuxtLink>
                </DisclosureButton>
            </div>
        </DisclosurePanel>
    </Disclosure>

    <div class="m-10">
        <slot />
    </div>
</template>

<script lang="ts" setup>
import { Disclosure, DisclosureButton, DisclosurePanel } from "@headlessui/vue";

const navigation = [
    {
        title: "List credentials",
        icon: "carbon:prompt-template",
        link: "/"
    },
    {
        title: "Issue credentials",
        icon: "carbon:certificate-check",
        link: "/issuance"
    },
    {
        title: "Verify credentials",
        icon: "carbon:list-checked",
        link: "/verification"
    }
];
</script>

<style scoped>
.router-link-active {
    --un-border-opacity: 1;
    border-color: rgb(99 102 241 / var(--un-border-opacity));
    --un-text-opacity: 1;
    color: rgb(17 24 39 / var(--un-text-opacity));
}

.router-link-active:hover {
    --un-border-opacity: 1;
    border-color: rgb(99 102 241 / var(--un-border-opacity));
    --un-text-opacity: 1;
    color: rgb(17 24 39 / var(--un-text-opacity));
}
</style>
