<template>
    <button
        :disabled="loading"
        class="group relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-gray-900 hover:bg-red-600 hover:text-white duration-150"
        @click="handleClick"
    >
        <component :is="icon" v-if="!loading" class="h-5 w-5 text-gray-400 group-hover:text-gray-200" />
        <InlineLoadingCircle v-else-if="loading" class="h-5 pr-2 text-gray-400 group-hover:text-gray-200" />
        <span v-if="!loading">{{ props.displayText }}</span>
        <span v-else>{{ props.actionText }}</span>
    </button>
</template>

<script setup>
import InlineLoadingCircle from "~/components/loading/InlineLoadingCircle.vue";

const emit = defineEmits(["click"]);
const props = defineProps(["handler", "icon", "displayText", "actionText"]);

const icon = ref(props.icon);

const loading = ref(false);

function handleClick() {
    loading.value = true;
    emit("click");
}
</script>

<style scoped></style>
