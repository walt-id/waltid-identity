<template>
    <div>
        <CloseButton />
        <div class="flex min-h-full items-end p-4 sm:items-center sm:p-0">
            <div class="sm:flex sm:items-start">
                <div class="shadow p-3 mt-2 font-mono">
                    <h3 class="font-semibold mb-2">{{ props.title }}</h3>
                    <pre v-if="props.data != null">{{ JSON.stringify(props.data, null, 2) }}</pre>
                    <pre v-else>No data available.</pre>
                </div>
            </div>
        </div>
        <OkButton />
    </div>
</template>

<script lang="ts" setup>
import CloseButton from "./CloseButton.vue";
import OkButton from "./OkButton.vue";

const props = defineProps<{
    title: string;
    data: object;
}>();
</script>
