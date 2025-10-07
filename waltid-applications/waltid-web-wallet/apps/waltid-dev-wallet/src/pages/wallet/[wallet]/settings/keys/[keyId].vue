<template>
    <CenterMain>
        <BackButton/>
        <div>
            <h2 class="text-lg font-semibold leading-7 text-gray-900">
                Key: {{ displayKid }}
            </h2>
            <p class="mt-1 max-w-2xl text-sm leading-6 text-gray-600">
                We allow you to export your keypair, however, make sure you keep it
                safe, so that you cannot be impersonated.
            </p>
            Be careful of what information of this page you share.
            <span class="font-semibold">Never share your private key!</span>
        </div>

        <div class="mt-6 border p-4 rounded-2xl">
            <p class="text-base font-semibold">Key information</p>
            <div>
                <div
                    class="mt-3 space-y-4 border-gray-900/10 pb-2 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0"
                >
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Alias</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ aliasName || jwk?.alias || jwk?.name || '—' }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Key ID (kid)</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0 break-all">{{ displayKid }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Algorithm</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ meta?.algorithm || jwk?.alg || '—' }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Key type (kty)</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ jwk?.kty || '—' }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
                        <label class="block font-medium text-gray-900">Curve (crv)</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ jwk?.crv || '—' }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-2">
                        <label class="block font-medium text-gray-900">Use</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ jwk?.use || '—' }}</div>
                    </div>
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-2">
                        <label class="block font-medium text-gray-900">Crypto provider</label>
                        <div class="mt-1 sm:col-span-2 sm:mt-0">{{ meta?.cryptoProvider || '—' }}</div>
                    </div>
                </div>
            </div>

            <div class="mt-4">
                <label class="block font-medium text-gray-900 mb-1">Public JWK</label>
                <pre class="text-xs bg-gray-50 p-3 rounded border overflow-auto">{{ formattedJwk }}</pre>
            </div>

            <div class="mt-4 flex items-center justify-end gap-x-6">
                <button
                    class="inline-flex justify-center bg-red-600 hover:bg-red-500 focus-visible:outline-red-700 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="deleteKey"
                >
          <span class="inline-flex place-items-center gap-1">
            <TrashIcon class="w-5 h-5 mr-0.5" />
            Delete key
          </span>
                </button>
            </div>
        </div>

        <div class="mt-6 border p-4 rounded-2xl">
            <div>
                <p class="text-base font-semibold">Export key</p>
                <div
                    class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0"
                >
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                        <label
                            class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5"
                        >Private key
                        </label>
                        <div class="mt-2 sm:col-span-2 sm:mt-0">
                            <SwitchGroup as="div" class="flex items-center">
                                <Switch
                                    v-model="enableLoadPrivateKey"
                                    :class="[
                    enableLoadPrivateKey ? 'bg-blue-600' : 'bg-gray-200',
                    'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2',
                  ]"
                                >
                  <span
                      :class="[
                      enableLoadPrivateKey ? 'translate-x-5' : 'translate-x-0',
                      'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
                    ]"
                      aria-hidden="true"
                  />
                                </Switch>
                                <SwitchLabel
                                    as="span"
                                    class="ml-3 text-sm inline-flex gap-1 place-items-center"
                                >
                                    <ExclamationTriangleIcon class="w-5 h-5"/>
                                    <span class="font-medium text-gray-900"
                                    >Load private key</span
                                    >
                                    {{ " " }}
                                    <span class="text-gray-500"
                                    >(make sure you have no screenshares running)</span
                                    >
                                </SwitchLabel>
                            </SwitchGroup>
                        </div>
                    </div>

                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                        <label
                            class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5"
                            for="format"
                        >Format
                        </label>
                        <div class="mt-2 sm:col-span-2 sm:mt-0">
                            <select
                                id="format"
                                v-model="format"
                                class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                name="format"
                            >
                                <option value="JWK">JWK</option>
                                <option value="PEM">PEM</option>
                            </select>

                            <p class="mt-3 text-sm leading-6 text-gray-600">
                                Depending on the program you want to import your key in, you can
                                choose different formats to export your key(/pair) with.
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div class="mt-2 flex items-center justify-end gap-x-6">
                <button
                    :class="[
            enableLoadPrivateKey
              ? 'bg-yellow-500 hover:bg-yellow-400 focus-visible:outline-yellow-600'
              : 'bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600',
          ]"
                    class="inline-flex justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                    @click="exportKey"
                >
          <span class="inline-flex place-items-center gap-1">
            <ExclamationTriangleIcon
                v-if="enableLoadPrivateKey"
                class="w-5 h-5 mr-0.5"
            />
            <ArrowUpOnSquareIcon v-else class="w-5 h-5 mr-0.5" />
            Export
          </span>
                </button>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import BackButton from "@waltid-web-wallet/components/buttons/BackButton.vue";
import {ArrowUpOnSquareIcon, ExclamationTriangleIcon, TrashIcon} from "@heroicons/vue/24/outline";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import {computed, onMounted, ref} from "vue";

import {Switch, SwitchGroup, SwitchLabel} from "@headlessui/vue";

const route = useRoute();

const keyId = route.params.keyId as string;

const format = ref("JWK");
const enableLoadPrivateKey = ref(false);

const currentWallet = useCurrentWallet();

const jwk = ref<any | null>(null);
const meta = ref<any | null>(null);
const aliasName = ref<string | null>(null);

const displayKid = computed(() => jwk.value?.kid || keyId);
const formattedJwk = computed(() => (jwk.value ? JSON.stringify(jwk.value, null, 2) : "Loading…"));

async function loadData() {
    try {
        jwk.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/${keyId}/load`);
    } catch (e) {
        jwk.value = null;
    }
    try {
        meta.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/${keyId}/meta`);
    } catch (e) {
        meta.value = null;
    }
    try {
        const list: any[] = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys`);
        const found = list.find((k: any) => k?.keyId?.id === keyId || k?.keyId?.id === jwk.value?.kid);
        aliasName.value = found?.name || null;
    } catch (e) {
        aliasName.value = null;
    }
}

onMounted(loadData);

function exportKey() {
  navigateTo(
    `export/${keyId}?format=${format.value}&loadPrivateKey=${enableLoadPrivateKey.value}`,
  );
}

async function deleteKey() {
  await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/${keyId}`, {
    method: "DELETE",
  }).finally(() => {
    navigateTo(`/wallet/${currentWallet.value}/settings/keys`);
  });
}

useHead({
  title: "View key - walt.id",
});
</script>

<style scoped></style>
