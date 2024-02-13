<template>
    <input v-model="model"
           class="block w-full min-w-0 grow flex-1 rounded-none rounded-r-md border-0 py-1.5 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
           type="number"
           :min="min"
           :max="max"
    />
</template>

<script setup lang="ts">
import type { ModelRef } from "vue";

const model: ModelRef<number | undefined> = defineModel();

const props = defineProps({
    min: {
        type: Number,
        required: false,
        default: 0
    },
    max: {
        type: Number,
        required: false
    }
})

</script>

<style scoped>

</style>
