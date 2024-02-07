<template>
    <CenterMain>
        <h1 class="text-2xl font-semibold mb-2">History</h1>

        <LoadingIndicator v-if="pending" class="my-6 mb-12 w-full"> Loading history... </LoadingIndicator>

        <p class="mb-1">The last 10 operations are shown below:</p>

        <div v-if="groupedHistoryElements.size > 0" aria-label="Credential list" class="h-full overflow-y-auto shadow-xl">
            <div v-for="group in groupedHistoryElements.keys()" :key="group.id" class="relative">
                <div class="sticky top-0 z-10 border-y border-b-gray-200 border-t-gray-100 bg-gray-50 px-3 py-1.5 text-sm font-semibold leading-6 text-gray-900">
                    <h3>{{ group }}:</h3>
                </div>
                <ul class="divide-y divide-gray-100 overflow-x-clip" role="list">
                    <li v-for="element in groupedHistoryElements.get(group)" :key="element" class="flex gap-x-4 px-3 py-5">
                        <!--<CredentialIcon :credentialType="element.name" class="h-6 w-6 flex-none rounded-full bg-gray-50"></CredentialIcon>-->

                        <div class="min-w-0">
                            <div class="flex items-start gap-x-3">
                                <span class="text-sm font-semibold leading-6 text-gray-900">{{ element.id }}.</span>

                                <div v-if="element.entry.operation === 'useOfferRequest'">
                                    <p class="text-sm font-semibold leading-6 text-gray-900">Used offer request</p>
                                    <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                        <code>{{ JSON.stringify(element.entry.data) }}</code>
                                    </p>
                                </div>
                                <div v-else-if="element.entry.operation === 'usePresentationRequest'">
                                    <p class="text-sm font-semibold leading-6 text-gray-900">Used presentation request</p>
                                    <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                        <code>{{ JSON.stringify(element.entry.data) }}</code>
                                    </p>
                                </div>
                                <div v-else>
                                    <p class="text-sm font-semibold leading-6 text-gray-900">{{ element.entry.operation }}</p>
                                    <p class="text-xs overflow-x-scroll w-1/3 h-5">
                                        <code>{{ JSON.stringify(element.entry.data) }}</code>
                                    </p>
                                </div>

                                <!--<p :class="[statuses[project.status], 'rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset']">{{ project.status }}</p>-->
                            </div>
                            <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                                <p class="whitespace-nowrap">
                                    At
                                    <time :datetime="element.entry.timestamp"
                                        >{{
                                            new Intl.DateTimeFormat("de-DE", {
                                                dateStyle: "medium",
                                                timeStyle: "long",
                                            }).format(new Date(element.entry.timestamp))
                                        }}
                                    </time>
                                </p>
                                <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                                    <circle cx="1" cy="1" r="1" />
                                </svg>
                                <p class="truncate w-1/4">
                                    Commenced by <code>{{ element.entry.data.did }}</code>
                                </p>
                            </div>
                        </div>
                    </li>
                </ul>
            </div>
        </div>
        <div v-else class="mt-2 border-l pl-2">
            <h3 class="mt-2 text-base font-semibold text-gray-900">No history entries.</h3>
            <p class="text-base text-gray-500">No operations have been logged to your auditable history yet.</p>
        </div>
    </CenterMain>
</template>

<script setup>
import CenterMain from "~/components/CenterMain.vue";
import { groupBy } from "~/composables/groupings";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";

const groupedHistoryElements = ref({});

const currentWallet = useCurrentWallet()

console.log("Loading history...");
const { data: history, pending: pending, refresh, error } = await useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/history`);
refreshNuxtData();

watch(history, () => {
    console.log("Received history: ", history);
    console.log("History raw:", toRaw(history));
    if (history != null && history.value != null) {
        console.log("Grouping history.");
        let i = 0;
        groupedHistoryElements.value = groupBy(
            history.value.map((entry) => {
                return { id: ++i, date: new Date(entry.timestamp).toDateString(), entry: entry };
            }),
            (c) => c.date,
        );
        console.log("Grouped: ", groupedHistoryElements.value);
    } else {
        console.log("History is null.");
    }
});

// const groupedCredentialTypes = groupBy(credentialTypes.map(item => {
//     return {id: ++i, name: item}
// }), c => c.name)
</script>

<style scoped></style>
