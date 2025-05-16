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
        <div v-if="titleTitelized !== 'Email Verification Credential' && titleTitelized !== 'Enrollment Credential'">
          <VerifiableCredentialCard :credential="credential" :isDetailView="true" />
        </div>
        <div v-else>
          <div ref="vcCardDiv"
            :class="{ 'hidden sm:block px-6 py-3 rounded-2xl shadow-2xl sm:shadow-lg h-full text-gray-900 bg-[url(/images/credential-bg.png)] bg-cover bg-center bg-no-repeat': true, 'lg:w-[550px]': true }">
            <div
              v-if="credential.parsedDocument && 'credentialSubject' in credential.parsedDocument && 'wiserID' in (credential.parsedDocument.credentialSubject as Object)"
              class="text-black rounded-full text-xs text-right">
              wiserID
              <span class="font-bold text-lg">{{ (credential.parsedDocument?.credentialSubject as any).wiserID }}</span>
            </div>
            <div v-else class="text-transparent rounded-full text-xs text-right">
              wiserID
              <span class="font-bold text-lg">wiserID</span>
            </div>
            <div class="flex">
              <div class="flex-1 border-r-2 border-gray-200">
                <div class="flex flex-col justify-between h-full">
                  <div class="mt-4">
                    <div class="flex items-center">
                      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path
                          d="M7 10.75L9.25 13L13 7.75M19 10C19 11.1819 18.7672 12.3522 18.3149 13.4442C17.8626 14.5361 17.1997 15.5282 16.364 16.364C15.5282 17.1997 14.5361 17.8626 13.4442 18.3149C12.3522 18.7672 11.1819 19 10 19C8.8181 19 7.64778 18.7672 6.55585 18.3149C5.46392 17.8626 4.47177 17.1997 3.63604 16.364C2.80031 15.5282 2.13738 14.5361 1.68508 13.4442C1.23279 12.3522 1 11.1819 1 10C1 7.61305 1.94821 5.32387 3.63604 3.63604C5.32387 1.94821 7.61305 1 10 1C12.3869 1 14.6761 1.94821 16.364 3.63604C18.0518 5.32387 19 7.61305 19 10Z"
                          stroke="#0F7DFA" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
                      </svg>
                      <div class="text-[#52606D] ml-2">Verified</div>
                    </div>
                    <div class="mt-2 text-[#1F2933] font-bold text-2xl">
                      {{ titleTitelized.split("Credential")[0] }}
                    </div>
                    <div class="text-[#1F2933] font-bold text-2xl">
                      Credential
                    </div>
                  </div>
                  <div class="mt-4">
                    <div class="text-[#52606D] text-sm">Issued by</div>
                    <div class="text-[#3E4C59] font-bold">{{ issuerName }}</div>
                  </div>
                </div>
              </div>
              <div class="flex-1 flex">
                <div class="flex flex-col justify-between h-full w-full">
                  <div class="mt-6">
                    <div class="mt-3 ml-10" v-for="(value, key, index) in credentialManifest?.claims">
                      <div
                        v-if="key === 'Name' || key === 'Email' || key === 'Verified At' || key === 'Date of Birth' || key === 'Start Date'">
                        <div class="text-[#52606D] text-sm">{{ key }}</div>
                        <div class="text-[#3E4C59] font-bold">{{ value }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="mt-4 flex justify-end w-full">
                    <img src="/images/pcg-credential-logo.png" alt="Credential Logo" class="w-10 h-10" />
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div ref="vcCardDiv"
            :class="{ 'block sm:hidden px-6 py-3 rounded-2xl shadow-2xl sm:shadow-lg h-full text-gray-900 bg-[url(/images/credential-bg.png)] bg-cover bg-center bg-no-repeat': true, 'lg:w-[550px]': true }">
            <div
              v-if="credential.parsedDocument && 'credentialSubject' in credential.parsedDocument && 'wiserID' in (credential.parsedDocument.credentialSubject as Object)"
              class="text-[#3E4C59] rounded-full text-xs text-right">
              wiserID
              <span class="text-[#3E4C59] font-bold text-sm">{{ (credential.parsedDocument?.credentialSubject as
                any).wiserID }}</span>
            </div>
            <div class="border-b-2 border-gray-200">
              <div class="flex flex-col justify-between h-full">
                <div class="flex items-center">
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M4.875 6.90625L6.09375 8.125L8.125 5.28125M11.375 6.5C11.375 7.14019 11.2489 7.77412 11.0039 8.36558C10.7589 8.95704 10.3998 9.49446 9.94715 9.94715C9.49446 10.3998 8.95704 10.7589 8.36558 11.0039C7.77412 11.2489 7.14019 11.375 6.5 11.375C5.85981 11.375 5.22588 11.2489 4.63442 11.0039C4.04296 10.7589 3.50554 10.3998 3.05285 9.94715C2.60017 9.49446 2.24108 8.95704 1.99609 8.36558C1.7511 7.77412 1.625 7.14019 1.625 6.5C1.625 5.20707 2.13861 3.96709 3.05285 3.05285C3.96709 2.13861 5.20707 1.625 6.5 1.625C7.79293 1.625 9.03291 2.13861 9.94715 3.05285C10.8614 3.96709 11.375 5.20707 11.375 6.5Z"
                      stroke="#0F7DFA" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <div class="text-[#52606D] text-sm ml-1">Verified</div>
                </div>
                <div class="mt-1 text-[#1F2933] font-bold">{{ titleTitelized }}</div>
              </div>
            </div>
            <div class="flex">
              <div class="flex flex-col justify-between h-full w-full">
                <div class="flex flex-wrap gap-3 mt-3">
                  <template v-for="(value, key) in credentialManifest?.claims" :key="key">
                    <div v-if="['Name', 'Email', 'Verified At', 'Date of Birth', 'Start Date'].includes(key)">
                      <div class="text-[#52606D] text-xs">{{ key }}</div>
                      <div class="text-[#3E4C59] text-sm font-bold">{{ value }}</div>
                    </div>
                  </template>
                </div>
                <div class="mt-4 flex justify-between w-full items-center">
                  <div>
                    <div class="text-[#52606D] text-xs">Issued by</div>
                    <div class="text-[#3E4C59] text-sm font-bold">{{issuerName.split(" ").map((word) =>
                      word.charAt(0).toUpperCase()).join("")}}</div>
                  </div>
                  <div>
                    <img src="/images/pcg-credential-logo.png" alt="Credential Logo" class="w-10 h-10" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Desktop view -->
      <div class="hidden sm:block px-4 py-6 bg-white rounded-xl">
        <hr v-if="credentialManifest" class="w-full border-gray-200 my-2" />
        <div v-if="credentialManifest" class="text-gray-500 font-bold mt-4 mb-8">
          Subject Info
        </div>
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
  titleTitelized,
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
        transform: (data: { claims: { [key: string]: string } }) => {
          return {
            ...data,
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
        },
      },
    );
    return data.value;
  }
  return null;
});

async function deleteCredential() {
  await $fetch(
    `/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`,
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
