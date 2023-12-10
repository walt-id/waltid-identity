<template>
    <DidCreationView :didParams="{ domain: domain, path: path }" method="web">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
            <label class="block font-medium text-gray-900">Domain</label>
            <div class="mt-1 sm:col-span-2 sm:mt-0">
                <input
                    v-model="domain"
                    class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    placeholder="(optional)"
                    type="text"
                />
            </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2">
            <label class="block font-medium text-gray-900">Path</label>
            <div class="mt-1 sm:col-span-2 sm:mt-0">
                <input
                    id="keyId"
                    v-model="path"
                    class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    name="keyId"
                    placeholder="(optional)"
                    type="text"
                />
            </div>
        </div>
    </DidCreationView>
</template>

<script lang="ts" setup>
import DidCreationView from "~/components/dids/DidCreationView.vue";

const domain = ref("");
const path = ref("");
</script>
