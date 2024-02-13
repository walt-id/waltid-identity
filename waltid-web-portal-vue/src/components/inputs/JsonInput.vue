<template>
    <ClientOnly>
        <LazyMonacoEditor v-model="model" class="h-full" lang="json" />
    </ClientOnly>
</template>

<script setup lang="ts">
import type { ModelRef } from "vue";

const model: ModelRef<string | undefined> = defineModel();
</script>

<style scoped>

</style>
