<template>
    <CenterMain>
        <div>
            <h1 class="block text-lg font-semibold leading-6 text-gray-900">
                <label for="key"> Import your key in PEM or JWK format:</label>
            </h1>

            <div class="mt-1">
        <textarea
            id="key"
            v-model="keyText"
            class="block w-full px-3 rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
            name="key"
            rows="6"
        />
            </div>

            <div class="mt-4">
                <label for="alias" class="block text-sm font-medium text-gray-700">Alias (optional)</label>
                <input
                    id="alias"
                    v-model="alias"
                    type="text"
                    placeholder="e.g. Signing key"
                    class="mt-1 block w-full rounded-md border-0 py-1.5 px-3 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                />
                <p class="mt-1 text-xs text-gray-500">
                    If your JWK doesn't include an alias/name, we'll attach this alias. For PEM keys, alias cannot be
                    embedded and may be ignored.
                </p>
            </div>

            <div class="mt-4 flex justify-end">
                <button
                    class="inline-flex items-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="importKey"
                >
                    <DocumentPlusIcon
                        aria-hidden="true"
                        class="h-5 w-5 text-white mr-1"
                    />
                    <span>Import key</span>
                </button>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {DocumentPlusIcon} from "@heroicons/vue/24/outline";

const keyText = ref("");
const alias = ref("");
const currentWallet = useCurrentWallet();

function tryInjectAliasIntoJwk(text: string, alias?: string): string {
    if (!text) return text;
    const trimmed = text.trim();
    if (!trimmed.startsWith("{")) return text;
    try {
        const obj = JSON.parse(trimmed);
        const hasAlias = typeof obj.alias === "string" && obj.alias.length > 0;
        const hasName = typeof obj.name === "string" && obj.name.length > 0;
        if ((!hasAlias && !hasName) && alias && alias.trim().length > 0) {
            obj.alias = alias.trim();
        }
        return JSON.stringify(obj);
    } catch (_) {
        return text;
    }
}

async function importKey() {
    const bodyText = tryInjectAliasIntoJwk(keyText.value, alias.value);
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/import?alias=${encodeURIComponent(alias.value)}`, {
        method: "POST",
        body: bodyText,
    });
    navigateTo(`/wallet/${currentWallet.value}/settings/keys`);
}
</script>

<style scoped></style>
