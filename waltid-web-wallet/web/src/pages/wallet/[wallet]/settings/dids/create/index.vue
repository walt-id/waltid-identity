<template>
    <CenterMain>
        <ol class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-3" role="list">
            <li v-for="method in methods" :key="method[0]" class="flex items-center justify-between gap-x-6 py-4">
                <div class="min-w-0">
                    <div class="flex items-start gap-x-3">
                        <p class="text-base font-semibold leading-6 text-gray-900">
                            {{ method[0].toUpperCase() }} (did:{{ method[0] }}):
                            <span class="text-base font-normal"> {{ method[1].length == 0 ? "no parameters needed" : "Define (" + method[1].length + "): " + method[1].join(", ") }}</span>
                        </p>
                    </div>
                </div>
                <div class="flex flex-none items-center gap-x-4">
                    <NuxtLink
                        :to="'create/' + method[0]"
                        class="w-32 text-center rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                    >
                        Create did:{{ method[0] }}
                    </NuxtLink>
                </div>
            </li>
        </ol>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";

const methods = new Map([
    ["key", ["useJwkJcsPub"]],
    ["jwk", []],
    ["web", ["domain", "path"]],
    ["cheqd", ["network"]],
    // ["ebsi", ["bearer token", "version"]],
    // ["iota", []]
]);

useHead({
    title: "Create DID - walt.id",
});
</script>

<style scoped></style>
