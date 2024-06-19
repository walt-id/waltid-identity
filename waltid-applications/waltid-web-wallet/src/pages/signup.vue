<template>
    <div class="flex flex-col min-h-full">
        <div class="w-full">
            <img :src="logoImage" alt="walt.id logo" class="h-8 w-auto mx-auto mt-5" />
        </div>
        <div
            class="flex flex-1 flex-col justify-center px-4 py-12 sm:px-6 lg:flex-none lg:px-20 xl:px-24 lg:bg-white lg:bg-opacity-50">
            <div class="mx-auto w-full max-w-sm lg:w-96 p-3 lg:backdrop-blur-md lg:rounded-3xl lg:bg-opacity-40">
                <h2 class="mt-4 text-3xl font-bold tracking-tight text-black text-center">Sign Up</h2>
                <p class="mt-2 text-sm text-black text-center">
                    Already have an Account? {{ " " }}
                    <NuxtLink class="font-medium text-blue-600 hover:text-blue-500" to="/login">Sign in here</NuxtLink>
                </p>

                <div class="mt-8">
                    <div class="mt-6">
                        <form class="space-y-6" @submit.prevent="register">
                            <!-- name -->
                            <div>
                                <label class="block text-sm font-medium leading-6 text-gray-600" for="email">
                                    <span class="flex flex-row items-center">
                                        Name
                                    </span></label>
                                <div class="mt-1">
                                    <input id="name" v-model="nameInput" autocomplete="name" autofocus
                                        class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                        name="email" required type="text" />
                                </div>
                            </div>

                            <!-- email -->
                            <div>
                                <label class="block text-sm font-medium leading-6 text-gray-700" for="email">
                                    <span class="flex flex-row items-center">
                                        Email address
                                    </span></label>
                                <div class="mt-1">
                                    <input id="email" v-model="emailInput" autocomplete="email"
                                        class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                        name="email" required type="email" />
                                </div>
                            </div>
                            <!-- password -->
                            <div class="space-y-1">
                                <label class="block text-sm font-medium leading-6 text-gray-600" for="password">
                                    <span class="flex flex-row items-center">
                                        Password
                                    </span></label>
                                <div class="mt-1">
                                    <input id="password" v-model="passwordInput" autocomplete="current-password"
                                        class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                        name="password" required type="password" />
                                </div>
                            </div>

                            <div>
                                <button
                                    :class="[success ? 'bg-green-500 hover:bg-green-600 animate-bounce' : 'bg-gradient-to-br from-[#0573F0] to-[#03449E] hover:bg-blue-500']"
                                    class="flex w-full justify-center rounded-xl px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                                    type="submit">
                                    Sign Up
                                    <svg v-if="isProgress" class="animate-spin ml-1.5 mr-3 h-5 w-5 text-white"
                                        fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor"
                                            stroke-width="4"></circle>
                                        <path class="opacity-75"
                                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                                            fill="currentColor"></path>
                                    </svg>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>

        <TransitionRoot :show="error.isError === true" as="template">
            <Dialog as="div" class="relative z-10" @close="closeModal">
                <TransitionChild as="template" enter="ease-out duration-300" enter-from="opacity-0"
                    enter-to="opacity-100" leave="ease-in duration-200" leave-from="opacity-100" leave-to="opacity-0">
                    <div class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" />
                </TransitionChild>

                <div class="fixed inset-0 z-10 overflow-y-auto">
                    <div class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
                        <TransitionChild as="template" enter="ease-out duration-300"
                            enter-from="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                            enter-to="opacity-100 translate-y-0 sm:scale-100" leave="ease-in duration-200"
                            leave-from="opacity-100 translate-y-0 sm:scale-100"
                            leave-to="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95">
                            <DialogPanel
                                class="relative transform overflow-hidden rounded-lg bg-white px-4 pb-4 pt-5 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6">
                                <div class="absolute right-0 top-0 hidden pr-4 pt-4 sm:block">
                                    <button
                                        class="rounded-md bg-white text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                                        type="button" @click="closeModal">
                                        <span class="sr-only">Close</span>
                                        <XMarkIcon aria-hidden="true" class="h-6 w-6" />
                                    </button>
                                </div>
                                <div class="sm:flex sm:items-start">
                                    <div
                                        class="mx-auto flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-red-100 sm:mx-0 sm:h-10 sm:w-10">
                                        <ExclamationCircleIcon aria-hidden="true" class="h-6 w-6 text-red-600" />
                                    </div>
                                    <div class="mt-3 text-center sm:ml-4 sm:mt-0 sm:text-left">
                                        <DialogTitle as="h3" class="text-base font-semibold leading-6 text-gray-900">
                                            Error </DialogTitle>
                                        <div class="mt-2">
                                            <p class="text-sm text-gray-500">
                                                {{ error.message }}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                <div class="mt-5 sm:mt-4 sm:flex sm:flex-row-reverse">
                                    <button
                                        class="mt-3 inline-flex w-full justify-center rounded-md bg-blue-600 px-3 py-2 text-sm font-semibold text-neutral-50 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-blue-700 sm:mt-0 sm:w-auto"
                                        type="button" @click="closeModal">
                                        Okay
                                    </button>
                                </div>
                            </DialogPanel>
                        </TransitionChild>
                    </div>
                </div>
            </Dialog>
        </TransitionRoot>
    </div>
</template>

<script lang="ts" setup>
import { Dialog, DialogPanel, DialogTitle, TransitionChild, TransitionRoot } from "@headlessui/vue";
import { ExclamationCircleIcon, XMarkIcon } from "@heroicons/vue/24/outline";
import { useTenant } from "~/composables/tenants";

const tenant = await (useTenant()).value as any
const { logoImage } = tenant

const isProgress = ref(false);
const success = ref(false);
const error = ref({ isError: false, message: "" });

let nameInput = "";
let emailInput = "";
let passwordInput = "";

async function register() {
    isProgress.value = true;

    const user = {
        name: nameInput,
        email: emailInput,
        password: passwordInput,
        type: "email",
    };
    await $fetch("/wallet-api/auth/create", {
        method: "POST",
        body: user,
    })
        .then((response) => {
            success.value = true;
            isProgress.value = false;
            navigateTo("/");
        })
        .catch((err) => {
            isProgress.value = false;
            error.value = {
                isError: true,
                message: JSON.parse(err.data).message || "An error occurred",
            };
        });
}

function closeModal() {
    error.value = {
        isError: false,
        message: "",
    };
}

definePageMeta({
    title: "Register for your wallet - walt.id",
    layout: "minimal",
    auth: {
        unauthenticatedOnly: true,
        navigateAuthenticatedTo: "/",
    },
});

useHead({
    title: "Register for your wallet - walt.id",
});
</script>

<style scoped>
@keyframes zoom-in {

    25%,
    100% {
        transform: scale(2);
        filter: blur(1rem);
    }
}

@keyframes zoom-out {
    0% {
        transform: scale(2);
    }

    100% {
        transform: scale(1);
    }
}

.zoom-in {
    animation: zoom-in 0.4s normal forwards;
}

.zoom-out {
    animation: zoom-out 0.5s normal forwards;
    /* animation: none; */
}
</style>
