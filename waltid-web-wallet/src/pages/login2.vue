<template>
    <div class="sm:flex items-center bg-transparent">
        <ul class="sm:m-5 divide-y w-full divide-gray-200 rounded-lg shadow sm:grid sm:grid-cols-2 gap-3">
            <li>
                <LoginMethodView id="email" :idx="0" icon="heroicons:envelope" name="E-Mail & password">
                    <form class="w-full" @submit.prevent="loginEmail">
                        <div>
                            <label class="block text-sm font-medium leading-6 text-gray-900" for="email">
                                <span class="flex flex-row items-center">
                                    <Icon class="h-5 w-5 mr-1" name="heroicons:envelope-solid" />
                                    Email address
                                </span>
                            </label>
                            <div class="mt-2">
                                <input
                                    id="email"
                                    v-model="emailInput"
                                    :required="true"
                                    autocomplete="email"
                                    autofocus
                                    class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                    name="email"
                                    type="email"
                                />
                            </div>
                        </div>

                        <div class="space-y-1 mt-2">
                            <label class="block text-sm font-medium leading-6 text-gray-900" for="password">
                                <span class="flex flex-row items-center">
                                    <Icon class="h-5 w-5 mr-1" name="heroicons:identification-solid" />
                                    Password
                                </span></label
                            >
                            <div class="mt-2">
                                <input
                                    id="password"
                                    v-model="passwordInput"
                                    :required="true"
                                    autocomplete="current-password"
                                    class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                    name="password"
                                    type="password"
                                />
                            </div>
                        </div>

                        <div class="mt-2.5">
                            <button
                                class="bg-blue-600 hover:bg-blue-500 flex w-full justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                                type="submit"
                            >
                                Sign in with email
                                <InlineLoadingCircle v-if="isLoggingInEmail" class="ml-1.5 mr-3 h-5 w-5" />
                                <Icon v-else class="ml-1.5 h-5 w-5" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                            </button>
                        </div>
                    </form>
                </LoginMethodView>
            </li>
            <li>
                <LoginMethodView id="web3" :idx="1" icon="ic:baseline-filter-3" name="Web 3">
                    <div class="w-full place-self-center flex flex-col gap-2">
                        <button
                            class="group relative w-full flex justify-center bg-white bg-opacity-90 border text-black hover:(bg-blue-500 text-white) rounded-md px-3 py-2 text-sm font-semibold shadow focus-visible:(outline outline-2 outline-offset-2 outline-blue-600)"
                            type="submit"
                        >
                            <span class="pointer-events-none absolute inset-y-0 left-0 mx-1.5 flex items-center">
                                <Icon aria-hidden="true" class="h-5 w-6 group-hover:animate-pulse" name="simple-icons:walletconnect" />
                            </span>
                            Login with WalletConnect
                            <Icon class="ml-1.5 h-5 w-5" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                        </button>

                        <button
                            class="group relative w-full flex justify-center bg-white bg-opacity-90 border text-black hover:(bg-blue-500 text-white) rounded-md px-3 py-2 text-sm font-semibold shadow focus-visible:(outline outline-2 outline-offset-2 outline-blue-600)"
                            type="submit"
                        >
                            <span class="pointer-events-none absolute inset-y-0 left-0 mx-1.5 flex items-center">
                                <Icon aria-hidden="true" class="h-5 w-6 group-hover:animate-pulse" name="simple-icons:near" />
                            </span>
                            Login with NEAR
                            <Icon class="ml-1.5 h-5 w-5 justify-self-start place-self-start start-0 left-0 self-start" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                        </button>

                        <button
                            class="group relative w-full flex justify-center bg-white bg-opacity-90 border text-black hover:(bg-blue-500 text-white) rounded-md px-3 py-2 text-sm font-semibold shadow focus-visible:(outline outline-2 outline-offset-2 outline-blue-600)"
                            type="submit"
                        >
                            <span class="pointer-events-none absolute inset-y-0 left-0 mx-1.5 flex items-center">
                                <Icon aria-hidden="true" class="h-5 w-6 group-hover:animate-pulse" name="simple-icons:algorand" />
                            </span>
                            Login with Algorand
                            <Icon class="ml-1.5 h-5 w-5 justify-self-start place-self-start start-0 left-0 self-start" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                        </button>
                    </div>
                </LoginMethodView>
            </li>
            <li>
                <LoginMethodView id="oidc" :idx="2" icon="simple-icons:openid" name="OpenID Connect / OAuth 2">
                    <ul class="divide-y divide-gray-100 w-full" role="list">
                        <li v-for="(oidcProvider, idx) of availableOidcProviders" :key="idx" class="flex justify-between gap-x-2 py-2 w-full">
                            <button class="flex min-w-0 gap-x-4 rounded-xl bg-white bg-opacity-90 border p-2 shadow w-full group hover:(bg-primary-500 text-white cursor-pointer)">
                                <img :src="oidcProvider.icon" alt="" class="h-12 w-12 flex-none rounded-full bg-gray-50 border" />
                                <div class="min-w-0 flex-auto text-left">
                                    <p class="text-sm font-semibold leading-6 text-gray-900 group-hover:text-white">{{ oidcProvider.name }}</p>
                                    <p class="mt-1 text-xs leading-5 text-gray-500 group-hover:text-gray-200">
                                        {{ oidcProvider.description }}
                                    </p>
                                </div>
                            </button>
                        </li>
                    </ul>
                </LoginMethodView>
            </li>
            <li>
                <LoginMethodView id="webauthn" :idx="3" icon="simple-icons:webauthn" name="WebAuthn / Passkeys">
                    <form class="w-full" @submit.prevent="loginEmail">
                        <div>
                            <label class="block text-sm font-medium leading-6 text-gray-900" for="email">
                                <span class="flex flex-row items-center">
                                    <Icon class="h-5 w-5 mr-1" name="heroicons:envelope-solid" />
                                    Email address
                                </span></label
                            >
                            <div class="mt-2">
                                <input
                                    id="email"
                                    v-model="emailInput"
                                    :required="true"
                                    autocomplete="email"
                                    autofocus
                                    class="block w-full rounded-md border-0 py-1.5 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6 px-2 text-gray-600"
                                    name="email"
                                    type="email"
                                />
                            </div>
                        </div>

                        <div class="mt-2 flex gap-2">
                            <button
                                class="bg-blue-600 hover:bg-blue-500 flex w-full justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                                type="submit"
                            >
                                Register
                                <InlineLoadingCircle v-if="isLoggingInEmail" class="ml-1.5 mr-3 h-5 w-5" />
                                <Icon v-else class="ml-1.5 h-5 w-5" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                            </button>

                            <button
                                class="bg-blue-600 hover:bg-blue-500 flex w-full justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                                type="submit"
                            >
                                Authenticate
                                <InlineLoadingCircle v-if="isLoggingInEmail" class="ml-1.5 mr-3 h-5 w-5" />
                                <Icon v-else class="ml-1.5 h-5 w-5" name="heroicons:arrow-right-end-on-rectangle-20-solid" />
                            </button>
                        </div>
                    </form>
                </LoginMethodView>
            </li>
        </ul>

        <div class="overflow-hidden max-h-screen absolute left-0 w-full h-full -z-10 hidden lg:block">
            <img ref="container" :class="[isAnyLoggingIn ? 'zoom-in' : 'zoom-out']" src="/images/start-page-background.png" alt="" class="absolute inset-0 h-full w-full object-cover hidden lg:block -z-10" />
            <!-- src="https://images.unsplash.com/photo-1529144415895-6aaf8be872fb?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80"/> -->

            <!--<div class="relative hidden w-0 flex-1 lg:block">-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1567095761054-7a02e69e5c43?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1541701494587-cb58502866ab?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1516550893923-42d28e5677af?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--</div>-->
        </div>
    </div>
</template>

<script lang="ts" setup>
import LoginMethodView from "~/components/login/LoginMethodView.vue";
import InlineLoadingCircle from "~/components/loading/InlineLoadingCircle.vue";

const emailInput = ref("");
const passwordInput = ref("");

const isLoggingInEmail = ref(false);
const isLoggingInWeb3 = ref(false);

const isAnyLoggingIn = computed(() => {
    return isLoggingInEmail.value || isLoggingInWeb3.value
})

const availableOidcProviders = [
    {
        name: "walt.id Keycloak",
        url: "",
        description: "walt.id hosted Keycloak 23.0.6",
        icon: "/svg/waltid.svg",
    },
    {
        name: "walt.id Keycloak Test (Dev) Instance",
        url: "",
        description: "This is an unsecured test deployment. Do not use for production purposes.",
        icon: "/svg/walt-s.svg",
    },
];

function loginEmail() {}

definePageMeta({
    title: "Login to your wallet - walt.id",
    layout: "minimal",
    auth: {
        unauthenticatedOnly: true,
        navigateAuthenticatedTo: "/",
    },
});
</script>

<style scoped></style>
