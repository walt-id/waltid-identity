<template>
    <CenterMain>
        <div v-if="pending" class="flex justify-center items-center h-screen">
            <LoadingIndicator>Loading credential...</LoadingIndicator>
        </div>
        <div v-else>
            <div class="flex justify-between items-center">
                <div class="cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
                    @click="navigateTo({ path: `/wallet/${walletId}` })">X</div>
                <div class="cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
                    @click="deleteCredential">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor"
                        class="bi bi-trash" viewBox="0 0 16 16">
                        <path
                            d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0z" />
                        <path
                            d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4zM2.5 3h11V2h-11z" />
                    </svg>
                </div>
            </div>
            <div class="my-10">
                <VerifiableCredentialCard :credential="credential" :isDetailView="true" />
            </div>
            <div class="px-4 py-6 shadow-sm bg-white rounded-xl" v-if="credentialManifest">
                <div class="text-gray-600 font-bold mb-4">Credential Details</div>
                <div v-for="(value, key, index) in credentialManifest.claims" :key="key">
                    <div class="text-gray-500">{{ key }}</div>
                    <div class="text-black">{{ value }}</div>
                    <hr v-if="index !== Object.keys(credentialManifest.claims).length - 1"
                        class="w-full border-gray-200 my-2" />
                </div>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import { parseDisclosures } from "~/composables/disclosures";
import { decodeBase64ToUtf8 } from "~/composables/base64";
import CenterMain from "~/components/CenterMain.vue";
import { JSONPath } from "jsonpath-plus";
import { ref } from "vue";

const route = useRoute();
const runtimeConfig = useRuntimeConfig()

const walletId = route.params.wallet as string;
const credentialId = route.params.credentialId as string;
const currentWallet = useCurrentWallet();

const showCredentialJson = ref(false);
const showCredentialManifest = ref(false);

const { data: credential, pending, refresh, error } = await useFetch<WalletCredential>(`/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`);
const jwtJson = computed(() => {
    if (credential.value) {
        const vcData = credential.value.document.split(".")[1];
        console.log("Credential is: ", vcData);

        const vcBase64 = vcData.replaceAll("-", "+").replaceAll("_", "/");
        console.log("Base64 from base64url: ", vcBase64);

        const decodedBase64 = decodeBase64ToUtf8(vcBase64).toString();
        console.log("Decoded: ", decodedBase64);

        let parsed
        try {
            parsed = JSON.parse(decodedBase64);
        } catch (e) {
            console.log(e)
        }

        if (parsed?.vc) return parsed.vc;
        else return parsed;
    } else return null;
});
const { data: credentialManifest } = await useLazyFetch(`${runtimeConfig.public.credentialsRepositoryUrl}/api/manifest/${jwtJson.value?.type[jwtJson.value?.type.length - 1]}`, {
    transform: (data: { claims: { [key: string]: string; } }) => {
        return {
            ...data,
            claims: Object.fromEntries(Object.entries(data?.claims).map(([key, value]) => {
                return [key, JSONPath({ path: value, json: jwtJson.value })[0]];
            })),
        }
    },
});

const disclosures = computed(() => {
    if (credential.value && credential.value.disclosures) {
        return parseDisclosures(credential.value.disclosures);
    } else return null;
});

type WalletCredential = {
    wallet: string;
    id: string;
    document: string;
    disclosures: string | null;
    addedOn: string;
    manifest: string | null;
    parsedDocument: object | null;
};

const manifest = computed(() => (credential.value?.manifest && credential.value?.manifest != "{}" ? (typeof credential.value?.manifest === 'string' ? JSON.parse(credential.value?.manifest) : credential.value?.manifest) : null));
const manifestClaims = computed(() => manifest.value?.display?.claims);

const issuerName = ref(null);
const issuerDid = ref(null);
const credentialIssuerService = ref(null);

watchEffect(() => {
    issuerName.value = manifest.value?.display?.card?.issuedBy ?? jwtJson.value?.issuer?.name;
    issuerDid.value = manifest.value?.input?.issuer ?? jwtJson.value?.issuer?.id ?? jwtJson.value?.issuer;
    credentialIssuerService.value = manifest.value?.input?.credentialIssuer;
});

const issuanceDate = computed(() => {
    if (jwtJson?.issuanceDate) {
        return new Date(jwtJson?.issuanceDate).toISOString().slice(0, 10);
    } else if (jwtJson?.validFrom) {
        return new Date(jwtJson?.validFrom).toISOString().slice(0, 10);
    } else {
        return null;
    }
});

useHead({ title: "View credential - walt.id" });

async function deleteCredential() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`, {
        method: "DELETE",
    });
    await navigateTo({ path: `/wallet/${currentWallet.value}` });
}

definePageMeta({
    layout: false
});
</script>
