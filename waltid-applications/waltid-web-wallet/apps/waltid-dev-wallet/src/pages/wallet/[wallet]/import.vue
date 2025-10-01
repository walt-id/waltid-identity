<template>
    <div>
        <WalletPageHeader/>
        <CenterMain>
            <div class="max-w-5xl mx-auto">
                <div class="flex justify-between items-center mb-4">
                    <h1 class="text-xl font-semibold">Import credential (JWT)</h1>
                </div>

                <div class="p-4 bg-white rounded shadow">
                    <div class="grid grid-cols-1 md:grid-cols-4 gap-3 items-start">
                        <div class="md:col-span-3">
                            <label class="block text-sm font-medium text-gray-700 mb-1">Signed VC JWT</label>
                            <textarea
                                v-model="jwtInput"
                                rows="6"
                                placeholder="Paste a signed VC JWT here..."
                                class="w-full rounded-md border border-gray-300 p-2 font-mono text-xs focus:ring-2 focus:ring-blue-500 focus:outline-none"
                            ></textarea>
                        </div>
                        <div class="md:col-span-1 w-full">
                            <label class="block text-sm font-medium text-gray-700 mb-1">Associated DID</label>
                            <select
                                v-model="selectedDid"
                                class="w-full h-10 rounded-md border border-gray-300 p-2 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                            >
                                <option disabled value="">Select DID</option>
                                <option v-for="d in dids" :key="d.did" :value="d.did">
                                    {{ d.alias || d.did }}
                                </option>
                            </select>
                            <NuxtLink
                                :to="`/wallet/${currentWallet}/settings/dids`"
                                class="text-xs text-blue-600 hover:underline mt-1 inline-block"
                            >
                                Manage DIDs
                            </NuxtLink>
                        </div>
                    </div>
                    <div class="mt-3 flex gap-2 items-center">
                        <button
                            @click="onImport"
                            :disabled="importing || !jwtInput || !selectedDid"
                            type="button"
                            class="inline-flex items-center rounded-md bg-blue-600 px-3 py-2 text-sm font-semibold text-white shadow-sm disabled:bg-gray-300 disabled:cursor-not-allowed hover:bg-blue-700"
                        >
                            <span v-if="!importing">Import credential</span>
                            <span v-else>Importing...</span>
                        </button>
                        <button @click="onClear" type="button"
                                class="inline-flex items-center rounded-md bg-blue-600 px-3 py-2 text-sm font-semibold text-white shadow-sm disabled:bg-gray-300 disabled:cursor-not-allowed hover:bg-blue-700">
                            Clear
                        </button>

                    </div>
                    <span v-if="importError" class="text-sm text-red-600">{{ importError }}</span>
                    <span v-if="importSuccess" class="text-sm text-green-700">Imported credential: {{
                            importSuccess
                        }}</span>
                </div>
            </div>
        </CenterMain>
    </div>
</template>

<script setup>
import WalletPageHeader from "@waltid-web-wallet/components/WalletPageHeader.vue";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";

const currentWallet = useCurrentWallet();

const jwtInput = ref("");
const importing = ref(false);
const importSuccess = ref("");
const importError = ref("");

const dids = ref(null);
const selectedDid = ref("");

onMounted(async () => {
    try {
        const result = await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids`);
        dids.value = result ?? [];
        const defaultDid = (dids.value || []).find((d) => d.default) || (dids.value || [])[0];
        selectedDid.value = defaultDid?.did || "";
    } catch (e) {
        console.error("Failed to load DIDs", e);
    }
});

async function onImport() {
    importError.value = "";
    importSuccess.value = "";
    if (!jwtInput.value || !selectedDid.value) return;
    importing.value = true;
    try {
        const res = await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials/import`, {
            method: "POST",
            body: {
                jwt: jwtInput.value.trim(),
                associated_did: selectedDid.value,
            },
        });
        importSuccess.value = res?.id || "success";
        jwtInput.value = "";
    } catch (e) {
        const msg = e?.data || e?.message || "Import failed";
        importError.value = typeof msg === "string" ? msg : JSON.stringify(msg);
    } finally {
        importing.value = false;
    }
}

function onClear() {
    jwtInput.value = "";
    importError.value = "";
    importSuccess.value = "";
}

useHead({title: "Import credential - walt.id"});
</script>
