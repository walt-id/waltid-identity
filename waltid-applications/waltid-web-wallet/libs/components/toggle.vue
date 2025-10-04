<template>
    <label class="relative inline-flex cursor-pointer items-center justify-center rounded-md bg-white p-1 gap-2">
        <input type="checkbox" class="sr-only" @change="handleOptionChange" />
        <span
            :class="`${option1Selected ? 'bg-[#002159] text-white sm:bg-gradient-to-r from-[#0573F0] to-[#03449E]' : 'sm:border border-[#002159] text-black'}`"
            class="flex items-center space-x-[6px] rounded sm:rounded-full py-2 px-[18px] text-sm font-bold">
            {{ options[0] }}
        </span>
        <span
            :class="`${!option1Selected ? 'bg-[#002159] text-white sm:bg-gradient-to-r from-[#0573F0] to-[#03449E]' : 'sm:border border-[#002159] text-black'}`"
            class="flex items-center space-x-[6px] rounded sm:rounded-full py-2 px-[18px] text-sm font-bold">
            {{ options[1] }}
        </span>
    </label>
</template>

<script setup lang="ts">
import {ref} from "vue";

const props = defineProps<{
    options: string[],
    option1Selected?: boolean
}>()
const emit = defineEmits(['update:option1Selected'])

const option1Selected = ref(props.option1Selected ?? true)
const handleOptionChange = () => {
    option1Selected.value = !option1Selected.value
    emit('update:option1Selected', option1Selected.value)
}
</script>
