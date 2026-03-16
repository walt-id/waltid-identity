<template>
    <div class="flex min-h-full">
        <div
            class="flex flex-1 flex-col justify-center px-4 py-12 sm:px-6 lg:flex-none lg:px-20 xl:px-24 lg:bg-white lg:bg-opacity-50"
        >
            <div
                class="mx-auto w-full max-w-sm lg:w-96 p-3 lg:backdrop-blur-md lg:rounded-3xl lg:shadow lg:bg-neutral-100 lg:bg-opacity-40"
            >
                <div class="">
                    
                    <!-- TODO: i18n system -->
                    <h2 class="mt-4 text-3xl font-bold tracking-tight text-gray-800">
                        Sign in to your Freshblue wallet
                    </h2>
                    <p v-if="!isOidcLogin" class="mt-2 text-sm text-gray-600">
                        Or {{ " " }}
                        <NuxtLink
                            class="font-medium text-blue-600 hover:text-blue-500"
                            to="/signup"
                        >sign up for your Freshblue wallet!
                        </NuxtLink>
                    </p>
                    <p v-if="isOidcLogin" class="flex items-center">
                        <!--            <LoadingIndicator> OIDC Login processing... </LoadingIndicator>-->
                    </p>
                </div>

                <div v-if="!isOidcLogin" class="mt-5">
                    

                    <div class="mt-6">
                        <form class="space-y-6" @submit.prevent="login">
                            <div>
                                <label
                                    class="block text-sm font-medium leading-6 text-gray-900"
                                    for="email"
                                >
                  <span class="flex flex-row items-center">
                    <EnvelopeIcon class="h-5 mr-1" />
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

                            <div class="space-y-1">
                                <label
                                    class="block text-sm font-medium leading-6 text-gray-900"
                                    for="password"
                                >
                  <span class="flex flex-row items-center">
                    <IdentificationIcon class="h-5 mr-1" />
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

                            <div class="flex flex-col gap-1.5">
                                <button
                                    :class="[
                    success
                      ? 'bg-green-500 hover:bg-green-600 animate-bounce'
                      : 'bg-blue-600  hover:bg-blue-500',
                  ]"
                                    class="flex w-full justify-center rounded-md px-3 py-2 text-sm font-semibold text-white shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                                    type="submit"
                                >
                                    Sign in
                                    <svg
                                        v-if="isLoggingIn"
                                        class="animate-spin ml-1.5 mr-3 h-5 w-5 text-white"
                                        fill="none"
                                        viewBox="0 0 24 24"
                                        xmlns="http://www.w3.org/2000/svg"
                                    >
                                        <circle
                                            class="opacity-25"
                                            cx="12"
                                            cy="12"
                                            r="10"
                                            stroke="currentColor"
                                            stroke-width="4"
                                        ></circle>
                                        <path
                                            class="opacity-75"
                                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                                            fill="currentColor"
                                        ></path>
                                    </svg>
                                    <ArrowRightOnRectangleIcon v-else class="ml-1.5 h-5 w-5" />
                                </button>
                            </div>

                            <div class="flex items-center justify-between pb-2">
                                <div class="flex items-center">
                                    <input
                                        id="remember-me"
                                        class="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-600"
                                        name="remember-me"
                                        type="checkbox"
                                    />
                                    <label
                                        class="ml-2 block text-sm text-gray-900"
                                        for="remember-me"
                                    >
                    <span class="flex flex-row items-center">
                      <BookmarkSquareIcon class="-ml-0.5 mr-0.5 h-4 w-4" />
                      Remember me
                    </span></label
                                    >
                                </div>

                                <div class="text-sm">
                                    <a
                                        class="font-medium text-blue-600 hover:text-blue-500"
                                        href="#"
                                    >
                    <span class="flex flex-row items-center">
                      <QuestionMarkCircleIcon class="h-5 w-5 mr-0.5" />
                      Forgot your password?
                    </span>
                                    </a>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
        <div
            class="overflow-hidden max-h-screen absolute left-0 w-full h-full -z-10 hidden lg:block"
        >
            <img
                ref="container"
                :class="[isLoggingIn ? 'zoom-in' : 'zoom-out']"
                :src="bgImg"
                alt=""
                class="absolute inset-0 h-full w-full object-cover hidden lg:block -z-10"
            />
            <!-- src="https://images.unsplash.com/photo-1529144415895-6aaf8be872fb?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80"/> -->

            <!--<div class="relative hidden w-0 flex-1 lg:block">-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1567095761054-7a02e69e5c43?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1541701494587-cb58502866ab?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--<img class="absolute inset-0 h-full w-full object-cover" src="https://images.unsplash.com/photo-1516550893923-42d28e5677af?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1980&q=80" alt="" />-->
            <!--</div>-->
        </div>

        <div
            v-if="showWaltidLoadingSpinner"
            :class="[isLoggingIn ? 'animate-spin' : '']"
            :style="cardStyle"
            class="absolute bottom-3.5 right-3.5 w-10 lg:w-16 h-10 lg:h-16 overflow-hidden"
        >
            
        </div>

        <!--suppress PointlessBooleanExpressionJS -->
        <!-- TODO: move transition to @waltid-web-wallet/components/modals/ModalBase.vue -->
        <TransitionRoot :show="error.isError === true" as="template">
            <Dialog as="div" class="relative z-10" @close="closeModal">
                <TransitionChild
                    as="template"
                    enter="ease-out duration-300"
                    enter-from="opacity-0"
                    enter-to="opacity-100"
                    leave="ease-in duration-200"
                    leave-from="opacity-100"
                    leave-to="opacity-0"
                >
                    <div
                        class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"
                    />
                </TransitionChild>

                <div class="fixed inset-0 z-10 overflow-y-auto">
                    <div
                        class="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0"
                    >
                        <TransitionChild
                            as="template"
                            enter="ease-out duration-300"
                            enter-from="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                            enter-to="opacity-100 translate-y-0 sm:scale-100"
                            leave="ease-in duration-200"
                            leave-from="opacity-100 translate-y-0 sm:scale-100"
                            leave-to="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                        >
                            <DialogPanel
                                class="relative transform overflow-hidden rounded-lg bg-white px-4 pb-4 pt-5 text-left shadow-xl transition-all sm:my-8 sm:w-full sm:max-w-lg sm:p-6"
                            >
                                <div class="absolute right-0 top-0 hidden pr-4 pt-4 sm:block">
                                    <button
                                        class="rounded-md bg-white text-gray-400 hover:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                                        type="button"
                                        @click="closeModal"
                                    >
                                        <span class="sr-only">Close</span>
                                        <XMarkIcon aria-hidden="true" class="h-6 w-6" />
                                    </button>
                                </div>
                                <div class="sm:flex sm:items-start">
                                    <div
                                        class="mx-auto flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-red-100 sm:mx-0 sm:h-10 sm:w-10"
                                    >
                                        <ExclamationCircleIcon
                                            aria-hidden="true"
                                            class="h-6 w-6 text-red-600"
                                        />
                                    </div>
                                    <div class="mt-3 text-center sm:ml-4 sm:mt-0 sm:text-left">
                                        <DialogTitle
                                            as="h3"
                                            class="text-base font-semibold leading-6 text-gray-900"
                                        >
                                            Invalid login
                                        </DialogTitle>
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
                                        type="button"
                                        @click="closeModal"
                                    >
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
import {
    ArrowRightOnRectangleIcon,
    BookmarkSquareIcon,
    EnvelopeIcon,
    IdentificationIcon,
    QuestionMarkCircleIcon
} from "@heroicons/vue/20/solid";
import {Dialog, DialogPanel, DialogTitle, TransitionChild, TransitionRoot} from "@headlessui/vue";
import {ExclamationCircleIcon, XMarkIcon} from "@heroicons/vue/24/outline";
import {usePageLeave, useParallax} from "@vueuse/core";
import useModalStore from "@waltid-web-wallet/stores/useModalStore.ts";
import {useUserStore} from "@waltid-web-wallet/stores/user.ts";
import {storeToRefs} from "pinia";
import {useTenant} from "@waltid-web-wallet/composables/tenants.ts";
import {decodeJwt} from "jose";
import {MetaMaskSDK} from "@metamask/sdk";

const store = useModalStore();

const tenant = await useTenant().value;
const bgImg = tenant?.bgImage;
const name = tenant?.name;
const logoImg = tenant?.logoImage;
const showWaltidLoadingSpinner = tenant?.showWaltidLoadingSpinner;

const isLoggingIn = ref(false);
const error = ref({});
const success = ref(false);

let emailInput = "";
let passwordInput = "";

const userStore = useUserStore();
const { user } = storeToRefs(userStore);

const { status, data, signIn } = useAuth();

const signInRedirectUrl = ref("/");

async function connectOidc() {
    navigateTo("/wallet-api/auth/oidc-login", { external: true });
}

async function login() {
    console.log("logging in");
    isLoggingIn.value = true;

    const userData = {
        email: emailInput,
        password: passwordInput
    };

    // try {
    await signIn(
        { email: emailInput, password: passwordInput, type: "email" },
        { callbackUrl: signInRedirectUrl.value }
    )
        .then(() => {
            user.value = {
                id: "",
                friendlyName: userData.email
            };
        })
        .catch((err) => {
            console.log("Could not sign in", err);
            error.value = {
                isError: true,
                message:
                    "Please check that you have entered your correct email address and password!" //(await response.text())
            };
            isLoggingIn.value = false;
        });
}

function closeModal() {
    error.value = {};
}

const MMSDK = new MetaMaskSDK({
  dappMetadata: {name: "Walt.id Web Wallet", url: window.location.href},
  injectProvider: true
});




async function openWeb3() {
  await MMSDK.connect();
  const ethereum = MMSDK.getProvider();
    const response = await fetch("/wallet-api/auth/account/web3/nonce", { method: "GET" });
    const challenge = await response.text();
    console.log("====Frontend DEBUG LOGS====");
    console.log("Received JWT:", challenge);


    const accounts = await ethereum.request({ method: "eth_requestAccounts" });
    const address = accounts[0];

    const signature = await ethereum.request({
        method: "personal_sign",
        params: [challenge, address]
    });

    console.log("Signature:", signature);


    const verificationResponse = await fetch("/wallet-api/auth/account/web3/signed", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            publicKey: address,
            signed: signature,
            challenge: challenge // Send the full tokenText (JWT)
        })
    });


    console.log("Signing message:", {
        challenge,
        address,
        messageToSign: challenge
    });

    const result = await verificationResponse.json();
    console.log("Verification result: ", result);
    await authnzLogin(address, result.token);
}

definePageMeta({
    title: "Login to your wallet - walt.id",
    layout: "minimal",
    auth: {
        unauthenticatedOnly: true,
        navigateAuthenticatedTo: "/"
    }
});
const container = ref(null);
const parallax = reactive(useParallax(container));
const isLeft = reactive(usePageLeave());
const cardStyle = computed(() => ({
    overflow: "hidden",
    transition: ".5s ease-out all",
    transform: `rotateX(${parallax.source == "mouse" && (isLeft.value || parallax.roll == 0.5) ? 0 : parallax.roll * 60}deg) rotateY(
    ${parallax.source == "mouse" && (isLeft.value || parallax.tilt == -0.5) ? 0 : parallax.tilt * 60}deg)`
}));

useHead({
    title: "Login to your wallet - walt.id"
});

const route = useRoute();
if (route.redirectedFrom != undefined) {
    console.log(`Redirected from: ${JSON.stringify(route.redirectedFrom)}`);
    signInRedirectUrl.value = route.redirectedFrom.fullPath;
}

const isOidcLogin = ref(route.query.oidc_login == "true");

async function authnzLogin(address, token) {
    console.log("authnz: logging in");
    isLoggingIn.value = true;

    /*const userData = {
        email: emailInput,
        password: passwordInput
    };*/

    // try {
    await signIn(
        { token: token /*email: emailInput, password: passwordInput, type: "email"*/ },
        { callbackUrl: signInRedirectUrl.value }
    )
        .then(() => {
            user.value = {
                id: "",
                friendlyName: address
            };
        })
        .catch((err) => {
            console.log("Could not sign in", err);
            error.value = {
                isError: true,
                message: "Sign in failed!" //(await response.text())
            };
            isLoggingIn.value = false;
        });
}

async function tryLoginWithOidcSession() {
    const token = await fetch("/wallet-api/auth/oidc-token", {
        redirect: "manual"
    });

    const tokenText = await token.text();
    console.log("text token : " + tokenText);

    await signIn(
        {
            email: emailInput,
            token: tokenText,
            type: "oidc"
        },
        { callbackUrl: signInRedirectUrl.value }
    )
        .then(() => {
            console.log("Signed in with OIDC");
            console.log("Token: " + decodeJwt(tokenText).sub);
            user.value = {
                token: tokenText,
                id: "",
                email: decodeJwt(tokenText).email,
                name: decodeJwt(tokenText).name,
                oidcSession: true
            };

            console.log("Wrote to user: " + JSON.stringify(user.value));
        })
        .catch((err) => {
            console.log("Could not sign in", err);
            error.value = {
                isError: true,
                message: "Your OIDC sign in failed."
            };
            isLoggingIn.value = false;
            isOidcLogin.value = false;
        });
}

if (isOidcLogin.value) {
    tryLoginWithOidcSession();
}
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
    //animation: none;
}
</style>
