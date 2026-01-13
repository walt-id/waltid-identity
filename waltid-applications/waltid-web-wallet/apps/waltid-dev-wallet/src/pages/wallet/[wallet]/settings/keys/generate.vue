<template>
    <CenterMain>
        <div class="mt-1 border p-4 rounded-2xl">
            <p class="text-base font-semibold">Generate key</p>
            <div>
                <div
                    class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                    <div>
                        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="name">
                                Name (optional)
                            </label>
                            <div class="mt-2 sm:col-span-2 sm:mt-0">
                                <input id="name" v-model="data.name"
                                       class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                       type="text" placeholder="e.g., My signing key"/>
                            </div>
                        </div>

                        <!-- Key Generation Request -->
                        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5"
                                   for="keyGenerationRequest">
                                KMS
                            </label>
                            <div class="mt-2 sm:col-span-2 sm:mt-0">
                                <select id="keyGenerationRequest" v-model="data.keyGenerationRequest.type"
                                        @change="data.keyGenerationRequest.config = {}"
                                        class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6">
                                    <option v-for="option in options" :key="option.keyGenerationRequest[1]"
                                            :value="option.keyGenerationRequest[1]">
                                        {{ option.keyGenerationRequest[0] }}
                                    </option>
                                </select>
                            </div>
                        </div>

                        <!-- Key Type -->
                        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-4">
                            <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5" for="keyType">
                                Key Type
                            </label>
                            <div class="mt-2 sm:col-span-2 sm:mt-0">
                                <select id="keyType" v-model="data.type"
                                        class="block px-2 w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:max-w-xs sm:text-sm sm:leading-6">
                                    <option v-for="keyType in options.find(
                    (option) =>
                      option.keyGenerationRequest[1] ==
                      data.keyGenerationRequest.type
                  )?.keyType" :key="keyType[1]" :value="keyType[1]">
                                        {{ keyType[0] }}
                                    </option>
                                </select>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Config Fields -->
                <div v-if="options.find(
          (option) =>
            option.keyGenerationRequest[1] ==
            data.keyGenerationRequest.type
        )?.config?.length"
                     class="mt-1 space-y-8 border-gray-900/10 pb-12 sm:space-y-0 sm:divide-gray-900/10 sm:border-t sm:pb-0">
                    <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-2" v-for="config in options.find(
            (option) =>
              option.keyGenerationRequest[1] ==
              data.keyGenerationRequest.type
          )?.config" :key="config">
                        <label class="block text-sm font-medium leading-6 text-gray-900 sm:pt-1.5">
                            {{ config.charAt(0).toUpperCase() + config.slice(1) }}
                        </label>
                        <textarea v-if="config.includes('signingKeyPem')"
                                  v-model="data.keyGenerationRequest.config[config]"
                                  class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                                  rows="4"></textarea>
                        <input v-else v-model="data.keyGenerationRequest.config[config]"
                               class="px-2 block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                               type="text"/>
                    </div>
                </div>
            </div>
        </div>

        <!-- Submit Button -->
        <div class="mt-2 flex items-center justify-end gap-x-6">
            <button
                class="inline-flex justify-center bg-blue-500 hover:bg-blue-600 focus-visible:outline-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2"
                @click="generateKey">
        <span class="inline-flex place-items-center gap-1">
          <KeyIcon v-if="!loading" class="w-5 h-5 mr-1" />
          <InlineLoadingCircle v-else class="mr-1" />
          Generate key
        </span>
            </button>
        </div>

        <!-- Response Section -->
        <div v-if="response && response !== ''" class="mt-6 border p-4 rounded-2xl">
            <p class="text-base font-semibold">Response</p>
            <div

                class="mt-1 space-y-6 border-gray-900/10 pb-6 sm:space-y-0 sm:divide-y sm:divide-gray-900/10 sm:border-t sm:pb-0">
                <p class="mt-2 flex items-center bg-green-100 p-3 rounded-xl overflow-x-scroll">
                    <CheckIcon class="w-5 h-5 mr-1 text-green-600"/>
                    <span class="text-green-800">Generated key: <code>{{ response }}</code></span>
                </p>
                <div class="pt-3 flex justify-end">
                    <NuxtLink :to="`/wallet/${currentWallet}/settings/keys`">
                        <button
                            class="mb-2 border rounded-xl p-2 bg-blue-500 text-white flex flex-row justify-center items-center">
                            <ArrowUturnLeftIcon class="h-5 pr-1"/>
                            Return back
                        </button>
                    </NuxtLink>
                </div>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {ArrowUturnLeftIcon, CheckIcon, KeyIcon} from "@heroicons/vue/24/outline";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import InlineLoadingCircle from "@waltid-web-wallet/components/loading/InlineLoadingCircle.vue";

const loading = ref(false);
const response = ref("");

const options = ref([
    {
        keyGenerationRequest: ["JWK", "jwk"],
        keyType: [
            ["EdDSA_Ed25519", "Ed25519"],
            ["ECDSA_Secp256k1", "secp256k1"],
            ["ECDSA_Secp256r1", "secp256r1"],
            ["RSA", "RSA"],
        ],
        config: [],
    },
    {
        keyGenerationRequest: ["TSE", "tse"],
        keyType: [
            ["EdDSA_Ed25519", "Ed25519"],
            ["RSA", "RSA"],
        ],
        config: ["server", "accessKey"],
    },
    {
        keyGenerationRequest: ["OCI REST API", "oci-rest-api"],
        keyType: [
            ["ECDSA_Secp256r1", "secp256r1"],
            ["RSA", "RSA"],
        ],
        config: [
            "tenancyOcid",
            "userOcid",
            "fingerprint",
            "cryptoEndpoint",
            "managementEndpoint",
            "signingKeyPem",
        ],
    },
    {
        keyGenerationRequest: ["OCI", "oci"],
        keyType: [["ECDSA_Secp256r1", "secp256r1"]],
        config: ["vaultId", "compartmentId"],
    },
    {
        keyGenerationRequest: ["AWS with AccessKey Auth", "aws-access-key"],
        keyType: [
            ["ECDSA_Secp256r1", "secp256r1"],
            ["ECDSA_Secp256k1", "secp256k1"],
            ["RSA", "RSA"]
        ],
        config: ["accessKeyId", "secretAccessKey", "region"]
    },
    {
        keyGenerationRequest: ["AWS with RoleName Auth", "aws-role-name"],
        keyType: [
            ["ECDSA_Secp256r1", "secp256r1"],
            ["ECDSA_Secp256k1", "secp256k1"],
            ["RSA", "RSA"]
        ],
        config: ["roleName", "region"]
    },
    {
      keyGenerationRequest: ["Azure with Client access key", "azure-rest-api"],
        keyType: [
            ["ECDSA_Secp256r1", "secp256r1"],
            ["ECDSA_Secp256k1", "secp256k1"],
            ["RSA", "RSA"]
        ],
        config: ["clientId", "clientSecret", "tenantId", "keyVaultUrl"]
    },
  {
    keyGenerationRequest: ["Azure Key Vault and Managed Identity", "azure"],
    keyType: [
      ["ECDSA_Secp256r1", "secp256r1"],
      ["ECDSA_Secp256k1", "secp256k1"],
      ["RSA", "RSA"]
    ],
    config: ["keyVaultUrl"]
  },
]);

const data = reactive<{
    name?: string;
    keyGenerationRequest: { type: string; config: Record<string, string> };
    type: string;
}>({
    name: '',
    keyGenerationRequest: {
        type: options.value[0].keyGenerationRequest[1],
        config: {},
    },
    type: options.value[0].keyType[0][1],
});

const currentWallet = useCurrentWallet();

async function generateKey() {
    const keyGenerationRequest = data.keyGenerationRequest;
    const type = keyGenerationRequest?.type;
    const config = keyGenerationRequest?.config;

    // Build the body based on the key generation type and config
    const body: any = {
        backend: type?.includes("aws") ? "aws" : type,
        keyType: data.type,
        config: {},
    };
    if (data.name && data.name.trim() !== '') {
        body.name = data.name.trim();
    }

    // Configure the 'config' object depending on the type (AWS, Azure, etc.)
    if (type === "aws-access-key") {
        body.config.auth = {
            accessKeyId: config?.accessKeyId,
            secretAccessKey: config?.secretAccessKey,
            region: config?.region,
        };
    } else if (type === "aws-role-name") {
        body.config.auth = {
            roleName: config?.roleName,
            region: config?.region,
        };
    } else if (type === "azure-rest-api") {
        body.config.auth = {
            clientId: config?.clientId,
            clientSecret: config?.clientSecret,
            tenantId: config?.tenantId,
            keyVaultUrl: config?.keyVaultUrl,
        };
    } else if (type === "azure") {
      body.config.auth = {
        keyVaultUrl: config?.keyVaultUrl,
      };
    } else {
        // For other types, just include the config directly
        body.config = {...config};
    }

    loading.value = true;

    try {
        response.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys/generate`, {
            method: "POST",
            body, // fetch automatically handles JSON conversion
            headers: {
                "Content-Type": "application/json",
            },
        });
    } catch (e: any) {
        console.error("Error generating key:", e);
        alert("Failed to generate key: " + (e.message || e));
    } finally {
        loading.value = false;
    }
}


useHead({
    title: "Generate key - walt.id",
});
</script>

<style scoped></style>
