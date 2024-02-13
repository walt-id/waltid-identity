<template>
    <div>
        <div class="flex items-center justify-between">
            <button class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                    type="button"
                    @click="model?.push('')"
            >
                <Icon name="heroicons:plus-circle" />
                Add another
            </button>

            <span>Total: {{ model?.length }}</span>
        </div>

        <ul v-if="model && model.length > 0" class="list-decimal border shadow pl-6 py-2 pr-2 mt-2">
            <li v-for="idx in Object.keys(model)" :key="idx">
                this is {{ idx }}, at model[idx] is {{ model[idx] }}
                <div class="flex items-center gap-1 py-0.5">
                    <DidInput v-model="model[idx]" class="grow" />
                    <button
                        v-if="model.length > 1"
                        class="flex items-center gap-1 rounded-md bg-white px-2.5 py-1.5 text-xs font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                        type="button"
                        @click="deleteIndex(idx)"
                    >
                        Delete
                        <Icon name="heroicons:trash" />
                    </button>
                </div>
            </li>
        </ul>
        <div v-else class="font-semibold">No DIDs defined in this array.</div>
    </div>
</template>

<script lang="ts" setup>
import type { ModelRef } from "vue";
import DidInput from "~/components/inputs/DidInput.vue";

function deleteIndex(idx: number) {
    model.value?.splice(idx, 1)
}

const model: ModelRef<string[] | undefined> = defineModel();
</script>
