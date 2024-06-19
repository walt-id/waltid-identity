<template>
    <CenterMain>
        <h1 class="text-lg font-semibold text-center">{{ issuer }}</h1>
        <p class="text-center">Select credential to request from issuer.</p>
        <div class="mt-8">
            <ol>
                <li v-for="credential in issuerCredentials?.credentials.filter(c => c.format == credentialType)"
                    :key="credential" class="flex items-center justify-between py-5 rounded-lg shadow-md mt-4 mb-10">
                    <NuxtLink
                        :to="issuerCredentials?.issuer.uiEndpoint + credential.id.split('_')[0] + '&callback=' + config.public.issuerCallbackUrl"
                        class="w-full">
                        <div class="flex items-start gap-x-3">
                            <p class="mx-2 text-base font-semibold leading-6 text-gray-900">
                                {{ credential.id.split('_')[0] }}
                            </p>
                        </div>
                    </NuxtLink>
                </li>
            </ol>
            <p v-if="credentials && issuerCredentials?.credentials.filter(c => c.format == credentialType).length == 0"
                class="text-lg font-semibold text-center">
                No credentials</p>
            <p v-if="error">
                Error while trying to use issuer <code>{{ issuer }}</code>:
                <span v-if="error.data" class="text-gray-600">{{ error.data.startsWith("{") ?
                    JSON.parse(error.data)?.message : error.data }}</span>
                <span v-else>{{ error }}</span>
            </p>

            <div v-if="pending" class="flex justify-center">
                <LoadingIndicator>Loading issuer configuration...</LoadingIndicator>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";

const config = useRuntimeConfig();
const route = useRoute();

const issuer = route.params.issuer;

const currentWallet = useCurrentWallet();

const { pending, data: issuerCredentials, error, refresh } = useLazyFetch(`/wallet-api/wallet/${currentWallet.value}/issuers/${issuer}/credentials`);
const credentialType = ref<string>('jwt_vc_json');

definePageMeta({
    layout: 'mobile'
});
useHead({
    title: `${issuer} - supported credentials`,
});
</script>

<style scoped></style>
