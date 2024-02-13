<template>
    <div v-if="error" class="rounded-md bg-red-50 p-4 my-2">
        <div class="flex">
            <div class="flex-shrink-0">
                <Icon aria-hidden="true" class="h-5 w-5 text-red-400" name="heroicons:x-circle" />
            </div>
            <div class="ml-3">
                <h3 class="text-sm font-medium text-red-800">{{ title }}</h3>
                <div class="mt-2 text-sm text-red-700">
                    <ul class="list-disc space-y-1 pl-5" role="list">
                        <li>{{ error.name }}: {{ error.statusCode }} {{ error.statusMessage }}</li>
                        <li>Message: {{ error.message }}</li>
                        <li v-if="error.response">Response: {{ error.response }}</li>

                        <li v-if="error.data">
                            {{ error.data?.startsWith("{") ? JSON.parse(error.data)?.message ?? JSON.parse(error.data) : error.data
                            }}
                        </li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { FetchError } from "ofetch";

const props = defineProps<{
    error: FetchError<any> | null,
    title: string
}>()
</script>

<style scoped>

</style>
