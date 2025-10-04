<template>
  <CenterMain>
    <BackButton />
    <ol
      class="divide-y divide-gray-100 list-decimal border rounded-2xl mt-2 px-2"
      role="list"
    >
      <li
        v-for="credential in issuerCredentials?.credentials"
        :key="credential"
        class="flex items-center justify-between gap-x-6 py-4"
      >
        <div class="min-w-0">
          <div class="flex items-start gap-x-3">
            <p class="mx-2 text-base font-semibold leading-6 text-gray-900">
              {{ credential.id }}
            </p>
          </div>
          <div class="flex items-start gap-x-3">
            <p
              class="mx-2 overflow-x-auto text-base font-normal leading-6 text-gray-500"
            >
              {{ credential.format }}
            </p>
          </div>
          <!-- <div class="flex items-start gap-x-3">
                        <p class="mx-2 overflow-x-auto text-base font-normal leading-6 text-gray-500">
                            <span>types:</span> {{ credential.types.join(',') }}
                        </p>
                    </div> -->
        </div>
        <div class="flex flex-none items-center gap-x-4">
          <NuxtLink
            :to="
              issuerCredentials?.issuer.uiEndpoint +
              credential.id +
              '&callback=' +
              config.public.issuerCallbackUrl
            "
            class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
            type="button"
          >
            Request credential offer
          </NuxtLink>
        </div>
      </li>
    </ol>
    <p
      v-if="credentials && issuerCredentials?.credentials?.length == 0"
      class="mt-2"
    >
      No credentials.
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
    <LoadingIndicator v-if="pending"
      >Loading issuer configuration...</LoadingIndicator
    >
  </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import BackButton from "@waltid-web-wallet/components/buttons/BackButton.vue";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";

const config = useRuntimeConfig();
const route = useRoute();

const issuer = route.params.issuer;

const currentWallet = useCurrentWallet();

const {
  pending,
  data: issuerCredentials,
  error,
  refresh,
} = useLazyFetch(
  `/wallet-api/wallet/${currentWallet.value}/issuers/${issuer}/credentials`,
);

useHead({
  title: `${issuer} - supported credentials`,
});
</script>

<style scoped></style>
