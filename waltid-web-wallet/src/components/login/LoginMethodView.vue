<template>
    <div class="flex flex-col h-full bg-opacity-95 p-6
            focus-within:(shadow-2xl shadow-blue-500)
            hover:(ring-2 ring-inset ring-blue-500)"
    >
        <div>
            <span class="inline-flex rounded-lg p-3">
                <Icon :name="icon" aria-hidden="true" class="h-8 w-8" />
            </span>
        </div>

        <div class="mt-5">
            <h3 class="text-base font-semibold leading-6 text-gray-900">
                {{ name }}
            </h3>
            <p class="mt-2 text-sm text-gray-500">{{ description }}</p>
        </div>

        <div class="p-2 grow flex items-center">
            <slot />
        </div>
    </div>
</template>

<script lang="ts" setup>
const props = defineProps({
    idx: {
        type: Number,
        required: true,
    },
    name: {
        type: String,
        required: true,
    },
    description: {
        type: String,
        required: false,
        default: "",
    },
    icon: {
        type: String,
        required: true,
    },
    id: {
        type: String,
        required: true,
    },
});
</script>

<style scoped></style>
