<template>
    <DidCreationView :didParams="{ network: network }" method="cheqd">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="format">Network</label>
            <div class="mt-2 sm:col-span-2 sm:mt-0">
                <select
                    id="format"
                    v-model="network"
                    class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    name="format"
                >
                    <option value="testnet">testnet</option>
                    <option value="mainnet">mainnet</option>
                </select>
            </div>
        </div>
    </DidCreationView>
</template>

<script lang="ts" setup>
import DidCreationView from "~/components/dids/DidCreationView.vue";

const network = ref("testnet");
</script>
