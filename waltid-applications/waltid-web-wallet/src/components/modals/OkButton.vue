<template>
    <div class="mt-5 sm:mt-4 sm:flex sm:flex-row-reverse">
        <button
            class="mt-3 inline-flex w-full justify-center rounded-md bg-blue-500 hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 px-3 py-2 text-sm font-semibold text-neutral-50 shadow-sm ring-1 ring-inset ring-gray-300 sm:mt-0 sm:w-auto"
            type="button"
            @click="store.closeModal"
        >
            Okay
        </button>
    </div>
</template>

<script lang="ts" setup>
import useModalStore from "~/stores/useModalStore";

const store = useModalStore();
</script>
