<template>
    <PageHeader>
        <template v-slot:icon>
            <img alt="" class="hidden h-16 w-16 rounded-full sm:block" src="/svg/walt-s.svg" />
            <!-- src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2.6&w=256&h=256&q=80"/> -->
        </template>

        <template v-slot:title>
            <img alt="" class="h-16 w-16 rounded-full sm:hidden" src="/svg/walt-s.svg" />
            <!-- src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-1.2.1&ixid=eyJhcHBfaWQiOjEyMDd9&auto=format&fit=facearea&facepad=2.6&w=256&h=256&q=80"/> -->
            <h1 class="ml-3 text-2xl font-bold leading-7 text-gray-900 sm:truncate sm:leading-9">
                Good <span v-if="now.getHours() < 12">morning</span>

                <span v-else-if="now.getHours() > 21">night</span>

                <span v-else-if="now.getHours() > 18">evening</span>

                <span v-else-if="now.getHours() >= 12">afternoon</span>

                <span>,</span> {{ user.email }}
            </h1>
        </template>

        <template v-slot:menu v-if="currentWallet">
            <NuxtLink
                class="inline-flex focus:outline focus:outline-blue-600 focus:outline-offset-2 items-center rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                :to="`/wallet/${currentWallet}/settings/issuers`"
                type="button"
                v-if="currentWallet"
            >
                <ArrowDownOnSquareStackIcon class="h-5 w-5 mr-1" />
                Request credentials
            </NuxtLink>
            <NuxtLink
                class="inline-flex focus:outline focus:outline-blue-600 focus:outline-offset-2 items-center rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                :to="`/wallet/${currentWallet}/scan`"
                type="button"
            >
                <QrCodeIcon class="h-5 w-5 mr-1" />
                Scan to receive or present credentials
            </NuxtLink>
        </template>
    </PageHeader>
</template>

<script setup>
import { useNow } from "@vueuse/core";
import { ArrowDownOnSquareStackIcon, QrCodeIcon } from "@heroicons/vue/24/outline";
import PageHeader from "~/components/PageHeader.vue";
import { useUserStore } from "~/stores/user";
import { storeToRefs } from "pinia";

const config = useRuntimeConfig();
const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const currentWallet = useCurrentWallet()

const now = useNow();
</script>
