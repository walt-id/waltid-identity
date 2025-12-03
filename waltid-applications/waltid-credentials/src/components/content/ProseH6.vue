<template>
    <h6 :id="id" class="font-semibold mb-1 mt-10 text-gray-50 group">
        <span class="absolute left-[-2px] text-primary-300 hidden group-hover:block">#</span>
        <NuxtLink v-if="id && generate" :to="{ hash: `#${id}` }">
            <slot />
        </NuxtLink>
        <slot v-else />
    </h6>
</template>

<script setup lang="ts">
import { computed, useRuntimeConfig } from "#imports";

const route = useRoute();
const props = defineProps<{ id?: string }>();

const { headings } = useRuntimeConfig().public.mdc;
const generate = computed(() => props.id && headings?.anchorLinks?.h6);
</script>
