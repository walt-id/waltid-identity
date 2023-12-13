<template>
    <CenterMain>
        <h1 class="text-2xl font-semibold mb-2">EventLog</h1>

        <LoadingIndicator v-if="pending" class="my-6 mb-12 w-full"> Loading eventLog... </LoadingIndicator>

        <p class="mb-1">The list of events is shown below:</p>

        <div v-if="eventLog?.count > 0" aria-label="Credential list" class="h-full overflow-y-auto shadow-xl">
            <div class="container mx-auto px-4 sm:px-8">
    <div class="py-8">
        <!-- table source: https://codepen.io/superfly/pen/wvvPLZB -->
        <div>
            <h2 class="text-2xl font-semibold leading-tight">List of events</h2>
        </div>
        <div class="-mx-4 sm:-mx-8 px-4 sm:px-8 py-4 overflow-x-auto">
            <div class="inline-block min-w-full shadow-md rounded-lg overflow-hidden">
                <table class="min-w-full leading-normal">
                    <thead>
                        <tr>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                Timestamp
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                Event
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                Action
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                Tenant
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                Originator
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                
                            </th>
                            <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100"></th>
                        </tr>
                    </thead>
                    <tbody>
                        <template v-for="item in eventLog.items" :key="item">
                            <tr>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                    <div class="flex">
                                        <div class="ml-3">
                                            <p class="text-gray-900 whitespace-no-wrap">
                                                {{item.timestamp}}
                                            </p>
                                            <p class="text-gray-600 whitespace-no-wrap">000004</p>
                                        </div>
                                    </div>
                                </td>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                    <p class="text-gray-900 whitespace-no-wrap">{{item.event}}</p>
                                </td>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                    <p class="text-gray-900 whitespace-no-wrap">{{item.action}}</p>
                                </td>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                    <p class="text-gray-900 whitespace-no-wrap">{{item.tenant}}</p>
                                </td>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                    <p class="text-gray-900 whitespace-no-wrap">{{item.originator}}</p>
                                </td>
                                <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm text-right">
                                    <button type="button" class="inline-block text-gray-500 hover:text-gray-700">
                                        <svg class="inline-block h-6 w-6 fill-current" viewBox="0 0 24 24">
                                            <path d="M12 6a2 2 0 110-4 2 2 0 010 4zm0 8a2 2 0 110-4 2 2 0 010 4zm-2 6a2 2 0 104 0 2 2 0 00-4 0z" />
                                        </svg>
                                    </button>
                                </td>
                            </tr>
                        </template>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>
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
