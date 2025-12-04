<template>
    <li :class="marginClass" class="my-2">
        <NuxtLink :to="{hash: `#${tocElement.id}`}" class="text-sm cursor-pointer hover:text-primary-300">
            <component :is="tocElement.tag" :id="tocElement.id">{{ tocElement.value }} </component>
        </NuxtLink>
    </li>
</template>

<script setup lang="ts">
interface TableOfContentsElement {
    tag: string;
    id: string;
    value: string;
}

const props = defineProps<{
    tocElement: TableOfContentsElement;
}>();

const { tocElement } = props;
const route = useRoute();

const marginClasses: Record<string, string> = {
    h2: "ml-0",
    h3: "ml-1.5",
    h4: "ml-3",
    h5: "ml-6",
    h6: "ml-6",
};

const marginClass = computed(() => marginClasses[tocElement.tag]);
</script>
