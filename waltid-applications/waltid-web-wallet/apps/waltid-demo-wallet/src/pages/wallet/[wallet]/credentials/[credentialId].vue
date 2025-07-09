<template>
  <CenterMain>
    <div v-if="pending" class="flex justify-center items-center h-screen">
      <LoadingIndicator>Loading credential...</LoadingIndicator>
    </div>
    <div v-else>
      <div class="flex justify-between items-center sm:hidden">
        <div
          class="cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
          @click="navigateTo({ path: `/wallet/${walletId}` })">
          X
        </div>
        <div
          class="cursor-pointer bg-gray-100 rounded-full w-8 h-8 flex items-center justify-center text-black text-xs font-bold"
          @click="deleteCredential">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-trash"
            viewBox="0 0 16 16">
            <path
              d="M5.5 5.5A.5.5 0 0 1 6 6v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m2.5 0a.5.5 0 0 1 .5.5v6a.5.5 0 0 1-1 0V6a.5.5 0 0 1 .5-.5m3 .5a.5.5 0 0 0-1 0v6a.5.5 0 0 0 1 0z" />
            <path
              d="M14.5 3a1 1 0 0 1-1 1H13v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4h-.5a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1H6a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1h3.5a1 1 0 0 1 1 1zM4.118 4 4 4.059V13a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V4.059L11.882 4zM2.5 3h11V2h-11z" />
          </svg>
        </div>
      </div>
      <div v-if="credential" class="my-10 sm:flex justify-center">
        <VerifiableCredentialCard :credential="credential" :isDetailView="true" />
      </div>

      <!-- Desktop view -->
      <div class="hidden sm:block px-4 py-6 bg-white rounded-xl">
        <hr v-if="credentialManifest" class="w-full border-gray-200 my-2" />
        <div v-if="credentialManifest" class="text-gray-500 font-bold mt-4 mb-2">
          Subject Info
        </div>
        <img v-if="credentialManifest?.image" :src="credentialManifest?.image" alt="User Avatar"
          class="h-28 mb-4 rounded-md" />
        <div v-for="(value, key, index) in credentialManifest?.claims" :key="key">
          <div class="flex mt-3">
            <div class="text-gray-500 w-sm">{{ key }}</div>
            <div class="text-gray-500 font-bold w-2xl">{{ value }}</div>
          </div>
        </div>

        <div v-if="credential?.format === 'mso_mdoc'">
          <hr class="w-full border-gray-200 my-2" />
          <div class="text-gray-500 font-bold mt-4 mb-8">Subject Info</div>
          <div v-for="elem in jwtJson?.issuerSigned?.nameSpaces[
            Object.keys(jwtJson?.issuerSigned?.nameSpaces)[0]
          ]">
            <div class="flex mt-3">
              <div class="text-gray-500 w-sm">{{ elem.elementIdentifier }}</div>
              <div class="text-gray-500 font-bold w-2xl">
                {{ elem.elementValue }}
              </div>
            </div>
          </div>
        </div>

        <hr class="w-full border-gray-200 mb-2 mt-8" v-if="issuerName || issuerDid" />
        <div class="text-gray-500 font-bold mt-4 mb-8" v-if="issuerName || issuerDid">
          Issuer
        </div>
        <div class="flex mt-2" v-if="issuerName">
          <div class="text-gray-500 w-sm">Name</div>
          <div class="text-gray-500 font-bold w-2xl">{{ issuerName }}</div>
        </div>
        <div class="flex mt-2 mb-8" v-if="issuerDid">
          <div class="text-gray-500 w-sm">DID</div>
          <div class="text-gray-500 font-bold w-2xl overflow-scroll">
            {{ issuerDid }}
          </div>
        </div>
        <hr v-if="disclosures" class="w-full border-gray-200 mb-2 mt-8" />
        <div v-if="disclosures" class="text-gray-500 font-bold mt-4 mb-8">
          Selectively disclosable attributes
        </div>
        <div v-if="disclosures">
          <div v-for="disclosure in disclosures">
            <div class="flex mt-2">
              <div class="text-gray-500 w-sm">{{ disclosure[1] }}</div>
              <div class="text-gray-500 font-bold overflow-scroll w-2xl">
                {{ disclosure[2] }}
              </div>
            </div>
          </div>
        </div>
        <hr class="w-full border-gray-200 my-2" />
        <div class="flex justify-between my-6">
          <div v-if="expirationDate" class="text-gray-500">
            Valid through {{ issuanceDate?.replace(/-/g, ".") }} -
            {{ expirationDate.replace(/-/g, ".") }}
          </div>
          <div v-else class="text-gray-500">No expiration date</div>
          <div class="text-gray-500" v-if="issuanceDate">
            Issued {{ issuanceDate?.replace(/-/g, ".") }}
          </div>
          <div class="text-gray-500" v-else>No issuance date</div>
        </div>
        <div class="flex justify-between my-16">
          <div class="text-blue-500 cursor-pointer underline" @click="showCredentialJson = !showCredentialJson">
            {{ showCredentialJson ? "Hide" : "Show" }} Credential JSON
          </div>
          <div class="text-red-500 cursor-pointer underline" @click="deleteCredential">
            Delete Credential
          </div>
        </div>
        <div v-if="showCredentialJson" class="bg-gray-100 p-4 rounded-xl overflow-auto">
          <pre class="text-xs">{{ jwtJson }}</pre>
        </div>
      </div>

      <!-- Mobile view -->
      <div class="px-4 py-6 shadow-sm bg-white rounded-xl sm:hidden"
        v-if="credentialManifest || credential?.format === 'mso_mdoc'">
        <div v-if="credentialManifest">
          <img v-if="credentialManifest?.image" :src="credentialManifest?.image" alt="User Avatar"
            class="h-28 mb-4 rounded-md" />
          <div class="text-gray-600 font-bold mb-4">Credential Details</div>
          <div v-for="(value, key, index) in credentialManifest.claims" :key="key">
            <div class="text-gray-500">{{ key }}</div>
            <div class="text-black">{{ value }}</div>
            <hr v-if="index !== Object.keys(credentialManifest.claims).length - 1"
              class="w-full border-gray-200 my-2" />
          </div>
        </div>

        <div v-if="credential?.format === 'mso_mdoc'">
          <div class="text-gray-600 font-bold mb-4">Credential Details</div>
          <div v-for="(elem, index) in jwtJson?.issuerSigned?.nameSpaces[
            Object.keys(jwtJson?.issuerSigned?.nameSpaces)[0]
          ]">
            <div class="text-gray-500">{{ elem.elementIdentifier }}</div>
            <div class="text-black">{{ elem.elementValue }}</div>
            <hr v-if="
              index !==
              jwtJson?.issuerSigned?.nameSpaces[
                Object.keys(jwtJson?.issuerSigned?.nameSpaces)[0]
              ].length -
              1
            " class="w-full border-gray-200 my-2" />
          </div>
        </div>
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import VerifiableCredentialCard from "@waltid-web-wallet/components/credentials/VerifiableCredentialCard.vue";
import { useCredential, type WalletCredential } from "@waltid-web-wallet/composables/credential.ts";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import { useCurrentWallet } from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import { JSONPath } from "jsonpath-plus";
import { ref } from "vue";

const route = useRoute();
const runtimeConfig = useRuntimeConfig();

const walletId = route.params.wallet as string;
const credentialId = route.params.credentialId as string;
const currentWallet = useCurrentWallet();

const showCredentialJson = ref(false);

const {
  data: credential,
  pending,
  refresh,
  error,
} = await useFetch<WalletCredential>(
  `/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`,
);
const {
  jwtJson,
  disclosures,
  issuerName,
  issuerDid,
  issuanceDate,
  expirationDate,
} = useCredential(credential);

const credentialManifest = computedAsync(async () => {
  if (jwtJson.value) {
    const { data } = await useFetch(
      `${runtimeConfig.public.credentialsRepositoryUrl}/api/manifest/${jwtJson.value?.type[jwtJson.value?.type.length - 1]}`,
      {
        transform: (data: { image?: string; claims: { [key: string]: string } }) => {
          let transformedManifest = JSON.parse(JSON.stringify(data));
          if (data.image) {
            transformedManifest = {
              ...transformedManifest,
              image: JSONPath({ path: data.image, json: jwtJson.value })[0] ??
                disclosures.value?.find(
                  (disclosure) => disclosure[1] === data.image.split(".").pop(),
                )?.[2],
            };
          }
          if (data.claims) {
            transformedManifest = {
              ...transformedManifest,
              claims: Object.fromEntries(
                Object.entries(data?.claims).map(([key, value]) => {
                  return [
                    key,
                    JSONPath({ path: value, json: jwtJson.value })[0] ??
                    disclosures.value?.find(
                      (disclosure) => disclosure[1] === value.split(".").pop(),
                    )?.[2],
                  ];
                }),
              ),
            };
          }
          return transformedManifest;
        },
      },
    );
    return data.value;
  }
  return null;
});

async function deleteCredential() {
  await $fetch(
    `/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}?permanent=true`,
    {
      method: "DELETE",
    },
  );
  await navigateTo({ path: `/wallet/${currentWallet.value}` });
}

useHead({ title: "View credential - walt.id" });
definePageMeta({
  layout: window.innerWidth > 650 ? "desktop-without-sidebar" : false,
});
</script>
