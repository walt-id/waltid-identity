<template>
    <div class="px-2">
        <ul class="divide-y divide-gray-200 list-disc" role="list">
            <li v-for="policy in policies" :key="policy.name">
                <div class="py-5">
                    <div>
                        <p class="text-lg font-semibold leading-6 text-gray-900 flex gap-2 w-full justify-between">
                            {{ policy.name }}
                            <span
                                class="inline-flex items-center rounded-md bg-gray-50 px-2 py-1 text-xs font-medium text-gray-600 ring-1 ring-inset ring-gray-500/10"
                                :class="[policyTypeColors[policy.policyType] ?? 'bg-red-400']"
                            >{{ policy.policyType }}</span>
                        </p>
                    </div>
                    <div class="flex">
                        <div class="w-3/4">
                            <div class="flex items-baseline justify-between gap-x-4">

                            </div>
                            <p class="mt-1 line-clamp-2 text leading-6 text-gray-600">{{ policy.description }}</p>
                            <div>
                                <div v-if="hasArguments(policy.argumentType)" class="mt-1 line-clamp-2 text leading-6 text-gray-600 flex items-center gap-1.5">
                                    <Icon name="tabler:braces" class="text-gray-500"/>
                                    <ul>
                                        <li v-for="argType of policy.argumentType">{{ argType }} argument</li>
                                    </ul>
                                </div>
                                <div v-else class="mt-1 line-clamp-2 text leading-6 text-gray-600 flex items-center gap-1.5">
                                    <Icon name="tabler:braces-off" class="text-gray-500"/>
                                    No arguments required.
                                </div>
                            </div>
                        </div>
                        <div class="w-1/4 flex justify-end items-center">
                            <button
                                class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                                type="button"
                                @click="addPolicy(policy)"
                            >
                                <span v-if="addedPolicyNames.includes(policy.name)" class="flex items-center gap-1">
                                    <Icon name="material-symbols:check" class="text-green-600"/>
                                    Added!
                                </span>
                                <span v-else>Add to selection</span>

                            </button>
                        </div>
                    </div>
                </div>
            </li>
        </ul>
    </div>
</template>

<script lang="ts" setup>

import { hasArguments, type VerificationPolicyInformation } from "~/composables/verification";

const props = defineProps<{
    policies: VerificationPolicyInformation[]
}>();

const emit = defineEmits<{
    (e: 'added-policy', policy: VerificationPolicyInformation): void
}>()

const addedPolicyNames: string[] = reactive([])

function addPolicy(policy: VerificationPolicyInformation) {
    addedPolicyNames.push(policy.name)
    emit("added-policy", policy)
}

const policyTypeColors = {
    "credential-wrapper-validator": "bg-violet-500 text-white",
    "jwt-verifier": "bg-indigo-600 text-white",
    "credential-data-validator": "bg-sky-400 text-white",
}
</script>

<style scoped>

</style>
