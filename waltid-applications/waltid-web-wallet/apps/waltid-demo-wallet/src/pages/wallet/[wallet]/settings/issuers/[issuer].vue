<template>
  <!-- Desktop view -->
  <div class="hidden sm:block p-8">
    <h1 class="text-2xl font-semibold text-left">{{ issuer }}</h1>
    <p class="text-left text-sm">Select credential to request.</p>
    <ul
      class="relative grid grid-cols-1 gap-y-4 sm:grid-cols-1 md:grid-cols-2 lg:grid-cols-3 sm:gap-x-4 sm:gap-y-6 mt-8"
    >
      <li
        v-for="credential in issuerCredentials?.credentials.filter(
          (c) => c.format == credentialType,
        )"
        :key="credential.id"
        class="col-span-1 divide-y divide-gray-200 rounded-2xl bg-white shadow transform hover:scale-105 cursor-pointer duration-200"
      >
        <NuxtLink
          :to="
            issuerCredentials?.issuer.uiEndpoint +
            credential.id.split('_')[0] +
            '&callback=' +
            config.public.issuerCallbackUrl
          "
          class="w-full"
        >
          <div
            ref="vcCardDiv"
            class="p-6 rounded-2xl shadow-2xl sm:shadow-lg h-full text-gray-900 bg-gradient-to-br from-[#0573F0] to-[#03449E] border-t-white border-t-[0.5px] bg-[#7B8794]"
          >
            <div class="mb-8">
              <div class="text-2xl font-bold bold text-white">
                {{
                  credential.id.split("_")[0].length > 20
                    ? credential.id.split("_")[0].substring(0, 20) + "..."
                    : credential.id.split("_")[0]
                }}
              </div>
            </div>

            <div class="sm:mt-18">
              <div
                :class="{ 'text-white': issuer, 'text-[#0573f000]': !issuer }"
              >
                Issuer
              </div>
              <div
                :class="{ 'text-white': issuer, 'text-[#0573f000]': !issuer }"
                class="font-bold"
              >
                {{ issuer ?? "Unknown" }}
              </div>
            </div>
          </div>
        </NuxtLink>
      </li>
    </ul>
  </div>

  <!-- Mobile view -->
  <CenterMain class="sm:hidden">
    <h1 class="text-lg font-semibold text-center">{{ issuer }}</h1>
    <p class="text-center">Select credential to request from issuer.</p>
    <div class="mt-8">
      <ol>
        <li
          v-for="credential in issuerCredentials?.credentials.filter(
            (c) => c.format == credentialType,
          )"
          :key="credential.id"
          class="flex items-center justify-between py-5 rounded-lg shadow-md mt-4 mb-10"
        >
          <NuxtLink
            :to="
              issuerCredentials?.issuer.uiEndpoint +
              credential.id.split('_')[0] +
              '&callback=' +
              config.public.issuerCallbackUrl
            "
            class="w-full"
          >
            <div class="flex items-start gap-x-3">
              <p class="mx-2 text-base font-semibold leading-6 text-gray-900">
                {{ credential.id.split("_")[0] }}
              </p>
            </div>
          </NuxtLink>
        </li>
      </ol>
      <p
        v-if="
          issuerCredentials?.credentials.filter(
            (c) => c.format == credentialType,
          ).length == 0
        "
        class="text-lg font-semibold text-center"
      >
        No credentials
      </p>
      <p v-if="error">
        Error while trying to use issuer <code>{{ issuer }}</code
        >:
        <span v-if="error.data" class="text-gray-600">{{
          error.data.startsWith("{")
            ? JSON.parse(error.data)?.message
            : error.data
        }}</span>
        <span v-else>{{ error }}</span>
      </p>

      <div v-if="pending" class="flex justify-center">
        <LoadingIndicator>Loading issuer configuration...</LoadingIndicator>
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";

interface Credential {
  id: string;
  format: string;
}

interface IssuerCredentials {
  credentials: Credential[];
  issuer: {
    uiEndpoint: string;
  };
}

const config = useRuntimeConfig();
const route = useRoute();

const issuer = route.params.issuer;

const currentWallet = useCurrentWallet();

const {
  pending,
  data: issuerCredentials,
  error,
  refresh,
} = useLazyFetch<IssuerCredentials>(
  `/wallet-api/wallet/${currentWallet.value}/issuers/${issuer}/credentials`,
);
const credentialType = ref<string>("jwt_vc_json");

definePageMeta({
  layout: window.innerWidth > 1024 ? "desktop" : "mobile",
});
useHead({
  title: `${issuer} - supported credentials`,
});
</script>

<style scoped></style>
