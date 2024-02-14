<template>
    <div class="mx-auto max-w-7xl px-4 lg:px-6 py-3">
        <div class="mx-auto grid max-w-3xl grid-cols-1 gap-x-10 gap-y-16 sm:gap-y-20 lg:mx-0 lg:max-w-none lg:grid-cols-3">
            <div>
                <h2 class="text-base font-semibold leading-7 text-indigo-600">Credential verified:</h2>
                <p class="mt-2 text-3xl font-bold tracking-tight text-gray-900 sm:text-4xl">{{ credential.credential }}</p>

                <!--                <code class="mt-6 text-sm leading-7 text-gray-600">
                                    <pre>{{ credential.credential }}</pre>
                                </code>-->

                <!--                <p class="mt-6 text-base leading-7 text-gray-600">Lorem ipsum, dolor sit amet consectetur adipisicing elit. Maiores-->
                <!--                    impedit perferendis suscipit eaque, iste dolor cupiditate blanditiis ratione.</p>-->
            </div>
            <dl class="col-span-2 grid grid-cols-1 gap-x-8 gap-y-10 text-base leading-7 text-gray-600 sm:grid-cols-2 lg:gap-y-16">
                <div v-for="res in credential.policies" :key="res.policy" class="relative pl-9 bg-gray-50 border rounded-md pb-3">
                    <dt class="font-semibold text-gray-900 pt-1">
                        <Icon v-if="res.is_success" aria-hidden="true" class="absolute left-2 top-2 h-5 w-5 text-green-500"
                              name="heroicons:check"
                        />
                        <Icon v-else aria-hidden="true" class="absolute left-2 top-2 h-5 w-5 text-red-500" name="heroicons:x-mark" />
                        {{ res.policy }}
                    </dt>
                    <dd class="mt-2">{{ res.description }}</dd>
                    <code class="text-sm text-gray-600">
                        <pre class="overflow-x-auto">{{ res.result ?? res.error }}</pre>
                    </code>
                </div>
                <div v-if="credential.policies.length == 0">(No policies)</div>
            </dl>
        </div>
    </div>
</template>

<script lang="ts" setup>
const props = defineProps({
    credential: Object
});
</script>

<style scoped>
pre {
    white-space: pre-wrap;
}
</style>
