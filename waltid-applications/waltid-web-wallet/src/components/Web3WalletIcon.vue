<template>
    <!-- <div class="inline-flex justify-center px-3 py-2"> -->
    <div>
        <img v-if="ecosystem === 'ethereum'" alt="ethereum" aria-hidden="true" class="h-5 w-full" fill="currentColor" src="/svg/ethereum.svg" />
        <img v-if="ecosystem === 'tezos'" alt="tezoz" aria-hidden="true" class="h-5 w-full" fill="currentColor" src="/svg/tezos.svg" />
        <img v-if="ecosystem === 'near'" alt="near" aria-hidden="true" class="h-5 w-full" fill="currentColor" src="/svg/near.svg" />
        <img v-if="ecosystem === 'flow'" alt="flow" aria-hidden="true" class="h-5 w-full" fill="currentColor" src="/svg/flow.svg" />
        <img v-if="ecosystem === 'algorand'" alt="algorand" aria-hidden="true" class="h-5 w-full" fill="currentColor" src="/svg/algorand-algo-logo.svg" />
    </div>
</template>

<script setup>
const props = defineProps(["ecosystem"]);
</script>

<style scoped></style>
