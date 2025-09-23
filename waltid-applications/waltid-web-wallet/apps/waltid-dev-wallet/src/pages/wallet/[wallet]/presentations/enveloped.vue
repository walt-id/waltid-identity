<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";

const route = useRoute()
const currentWallet = useCurrentWallet();

const dids = ref<Array<{ did: string; default: boolean }>>([])
const selectedDid = ref<string>('')

const credentials = ref<any[]>([])
const selectedCredentialIds = ref<string[]>([])

const loading = ref(false)
const errorMsg = ref<string>('')
const result = ref<{ vp_jwt: string; envelope: any } | null>(null)

async function loadDids() {
    dids.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/dids`)
    const def = dids.value.find((d) => d.default)
    selectedDid.value = def?.did || dids.value[0]?.did || ''
}

async function loadCredentials() {
    credentials.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials?showDeleted=false&showPending=false`)

}

async function generate() {
    errorMsg.value = ''
    result.value = null
    if (!selectedDid.value) {
        errorMsg.value = 'Please select a DID'
        return
    }
    if (selectedCredentialIds.value.length === 0) {
        errorMsg.value = 'Please select at least one credential'
        return
    }
    loading.value = true
    try {
        const body = {
            did: selectedDid.value,
            credentialIds: selectedCredentialIds.value,
        }
        result.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/presentations/enveloped`, {
            method: 'POST',
            body,
        })
    } catch (e: any) {
        errorMsg.value = e?.data?.message || e?.message || 'Failed to generate presentation'
    } finally {
        loading.value = false
    }
}

onMounted(async () => {
    await Promise.all([loadDids(), loadCredentials()])
})
</script>

<template>
    <div class="p-4 max-w-4xl mx-auto">
        <h1 class="text-2xl font-bold mb-4">Generate Enveloped Verifiable Presentation (JWT)</h1>

        <div class="mb-4">
            <label class="block font-medium mb-1">Holder DID</label>
            <select v-model="selectedDid" class="border rounded px-3 py-2 w-full">
                <option v-for="d in dids" :key="d.did" :value="d.did">
                    {{ d.did }} {{ d.default ? '(default)' : '' }}
                </option>
            </select>
        </div>

        <div class="mb-4">
            <label class="block font-medium mb-2">Select credentials to include</label>
            <div class="border rounded divide-y">
                <label
                    v-for="c in credentials"
                    :key="c.id"
                    class="flex items-center gap-3 p-3 hover:bg-gray-50"
                >
                    <input type="checkbox" :value="c.id" v-model="selectedCredentialIds"/>
                    <div class="flex-1">
                        <div class="font-mono text-sm break-all">{{ c.id }}</div>
                        <div class="text-gray-500 text-sm">{{ c.parsedDocument?.type?.join?.(', ') || c.format }}</div>
                    </div>
                </label>
            </div>
        </div>

        <div class="mb-4 text-red-600" v-if="errorMsg">{{ errorMsg }}</div>

        <button class="btn btn-primary" :disabled="loading" @click="generate">
            {{ loading ? 'Generating…' : 'Generate VP-JWT' }}
        </button>

        <div v-if="result" class="mt-6 space-y-4">
            <div>
                <h2 class="font-semibold mb-2">VP JWT</h2>
                <textarea class="w-full border rounded p-2 font-mono text-xs" rows="5"
                          readonly>{{ result.vp_jwt }}</textarea>
            </div>
            <div>
                <h2 class="font-semibold mb-2">Envelope JSON</h2>
                <pre class="w-full border rounded p-2 text-xs overflow-auto">{{
                        JSON.stringify(result.envelope, null, 2)
                    }}</pre>
            </div>
        </div>
    </div>
</template>

<style scoped>
.btn {
    @apply px-4 py-2 rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50;
}
</style>
