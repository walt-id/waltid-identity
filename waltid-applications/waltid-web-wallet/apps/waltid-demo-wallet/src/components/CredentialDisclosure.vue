<template>
    <div v-if="credential.disclosures" class="w-full" :class="{ 'text-[#7B8794]': !selection[credential.id] }">
        <div class="sm:flex justify-between items-center">
            <div @click="toggleDisclosure(credential.id)"
                :class="{ 'font-semibold': disclosureModalState[credential.id] && selection[credential.id], 'text-black-800': selection[credential.id] }"
                class="flex gap-3 items-center cursor-pointer">
                {{ displayType }}
                <svg v-if="disclosureModalState[credential.id]" width="16" height="16" viewBox="0 0 17 10" fill="none"
                    xmlns="http://www.w3.org/2000/svg">
                    <path d="M16 1L8.5 8.5L1 1" stroke="black" stroke-width="1.5" stroke-linecap="round"
                        stroke-linejoin="round" />
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 10 18" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M1.25 1.5L8.75 9L1.25 16.5" stroke="#323F4B" stroke-width="1.5" stroke-linecap="round"
                        stroke-linejoin="round" />
                </svg>
            </div>
            <div>
                <div v-if="disclosureModalState[credential.id]" class="flex items-center gap-2">
                    Disclose All
                    <input type="checkbox" :disabled="!selection[credential.id]"
                        class="h-4 w-4 rounded border-gray-300 text-primary-400 focus:ring-primary-500"
                        @click="disclosures[credential.id] = ($event.target as HTMLInputElement)?.checked ? disclosureList : []"
                        :checked="disclosures[credential.id]?.length === disclosureList?.length" />
                </div>
                <div v-else>
                    {{
                        disclosures[credential.id] === undefined ? `Disclosing 0 of ${disclosureList.length} attributes`
                            : disclosures[credential.id]?.length === disclosureList.length ? "Disclosing all attributes"
                                : `Disclosing ${disclosures[credential.id].length} of ${disclosureList.length} attributes`
                    }}
                </div>
            </div>
        </div>
        <div v-if="disclosureModalState[credential.id]">
            <div class="flex items-center mt-1">
                <div class="flex-1 border-t border-gray-300"></div>
                <div class="mx-3 text-gray-400">Attributes to disclose</div>
                <div class="flex-1 border-t border-gray-300"></div>
            </div>
            <div class="mt-1 divide-y px-10 divide-gray-100">
                <div v-for="(disclosure, disclosureIdx) in disclosureList" :key="disclosureIdx"
                    class="relative flex items-start py-1">
                    <div class="min-w-0 flex-1 text-sm leading-6">
                        <label :for="`disclosure-${credential.id}-${disclosure[0]}`">
                            <span :class="{ 'text-black-800': selection[credential.id] }">
                                {{
                                    disclosure[1].charAt(0).toUpperCase() +
                                    disclosure[1].slice(1)
                                }}
                            </span>
                        </label>
                    </div>
                    <div class="ml-3 flex h-6 items-center">
                        <input :id="`disclosure-${credential.id}-${disclosure[0]}`"
                            :name="`disclosure-${disclosure[0]}`"
                            class="h-4 w-4 rounded border-gray-300 text-primary-400 focus:ring-primary-500"
                            type="checkbox" :disabled="!selection[credential.id]"
                            :checked="disclosures[credential.id] && disclosures[credential.id].find((elem: Array<string>) => elem[0] === disclosure[0])"
                            @click="($event.target as HTMLInputElement)?.checked ? addDisclosure(credential.id, disclosure) : removeDisclosure(credential.id, disclosure)" />
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div :class="{ 'text-black-800': selection[credential.id], 'text-[#7B8794]': !selection[credential.id] }" v-else>
        {{ displayType }}
    </div>
    <hr class="my-2 border-gray-200" />
</template>

<script setup lang="ts">
import { parseJwt } from "@waltid-web-wallet/utils/jwt.ts";
import { parseDisclosures } from "@waltid-web-wallet/composables/disclosures.ts";

const props = defineProps<{
    credential: {
        id: string;
        document: string;
        parsedDocument?: string;
        disclosures?: string;
    };
    disclosures: Record<string, Array<Array<string>>>;
    selection: Record<string, boolean>;
    disclosureModalState: Record<string, boolean>;
    toggleDisclosure: (credentialId: string) => void;
    addDisclosure: (credentialId: string, disclosure: string) => void;
    removeDisclosure: (credentialId: string, disclosure: string) => void;
}>();

const type = computed(() => {
    const parsed = props.credential.parsedDocument ?? parseJwt(props.credential.document).vc ?? parseJwt(props.credential.document);
    return parsed?.type?.at(-1) ?? parsed.vct.split('/').pop() ?? "Unknown";
});
const displayType = computed(() => type.value.replace(/([a-z0-9])([A-Z])/g, "$1 $2"));
const disclosureList = computed(() => parseDisclosures(props.credential.disclosures || ""));
</script>