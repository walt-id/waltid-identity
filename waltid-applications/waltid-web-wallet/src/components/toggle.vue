<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits(['update:option1Selected'])

const option1Selected = ref(true)

const handleOptionChange = () => {
    option1Selected.value = !option1Selected.value
    emit('update:option1Selected', option1Selected.value)
}

const props = defineProps<{
    options: string[]
}>()

</script>

<template>
    <label class="relative inline-flex cursor-pointer items-center justify-center rounded-md bg-white p-1">
        <input type="checkbox" class="sr-only" @change="handleOptionChange" />
        <span :class="`${option1Selected ? 'bg-[#002159] text-white' : ''}`" class=" text-black flex items-center space-x-[6px] rounded py-2 px-[18px] text-sm
            font-bold">
            {{ options[0] }}
        </span>
        <span :class="`${!option1Selected ? 'bg-[#002159] text-white' : ''}`"
            class="flex items-center space-x-[6px] rounded py-2 px-[18px] text-sm text-black font-bold">
            {{ options[1] }}
        </span>
    </label>
</template>
