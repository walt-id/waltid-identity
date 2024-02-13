<template>
    <div class="flex items-center rounded-md border gap-1 relative">
        <span class="h-8 inline-flex border items-center rounded-l-md  text-gray-600 sm:text-sm gap-1 px-2">
            <span>did</span>
            <span class="font-semibold">:</span>
            <span>{{ didMethod ?? defaultMethod }}</span>
            <span class="font-semibold">:</span>
        </span>
        <input
            v-model="internalDidPart"
            class="border-gray-300 h-8 block w-full min-w-0 flex-1 rounded-none pl-1.5 pr-2 rounded-r-md border-0 py-1.5 text-gray-900 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
            placeholder="walt.id"
            type="text"
            @input="handleInput"
        />

        <div v-if="isClipboardSupported && model" class="absolute inset-y-0 right-0 py-1.5 pr-1.5 flex items-center bg-transparent group"
             @click="copy(modelValue)"
        >
            <Icon v-if="copied" class="w-full h-full rounded border border-gray-200 px-1 font-sans text-gray-400 group-hover:bg-gray-100"
                  name="heroicons:check-circle"
            />
            <Icon v-else class="w-full h-full rounded border border-gray-200 px-1 font-sans text-gray-400 group-hover:bg-gray-100"
                  name="radix-icons:clipboard-copy"
            />
        </div>
    </div>
</template>

<script lang="ts" setup>
import type { ModelRef, Ref } from "vue";

const { copy, copied, isSupported: isClipboardSupported } = useClipboard();

const defaultMethod = "web";

const model: ModelRef<string | null | undefined> = defineModel();

const internalDidPart: Ref<string | null> = ref(null);
const didMethod: Ref<string | null> = ref(defaultMethod);

function parseDidString(newDid: string | null | undefined): { method: string, did: string } | null {
    // console.log(`handling: ${newDid}`)
    if (newDid && newDid.startsWith("did:")) {
        const splittedParts = newDid!!.trim().split(":");

        if (splittedParts.length > 2) {
            // console.log(`parsed method: ${splittedParts[1]}`)
            return { method: splittedParts[1], did: splittedParts.splice(2).join(":") };
        } else {
            return null;
        }
    } else {
        return null;
    }
}

function removeDid() {
    if (internalDidPart.value != null) {
        didMethod.value = defaultMethod;
        internalDidPart.value = null;
        model.value = null;
    }
}

function handleUpdate(newDid: string | null | undefined) {
    if (newDid) {
        const parsed = parseDidString(newDid)
        if (!parsed) {
            internalDidPart.value = newDid
            model.value = `did:${didMethod.value}:${internalDidPart.value}`
        } else {
            if (parsed.did.trim() == "") {
                internalDidPart.value = null
                didMethod.value = parsed.method
                model.value = null
            } else if (internalDidPart.value != parsed.did || didMethod.value != parsed.method) {
                internalDidPart.value = parsed.did
                didMethod.value = parsed.method
                model.value = `did:${parsed.method}:${parsed.did}`
            }
        }
    } else removeDid();
}

function handleInput() {
    handleUpdate(internalDidPart.value)
}

watch((model), (value) => {
    handleUpdate(value)
});

handleUpdate(model.value)
</script>
