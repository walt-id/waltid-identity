<template>
    <button
        class="relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-1 border border-transparent py-4 text-sm font-semibold text-gray-900"
        @click="handleClick"
    >
        <Spinner v-if="loading" class="h-5 w-5" />
        <Icon v-else :name="action.icon" class="h-5 w-5" />
        {{ action.name }}
    </button>
</template>

<script lang="ts" setup>
import type { CredentialAction } from "~/composables/credential-action";

const loading = ref(false);

async function handleClick() {
    loading.value = true;
    await (props.action.action(props.template));
    loading.value = false;
}

const props = defineProps<{
    template: string
    action: CredentialAction
}>();
</script>
