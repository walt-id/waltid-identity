<template>

    <!--        <div class="relative mt-2 rounded-md shadow-sm">
               <div class="absolute inset-y-0 left-0 flex items-center rounded-l-md pl-3 border-r"
                    :class="[unsafePrefixes.includes(prefix) ? 'bg-yellow-400 text-black' : 'bg-green-600 text-white']"
               >
                   <Icon v-if="unsafePrefixes.includes(prefix)" class="text-black h-5 w-5 animate-pulse"
                         name="heroicons:exclamation-triangle"
                   />
                   <Icon v-else class="text-white h-5 w-5" name="heroicons:lock-closed" />
                   <select :value="prefix" v-model="prefix" class="h-full -ml-2 bg-transparent border-0 py-0 pr-7 focus:ring-none sm:text-sm">
                       <option v-for="currentPrefix of allowedPrefixes" :value="currentPrefix">{{ currentPrefix }}</option>
                   </select>
               </div>
               <input class="block w-full rounded-md border-0 py-1.5 pl-29 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6" name="phone-number"
                      placeholder="www.example.org/validate"
                      type="text"
               />
           </div>-->

    <div>
        <div class="mt-2 flex rounded-md shadow-sm">
            <span :class="[unsafePrefixes.includes(prefix) ? 'bg-yellow-400 text-black' : 'bg-green-600 text-white']"
                  class="flex items-center rounded-l-md px-3 sm:text-sm"
            >
                <Icon v-if="unsafePrefixes.includes(prefix)" class="mr-1 text-black h-5 w-5 animate-pulse"
                      name="heroicons:exclamation-triangle"
                />
                <Icon v-else class="mr-1 text-white h-4 w-4" name="heroicons:lock-closed" />
                {{ prefix }}
            </span>


            <div class="relative flex items-center grow">
                <input v-model="internalUrlPart"
                       class="block w-full min-w-0 pr-14 grow flex-1 rounded-none rounded-r-md border-0 py-1.5 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                       placeholder="www.example.org/validate"
                       type="text"
                       @input="handleInput"
                />

                <NuxtLink v-if="model" :to="model" class="absolute inset-y-0 right-0 py-1.5 pr-1.5 flex items-center bg-transparent group"
                          external
                          target="_blank"
                >
                    <Icon class="w-full h-full rounded border border-gray-200 px-1 font-sans text-gray-400 group-hover:bg-gray-100"
                          name="tabler:external-link"
                    />
                </NuxtLink>
            </div>
        </div>
    </div>
</template>

<script lang="ts" setup>
// noinspection HttpUrlsUsage
import type { ModelRef, Ref } from "vue";

const allowedPrefixes: string[] = ["https://", "http://"];
const unsafePrefixes: string[] = [allowedPrefixes[1]];

const prefix: Ref<string> = ref(allowedPrefixes[0]);

const model: ModelRef<string | null | undefined> = defineModel();

const internalUrlPart: Ref<string | null> = ref(null);

function removeAndUpdateSuffixes(url: string): { urlWithoutPrefix: string, prefix: string | null } {
    for (const checkPrefix of allowedPrefixes) {
        if (url.startsWith(checkPrefix)) {
            return { urlWithoutPrefix: url.substring(checkPrefix.length), prefix: checkPrefix };
        }
    }
    return { urlWithoutPrefix: url, prefix: null };
}

function handleUpdate(newUrl: string | null | undefined) {
    if (newUrl) {
        const { urlWithoutPrefix, prefix: newPrefix } = removeAndUpdateSuffixes(newUrl);

        if (newPrefix != null) {
            if (internalUrlPart.value != urlWithoutPrefix || prefix.value != newPrefix) {
                internalUrlPart.value = urlWithoutPrefix;
                prefix.value = newPrefix ?? allowedPrefixes[0];
                model.value = `${newPrefix}${urlWithoutPrefix}`;
            }
        } else {
            internalUrlPart.value = urlWithoutPrefix;
            model.value = `${prefix.value}${urlWithoutPrefix}`;
        }

    } else {
        if (internalUrlPart.value != null) {
            internalUrlPart.value = null;
            prefix.value = allowedPrefixes[0];
            model.value = null;
        }
    }
}

function handleInput() {
    let newUrl = internalUrlPart.value;
    handleUpdate(newUrl);
}

watch((model), (value) => {
    handleUpdate(value);
});

handleUpdate(model.value)
</script>
