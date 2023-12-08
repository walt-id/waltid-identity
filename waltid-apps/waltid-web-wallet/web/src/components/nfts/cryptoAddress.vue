<template>
    <span :on-mouseenter="toggleTooltip(true)" :on-mouseleave="toggleTooltip(false)" class="text-label text-natural ml-2 leading-none dark:text-natural-300"
        >{{ computedText }}
        <span v-if="tooltip" class="">{{ props.text }}</span>
    </span>
</template>

<script lang="ts" setup>
const props = withDefaults(
    defineProps<{
        text?: string;
    }>(),
    {
        text: "Lorem ipsum dolor, sit amet consectetur adipisicing elit. Illum recusandae reprehenderit sint neque voluptatum dicta iste animi eveniet doloribus? Ipsam unde quo illo! Maxime, similique est quod nisi dolor excepturi.",
    },
);

const tooltip = ref(false);
const computedText = computed(() => {
    if (props.text.length > 6) {
        return props.text.substring(0, 6) + "..." + props.text.substring(props.text.length - 4);
    }
    return props.text;
});

function toggleTooltip(value: boolean) {
    tooltip.value = value;
}
</script>
<style scoped>
.tooltip {
    position: relative;
    display: inline-block;
    border-bottom: 1px dotted black;
}

.tooltip .tooltiptext {
    visibility: hidden;
    width: 120px;
    background-color: black;
    color: #fff;
    text-align: center;
    border-radius: 6px;
    padding: 5px 0;

    /* Position the tooltip */
    position: absolute;
    z-index: 1;
}

.tooltip:hover .tooltiptext {
    visibility: visible;
}
</style>
