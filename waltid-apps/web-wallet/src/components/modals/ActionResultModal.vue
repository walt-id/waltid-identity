<template>
    <div>
        <CloseButton />
        <div class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
            <div class="sm:flex sm:items-start">
                <div :class="[props.isError ? 'bg-red-100' : 'bg-green-600']" class="mx-auto flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full sm:mx-0 sm:h-10 sm:w-10">
                    <ExclamationCircleIcon v-if="props.isError" aria-hidden="true" class="h-6 w-6 text-red-600" />
                    <CheckCircleIcon v-if="!props.isError" aria-hidden="true" class="h-6 w-6 text-green-100" />
                </div>
                <div class="mt-3 text-center sm:ml-4 sm:mt-0 sm:text-left">
                    <DialogTitle as="h3" class="text-base font-semibold leading-6 text-gray-900 dark:text-white shadow-sm">
                        {{ props.title }}
                    </DialogTitle>
                    <div class="mt-2">
                        <p class="text-sm text-gray-500 dark:text-gray-400 shadow-sm">
                            {{ props.message }}
                        </p>
                    </div>
                </div>
            </div>
        </div>
        <OkButton />
    </div>
</template>

<script lang="ts" setup>
import { CheckCircleIcon, ExclamationCircleIcon } from "@heroicons/vue/24/outline";
import useModalStore from "~/stores/useModalStore";
import CloseButton from "./CloseButton.vue";
import OkButton from "./OkButton.vue";

const store = useModalStore();

const props = defineProps<{
    title: string;
    message: string;
    isError: boolean;
    callback: () => void;
}>();
</script>
