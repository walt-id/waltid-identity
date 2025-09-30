<template>
  <CenterMain>
    <div>
      <h1 class="block text-lg font-semibold leading-6 text-gray-900 mb-4">
        Sign and Verify Messages
      </h1>
      <div class="mb-8">
        <h2 class="block text-md font-semibold text-gray-700 mb-2">
          Sign a Message
        </h2>

        <label for="keySelector" class="block text-sm font-medium text-gray-900">
          Select a Key:
        </label>
        <select
            id="keySelector"
            v-model="selectedKey"
            class="block w-full mt-1 mb-4 rounded-md border-gray-300 shadow-sm focus:ring-indigo-600 focus:border-indigo-600 sm:text-sm"
        >
          <option v-for="key in keys" :key="key.keyId.id" :value="key.keyId.id">
            {{ key.keyId.id }}
          </option>
        </select>

        <label for="message" class="block text-sm font-medium text-gray-900">
          Message (JSON):
        </label>
        <textarea
            id="message"
            v-model="messageToSign"
            class="block w-full mt-1 mb-4 rounded-md border-gray-300 shadow-sm focus:ring-indigo-600 focus:border-indigo-600 sm:text-sm"
            rows="4"
        ></textarea>

        <div class="flex justify-end">
          <button
              class="inline-flex items-center bg-blue-500 hover:bg-blue-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus:ring-2 focus:ring-offset-2 focus:ring-indigo-600"
              @click="signMessage"
          >
            <DocumentPlusIcon
                aria-hidden="true"
                class="h-5 w-5 text-white mr-1"
            />
            <span>Sign Message</span>
          </button>
        </div>
      </div>
      <div>
        <h2 class="block text-md font-semibold text-gray-700 mb-2">
          Verify a Signature
        </h2>

        <label for="jwk" class="block text-sm font-medium text-gray-900">
          JWK:
        </label>
        <textarea
            id="jwk"
            v-model="jwk"
            class="block w-full mt-1 mb-4 rounded-md border-gray-300 shadow-sm focus:ring-indigo-600 focus:border-indigo-600 sm:text-sm"
            rows="4"
            placeholder='{"kty":"OKP","crv":"Ed25519","kid":"...","x":"..."}'
        ></textarea>

        <label for="signature" class="block text-sm font-medium text-gray-900">
          Signature (JWT):
        </label>
        <textarea
            id="signature"
            v-model="signature"
            class="block w-full mt-1 mb-4 rounded-md border-gray-300 shadow-sm focus:ring-indigo-600 focus:border-indigo-600 sm:text-sm"
            rows="4"
        ></textarea>
        <div class="flex justify-end">
          <button
              class="inline-flex items-center bg-green-500 hover:bg-green-600 rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus:ring-2 focus:ring-offset-2 focus:ring-green-600"
              @click="verifySignature"
          >
            <span>Verify Signature</span>
          </button>
        </div>
      </div>
    </div>
  </CenterMain>
</template>

<script lang="ts" setup>
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import {DocumentPlusIcon} from "@heroicons/vue/24/outline";
import {onMounted, ref} from "vue";

const currentWallet = useCurrentWallet();
const keys = ref<any[]>([]);
const selectedKey = ref("");
const messageToSign = ref("");
const signature = ref("");
const jwk = ref("");

async function fetchKeys() {
  keys.value = await $fetch(`/wallet-api/wallet/${currentWallet.value}/keys`);

}

async function signMessage() {
  try {
    const messageObj = JSON.parse(messageToSign.value);

    const result = await $fetch(
        `/wallet-api/wallet/${currentWallet.value}/keys/${selectedKey.value}/sign`,
        {
          method: "POST",
          body: messageObj,
        }
    );

    signature.value = result;
  } catch (error) {
    if (error instanceof SyntaxError) {
      alert("Invalid JSON format. Please check your message.");
    } else {
      console.error("Signing error:", error);
      alert("An error occurred while signing the message.");
    }
  }
}

async function verifySignature() {
  try {
    const jwkObj = JSON.parse(jwk.value);
    const encodedJWK = encodeURIComponent(JSON.stringify(jwkObj));

    const result = await $fetch(
        `/wallet-api/wallet/${currentWallet.value}/keys/verify?JWK=${encodedJWK}`,
        {
          method: "POST",
          body: signature.value,
        }
    );

    alert(result ? "Signature is valid" : "Signature is invalid");
  } catch (error) {
    if (error instanceof SyntaxError) {
      alert("Invalid JWK format. Please check your JWK.");
    } else {
      console.error("Verification error:", error);
      alert("An error occurred while verifying the signature.");
    }
  }
}


onMounted(fetchKeys);
</script>

<style scoped></style>
