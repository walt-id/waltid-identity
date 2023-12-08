<template>
    <DidCreationView :didParams="{ useJwkJcsPub: useJwkJcsPub }" method="key">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="format">Use jwk_jcs-pub format</label>
            <!-- <div class="mt-2 sm:col-span-2 sm:mt-0"> -->
            <div class="group flex relative">
                <input
                    id="default-checkbox"
                    v-model="useJwkJcsPub"
                    class="text-blue-600 bg-gray-100 border-gray-300 rounded focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800 focus:ring-2 dark:bg-gray-700 dark:border-gray-600"
                    disabled
                    type="checkbox"
                />
                <!-- tooltip -->
                <span class="group-hover:opacity-100 transition-opacity bg-gray-800 px-1 text-sm text-gray-100 rounded-md absolute -translate-x-1/2 opacity-0 m-4 mx-auto">
                    The only supported currently.
                </span>
            </div>
        </div>
    </DidCreationView>
</template>

<script lang="ts" setup>
import DidCreationView from "~/components/dids/DidCreationView.vue";

const useJwkJcsPub = ref(true);
</script>
