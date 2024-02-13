<template>
    <div>
        <p class="font-semibold">{{ props.name }}</p>
        <p class="text-gray-600">{{ props.information.description }}</p>
        <p>Type: {{ props.information.policyType }}</p>

        <ul v-if="computedHasArguments" class="list-decimal">
            <li v-for="arg of props.information.argumentType">
                <p class="text-red">{{ arg }}:</p>
                <div v-if="arg == 'JSON'" class="h-12">
                    <LazyInputsJsonInput v-if="false"></LazyInputsJsonInput>
                </div>
                <div v-else-if="arg == 'URL'">
                    <UrlInput v-model="urlExample"/>
                    URL: {{ urlExample }}
                </div>
                <div v-else-if="arg == 'NUMBER'">
                    <NumberInput v-model="numberExample"/>
                    Number: {{ numberExample }}
                </div>
                <div v-else-if="arg == 'DID'">
                    <DidInput v-model="didExample"/>
                    DID: {{ didExample }}
                    <button @click="didExample = `did:key:${new Date()}`">DATE</button>
                    <button @click="didExample = null">NULL</button>
                </div>
                <div v-else-if="arg == 'DID_ARRAY'">
                    <DidArrayInput v-model="didArray" />
                {{ didArray }}

                    <button @click="didArray.push('did:key:123')">AAA</button>
                    <button @click="didArray.pop()">BBB</button>
                </div>
                <div v-else>
                    Unknown: {{ arg }}
                </div>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import { hasArguments, type VerificationPolicyInformation } from "~/composables/verification";
import UrlInput from "~/components/inputs/UrlInput.vue";
import NumberInput from "~/components/inputs/NumberInput.vue";
import JsonInput from "~/components/inputs/JsonInput.vue";
import DidInput from "~/components/inputs/DidInput.vue";
import DidArrayInput from "~/components/inputs/DidArrayInput.vue";

const urlExample = ref()
const numberExample = ref()
const didExample = ref()
const didArray = ref([])

const props = defineProps<{
    name: string,
    information: VerificationPolicyInformation
}>()

const computedHasArguments = computed(() => {
    return hasArguments(props.information.argumentType)
})

</script>
