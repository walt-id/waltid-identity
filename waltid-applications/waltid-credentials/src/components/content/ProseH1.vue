<template>
    <h1 :id="id" class="relative font-semibold text-4xl mb-10 text-gray-50 cursor-pointer group">
        <span class="absolute left-[-32px] text-primary-300 hidden group-hover:block">#</span>
        <NuxtLink v-if="id && generate" :to="{ hash: `#${id}` }">
            <slot />
        </NuxtLink>
        <slot v-else />
    </h1>
</template>

<script lang="ts" setup>
import { computed, useRuntimeConfig } from "#imports";

const route = useRoute();
const props = defineProps<{ id?: string }>();

const { headings } = useRuntimeConfig().public.mdc;
const generate = computed(() => props.id && headings?.anchorLinks?.h1);
</script>
