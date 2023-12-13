<template>
    <CenterMain>
        <h1 class="text-2xl font-semibold mb-2">EventLog</h1>

        <LoadingIndicator v-if="pending" class="my-6 mb-12 w-full"> Loading eventLog... </LoadingIndicator>

        <p class="mb-1">The last 10 operations are shown below:</p>

        <div v-if="eventLog?.count > 0" aria-label="Credential list" class="h-full overflow-y-auto shadow-xl">
            <ul class="divide-y divide-gray-100 overflow-x-clip" role="list">
                <li v-for="element in eventLog.items" :key="element" class="flex gap-x-4 px-3 py-5">
                    <!--<CredentialIcon :credentialType="element.name" class="h-6 w-6 flex-none rounded-full bg-gray-50"></CredentialIcon>-->

                    <div class="min-w-0">
                        <div class="flex items-start gap-x-3">
                            <!-- <span class="text-sm font-semibold leading-6 text-gray-900">{{ element.timestamp }}.</span> -->

                            <!-- <div v-if="element.entry.operation === 'useOfferRequest'">
                                <p class="text-sm font-semibold leading-6 text-gray-900">Used offer request</p>
                                <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                    <code>{{ JSON.stringify(element.entry.data) }}</code>
                                </p>
                            </div>
                            <div v-else-if="element.event === 'usePresentationRequest'">
                                <p class="text-sm font-semibold leading-6 text-gray-900">Used presentation request</p>
                                <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                    <code>{{ JSON.stringify(element.data) }}</code>
                                </p>
                            </div>
                            <div v-else>
                                <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.event }}</p>
                                <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                    <code>{{ JSON.stringify(element.data) }}</code>
                                </p>
                            </div> -->

                            <!--<p :class="[statuses[project.status], 'rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset']">{{ project.status }}</p>-->
                        </div>
                        <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                            <p class="whitespace-nowrap">
                                <time :datetime="element.timestamp"
                                    >{{
                                        new Intl.DateTimeFormat("de-DE", {
                                            dateStyle: "medium",
                                            timeStyle: "long",
                                        }).format(new Date(element.timestamp))
                                    }}
                                </time>
                            </p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.event }}</p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.action }}</p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.tenant }}</p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.originator }}</p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.account }}</p>
                            <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.wallet }}</p>
                            <div class="flex flex-none items-center gap-x-4">
                                <NuxtLink
                                    :to="`/wallet/${currentWallet}/settings/dids`"
                                    class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                                >
                                    View Data
                                </NuxtLink>
                            </div>
                            <!-- <span class="text-sm font-semibold leading-6 text-gray-900">{{ Json.parse(element.data) }}</span> -->
                        </div>
                    </div>
                </li>
            </ul>
        </div>
        <div v-else class="mt-2 border-l pl-2">
            <h3 class="mt-2 text-base font-semibold text-gray-900">No eventLog entries.</h3>
            <p class="text-base text-gray-500">No operations have been logged to your auditable eventLog yet.</p>
        </div>
    </CenterMain>
</template>

<script setup>
import CenterMain from "~/components/CenterMain.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";

const currentWallet = useCurrentWallet()
console.log("Loading EventLog...");
const eventLog = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/eventlog`).data;
refreshNuxtData();
// const { data: eventLog, pending: pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/eventlog`);
// refreshNuxtData();
// const { data: eventLog, pending: pending, refresh, error } = await useLazyAsyncData(() => $fetch(`/wallet-api/wallet/${currentWallet.value}/eventlog`));
</script>

<style scoped></style>
