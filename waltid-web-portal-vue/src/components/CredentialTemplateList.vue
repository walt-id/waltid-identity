<template>

    <div>
        <div class="relative flex items-center mt-1">
            <div class="absolute left-0 pl-1.5"><Icon name="heroicons:magnifying-glass" class="h-4 w-4"/></div>
            <input placeholder="Search template..." id="search" v-model="filter" class="pl-6 block w-full rounded-md border-0 px-2 py-1.5 pr-14 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" name="search"
                   type="text" accesskey="k"
            />
            <div class="absolute inset-y-0 right-0 flex py-1.5 pr-1.5">
                <button v-if="filter.length >= 1" @click="filter = ''" class="bg-white mr-2 border p-1 flex items-center rounded"><Icon name="fluent:clear-formatting-24-regular"/></button>
                <kbd class="inline-flex items-center rounded border border-gray-200 px-1 font-sans text-xs text-gray-400">⌘K</kbd>
            </div>
        </div>
    </div>

    <ul class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 mt-2" role="list">
        <li v-for="template in filteredCredentialTemplates" :key="template"
            class="col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow-lg border"
        >
            <div class="flex w-full items-center justify-between space-x-6 p-6">
                <div class="flex-1 truncate">
                    <div class="flex items-center space-x-3">
                        <h3 class="truncate text-lg font-bold text-gray-900">{{ template }}</h3>
                    </div>
                    <!--                    <p class="mt-1 truncate text-sm text-gray-500">{{ template }}</p>-->
                </div>
            </div>
            <div>
                <div class="-mt-px flex divide-x divide-gray-200">
                    <div class="flex w-0 flex-1">
                        <button
                            class="relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-1  border border-transparent py-4 text-sm font-semibold text-gray-900"
                            @click="setInspectingElement(template)"
                        >
                            <Icon class="h-5 w-5" name="heroicons:document-magnifying-glass" />
                            Inspect
                        </button>
                    </div>


                    <div v-for="(action) in actions" class="-ml-px flex w-0 flex-1">
                        <ActionButton :action="action" :template="template" />
                    </div>
                </div>
            </div>
        </li>
    </ul>
    <PageOverlay :isOpen="inspecting != null" :name="`Inspector: ${inspecting}`" @close="inspecting = null">
        <CredentialInspectView :template="inspecting" />
    </PageOverlay>
</template>

<script lang="ts" setup>
import type { Ref } from "vue";
import type { CredentialAction } from "~/composables/credential-action";


const props = defineProps({
    actions: {
        type: Array<CredentialAction>
    }
});

const config = useRuntimeConfig()

const { data: credentialTemplates, pending, error, refresh } = useFetch<Array<string>>(`${config.public.credentialRepository}/api/list`);
const filter = ref("");

const filteredCredentialTemplates = computed(() => {
    return credentialTemplates.value?.filter((value) =>
        value.toLowerCase().includes(filter.value.toLowerCase())
    );
});

const inspecting: Ref<String | null> = ref(null);

function setInspectingElement(template: string | null) {
    inspecting.value = template;
}

</script>

<style scoped>

</style>
