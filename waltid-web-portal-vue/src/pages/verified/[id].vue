<template>
    <main>
        <p class="text-lg ml-2 font-semibold">Information for verification <code class="text-gray-600 font-mono">{{ verificationId }}</code>:</p>
        <div v-if="pending" class="flex items-center gap-2 p-2 shadow m-2">
            <Spinner class="w-5 h-5" />
            Loading verification information...
        </div>

        <ErrorDisplay :error="error" title="Could not receive verification information:" />

        <div v-if="data">
            <div class="m-2 p-2 shadow">
                <button @click="showPresentationDefinition = !showPresentationDefinition" type="button" class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50">
                    Show presentation definition
                </button>
                <pre v-if="showPresentationDefinition"><code>{{ data.presentationDefinition }}</code></pre>
            </div>


            <div v-if="data.tokenResponse" class="m-2 p-2 shadow">
                <p class="text-green-700">Received token response on verification!</p>

                <div class="m-2 p-2 shadow">
                    <button @click="showSubmissionDefinition = !showSubmissionDefinition" type="button" class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50">
                        Show submission definition
                    </button>
                    <pre v-if="showSubmissionDefinition"><code>{{ data.tokenResponse?.presentation_submission }}</code></pre>
                </div>

                <div class="m-2 p-2 shadow">
                    <button @click="showVpToken = !showVpToken" type="button" class="rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50">
                        Show VP Token
                    </button>
                    <pre v-if="showVpToken" class="overflow-x-auto"><code>{{ data.tokenResponse?.vp_token }}</code></pre>
                </div>
            </div>

            <div v-if="data.policyResults" class="m-2 p-2 shadow">
                <p class="text-green-700">Verification policy results available!</p>

                <div class="mx-auto max-w-7xl px-6 lg:px-8">
                    <div class="mx-auto max-w-2xl lg:max-w-none">
                        <div class="text-center">
                            <h2 class="text-3xl font-bold tracking-tight text-gray-900 sm:text-4xl">Policy results</h2>
                            <p class="mt-4 text-lg leading-8 text-gray-600">Verification Policies </p>
                        </div>
                        <dl class="grid grid-cols-1 gap-0.5 overflow-hidden rounded-2xl text-center sm:grid-cols-3 lg:grid-cols-5">
                            <div class="flex flex-col bg-gray-400/5 p-8">
                                <dt class="text-sm font-semibold leading-6 text-gray-600">Overall pass</dt>
                                <dd class="order-first text-3xl font-semibold tracking-tight text-gray-900">{{ data.verificationResult == true ? "PASSED" : "FAILED" }}
                                </dd>
                            </div>
                            <div class="flex flex-col bg-gray-400/5 p-8">
                                <dt class="text-sm font-semibold leading-6 text-gray-600">Time</dt>
                                <dd class="order-first text-3xl font-semibold tracking-tight text-gray-900">{{ data.policyResults.time }}
                                </dd>
                            </div>
                            <div class="flex flex-col bg-gray-400/5 p-8">
                                <dt class="text-sm font-semibold leading-6 text-gray-600">Policies run</dt>
                                <dd class="order-first text-3xl font-semibold tracking-tight text-gray-900">
                                    {{ data.policyResults?.policies_run }}
                                </dd>
                            </div>
                            <div class="flex flex-col bg-gray-400/5 p-8">
                                <dt class="text-sm font-semibold leading-6 text-gray-600">Policies succeeded</dt>
                                <dd class="order-first text-3xl font-semibold tracking-tight text-gray-900">
                                    {{ data.policyResults?.policies_succeeded }}
                                </dd>
                            </div>
                            <div class="flex flex-col bg-gray-400/5 p-8">
                                <dt class="text-sm font-semibold leading-6 text-gray-600">Policies failed</dt>
                                <dd class="order-first text-3xl font-semibold tracking-tight text-gray-900">
                                    {{ data.policyResults?.policies_failed }}
                                </dd>
                            </div>

                        </dl>
                    </div>
                </div>

                <div class="bg-white py-4 sm:py-6">
                    <ul>
                        <li v-for="cred in data.policyResults.results">
                            <VerifiedCred :credential="cred"></VerifiedCred>
                        </li>
                    </ul>
                </div>
            </div>

            <!--            <pre><code>{{ data }}</code></pre>-->
        </div>

    </main>
</template>

<script lang="ts" setup>
const config = useRuntimeConfig();
const route = useRoute();


const showPresentationDefinition = ref(false);
const showSubmissionDefinition = ref(false);
const showVpToken = ref(false);

const verificationId = route.params.id;
const verifyUrl = `${config.public.verifier}/openid4vc/session/${verificationId}`;
const { data, pending, error, refresh } = useFetch(verifyUrl);
</script>

<style scoped>
pre {
    white-space: pre-wrap;
}
</style>
