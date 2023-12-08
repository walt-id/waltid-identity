<template>
    <CenterMain>
        <LoadingIndicator>Loading presentation parameters...</LoadingIndicator>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import { encodeRequest, fixRequest } from "~/composables/siop-requests";

const currentWallet = useCurrentWallet()

if (process.client) {
    let request = encodeURI(decodeURI(fixRequest("openid://" + window.location.search)));
    console.log("Fixed request: ", request);

    const encoded = encodeRequest(request);
    console.log("Encoded request: ", encoded);

    navigateTo({ path: `/wallet/${currentWallet.value}/exchange/presentation`, query: { request: encoded } });
}
</script>

<style scoped></style>
