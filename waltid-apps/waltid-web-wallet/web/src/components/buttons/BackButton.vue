<template>
    <button class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center" @click="router.back">
        <ArrowUturnLeftIcon class="h-5 pr-1" />
        Return back
    </button>
</template>

<script setup>
import { ArrowUturnLeftIcon } from "@heroicons/vue/24/outline";

const router = useRouter();
</script>

<style scoped></style>
