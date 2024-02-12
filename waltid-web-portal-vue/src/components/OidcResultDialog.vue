<template>
    <TransitionRoot :show="link != null" as="template">
        <Dialog as="div" class="relative z-10" @close="closeDialog">
            <TransitionChild as="template" enter="ease-out duration-300" enter-from="opacity-0" enter-to="opacity-100"
                             leave="ease-in duration-200" leave-from="opacity-100" leave-to="opacity-0"
            >
                <div class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" />
            </TransitionChild>

            <div class="fixed inset-0 z-10 w-screen overflow-y-auto">
                <div class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
                    <TransitionChild as="template" enter="ease-out duration-300"
                                     enter-from="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                                     enter-to="opacity-100 translate-y-0 sm:scale-100" leave="ease-in duration-200"
                                     leave-from="opacity-100 translate-y-0 sm:scale-100"
                                     leave-to="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                    >
                        <DialogPanel
                            class="relative transform overflow-hidden rounded-lg bg-white px-4 pb-4 pt-5 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6"
                        >
                            <div>
                                <div class="mx-auto flex h-96 w-24 items-center justify-center rounded-full">
                                    <NuxtErrorBoundary>
                                        <qrcode-vue :size="380" :value="link" level="L" style="height: 380px" />
                                        <template #error="{ error }">
                                            <p v-if="error.value?.message.includes('Data too long')">This QR code is too big to be displayed. <span class="text-xs">Length is: {{ link.length }}</span></p>
                                            <p v-else>{{ error }}</p>
                                        </template>
                                    </NuxtErrorBoundary>
                                </div>
                                DEBUG: Length is: {{ link.length }}
                                <div class="mt-3 text-center sm:mt-5">
                                    <DialogTitle as="h3" class="text-base font-semibold leading-6 text-gray-900">Claim your credentials
                                    </DialogTitle>
                                    <div class="mt-2">
                                        <p class="text-sm text-gray-500 p-2 h-16 overflow-x-scroll">
                                            <code>{{ link }}</code>
                                        </p>
                                    </div>
                                </div>

                                <div class="mt-3">
                                    <NuxtLink :to="webWalletLink"
                                              class="inline-flex w-full justify-center rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                                              type="button"
                                    >Open in Web Wallet
                                    </NuxtLink>
                                </div>
                            </div>
                            <div class="mt-5">
                                <button
                                    class="inline-flex w-full justify-center rounded-md bg-gray-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
                                    type="button"
                                    @click="closeDialog"
                                >Return to issuance
                                </button>
                            </div>
                        </DialogPanel>
                    </TransitionChild>
                </div>
            </div>
        </Dialog>
    </TransitionRoot>
</template>

<script lang="ts" setup>
import { Dialog, DialogPanel, DialogTitle, TransitionChild, TransitionRoot } from "@headlessui/vue";
import QrcodeVue from "qrcode.vue";

const config = useRuntimeConfig();

const props = defineProps({
    link: {
        type: String,
        required: true
    },
    text: {
        type: String
    }
});
const webWalletLink = computed(() => {
    return config.public.webWalletPrefix + props.link?.substring(props.link?.indexOf("/?") + 1);
});

const emit = defineEmits(["close"]);

function closeDialog() {
    emit("close");
}
</script>

<style scoped>

</style>
