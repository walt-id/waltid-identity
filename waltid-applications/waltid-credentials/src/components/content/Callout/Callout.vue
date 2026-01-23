<template>
    <div class="pt-4 px-4 bg-slate-800 text-slate-50 rounded-md border-l-4 flex gap-3" :class="borderClass">
        <div>
            <CheckCircleIcon v-if="type === 'Success'" class="text-green-700 h-6 mt-1" />
            <InformationCircleIcon v-if="type === 'Info'" class="text-primary-300 h-6 mt-1" />
            <ExclamationTriangleIcon v-if="type === 'Error'" class="text-red-400 h-6 mt-1" />
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
import { CheckCircleIcon, InformationCircleIcon, ExclamationTriangleIcon } from "@heroicons/vue/24/outline";
import { computed } from "vue";

const props = defineProps({
    type: {
        type: String,
        default: "Info",
        validator: (value: string) => ["Success", "Error", "Info"].includes(value),
        required: false,
    },
});

const borderClass = computed(() => {
    switch (props.type) {
        case "Success":
            return "border-green-500";
        case "Error":
            return "border-red-400";
        case "Info":
            return "border-primary-400";
        default:
            return "";
    }
});
</script>
