<template>
    <div>
        <button class="rounded-md text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2" type="button" @click="store.closeModal">
            <span class="sr-only">Close</span>
            <XMarkIcon aria-hidden="true" class="h-6 w-6" />
        </button>
    </div>
</template>

<script lang="ts" setup>
import { XMarkIcon } from "@heroicons/vue/24/outline";
import useModalStore from "~/stores/useModalStore";

const store = useModalStore();
</script>
