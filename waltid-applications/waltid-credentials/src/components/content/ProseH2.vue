<template>
    <h2 :id="id" class="font-semibold text-3xl mb-2 mt-10 text-gray-50 group">
        <span class="absolute left-[-2%] text-primary-300 hidden group-hover:block">#</span>
        <NuxtLink v-if="id && generate" :to="{ hash: `#${id}` }">
            <slot />
        </NuxtLink>
        <slot v-else />
    </h2>
</template>

<script lang="ts" setup>
import { computed, useRuntimeConfig } from "#imports";

const route = useRoute();
const props = defineProps<{ id?: string }>();

const { headings } = useRuntimeConfig().public.mdc;
const generate = computed(() => props.id && headings?.anchorLinks?.h2);
</script>
