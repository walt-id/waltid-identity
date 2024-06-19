<template>
    <DidCreationView :didParams="{ bearerToken: bearerToken, version: version }" method="ebsi">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
            <label class="block font-medium text-gray-900">Bearer token</label>
            <div class="mt-1 sm:col-span-2 sm:mt-0">
                <input
                    v-model="bearerToken"
                    class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    placeholder="(required for v1)"
                    type="text"
                />
            </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="format">Version</label>
            <div class="mt-2 sm:col-span-2 sm:mt-0">
                <select
                    id="format"
                    v-model="version"
                    class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    name="format"
                >
                    <option value="2">v2 (NaturalPerson)</option>
                    <option value="1">v1 (LegalEntity)</option>
                </select>
            </div>
        </div>
    </DidCreationView>
</template>

<script lang="ts" setup>
import DidCreationView from "~/components/dids/DidCreationView.vue";

const bearerToken = ref("");
const version = ref(2);
</script>
