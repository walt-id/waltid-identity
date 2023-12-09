<template>
    <div class="bg-white p-6 rounded-2xl shadow-2xl h-full">
        <div class="flex justify-end gap-1.5">
            <CredentialIcon :credentialType="credential.type.at(-1)" class="h-6 w-6 flex-none rounded-full bg-gray-50 justify-self-start"/>
            <div
                :class="credential.expirationDate
                    ? (new Date(credential.expirationDate).getTime() > new Date().getTime()
                    ? 'bg-cyan-100' : 'bg-red-50') : 'bg-cyan-50'
                "
                class="rounded-lg px-3 mb-2"
            >
                <span
                    :class="
                        credential.expirationDate
                            ? new Date(credential.expirationDate).getTime() > new Date().getTime()
                                ? 'text-cyan-900'
                                : 'text-orange-900'
                            : 'text-cyan-900'
                    "
                >{{ credential.expirationDate ?
                    (new Date(credential.expirationDate).getTime() > new Date().getTime() ? "Valid" : "Expired")
                    : "Valid" }}
                </span>
            </div>

            <div class="rounded-lg px-3 mb-2 bg-cyan-100" v-if="credential._sd">
                <span>SD</span>
            </div>
        </div>

        <h2 class="text-2xl font-bold text-gray-900 bold mb-8">
            {{ credential.type.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2") }}
            <p v-if="credential?.name" class="text-lg">{{ credential.name }}</p>
        </h2>

        <div v-if="credential.issuer" class="flex items-center">
            <img :src="credential.issuer?.image?.id ? credential.issuer?.image?.id : credential.issuer?.image" class="w-12" />
            <div class="text-natural-600 ml-2 w-32">
                {{ credential.issuer?.name }}
            </div>
        </div>

        <div class="font-mono mt-3" v-if="showId">ID: {{ credential.id }}</div>
    </div>
</template>

<script lang="ts" setup>
const props = defineProps({
    credential: {
        type: undefined
    },
    showId: {
        type: Boolean,
        required: false,
        default: false
    }
});
</script>

<style scoped>

</style>
