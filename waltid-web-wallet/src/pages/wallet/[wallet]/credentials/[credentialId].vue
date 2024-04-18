<template>
    <CenterMain>
        <BackButton />
        <LoadingIndicator v-if="pending">Loading credential...</LoadingIndicator>
        <div v-else>
            <div class="flex justify-center items-center my-10">
                <!--                <div class="bg-white p-6 rounded-2xl shadow-2xl h-full w-[350px]">
                                    <div class="flex justify-end">
                                        <div
                                            :class="jwtJson?.expirationDate ? (new Date(jwtJson?.expirationDate).getTime() > new Date().getTime() ? 'bg-cyan-50' : 'bg-red-50') : 'bg-cyan-50'"
                                            class="rounded-lg px-3 mb-2"
                                        >
                                            <div
                                                :class="jwtJson?.expirationDate ? (new Date(jwtJson?.expirationDate).getTime() > new Date().getTime() ? 'text-cyan-900' : 'text-orange-900') : 'text-cyan-900'"
                                            >
                                                {{ jwtJson?.expirationDate ? (new Date(jwtJson?.expirationDate).getTime() > new Date().getTime() ? "Valid" : "Expired") : "Valid" }}
                                            </div>
                                        </div>
                                    </div>
                                    <h2 class="text-2xl font-bold mb-2 text-gray-900 bold mb-8">
                                        {{ jwtJson?.type[jwtJson?.type.length - 1].replace(/([a-z0-9])([A-Z])/g, "$1 $2") }}
                                    </h2>
                                    <div v-if="jwtJson?.issuer" class="flex items-center">
                                        <img :src="jwtJson?.issuer?.image?.id ? jwtJson?.issuer?.image?.id : jwtJson?.issuer?.image" class="w-12" />
                                        <div class="text-natural-600 ml-2 w-32">
                                            {{ jwtJson?.issuer?.name }}
                                        </div>
                                    </div>
                                </div>-->
                <VerifiableCredentialCard :credential="credential" :isDetailView="true"/>
            </div>
            <div class="px-7 py-1">
                <div class="text-gray-600 font-bold">
                    {{ jwtJson?.type[jwtJson?.type.length - 1].replace(/([a-z0-9])([A-Z])/g, "$1 $2") }}
                    Details
                </div>

                <!-- VerifiableDiploma -->
                <div v-if="jwtJson?.type[jwtJson?.type.length - 1] == 'VerifiableDiploma'">
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">Subject</div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Given Name</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.givenNames }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Family Name</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.familyName }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Date Of Birth</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.dateOfBirth }}
                        </div>
                    </div>
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">Achievement</div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Identifier</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.identifier }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Title</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.learningAchievement.title }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Description</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.learningAchievement.description }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Additional Notes</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.learningAchievement.additionalNote[0] }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Grading Scheme</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.gradingScheme.title }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Graduation Location</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.awardingOpportunity.location }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Study Timeframe</div>
                        <div class="font-bold"></div>
                    </div>
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">University</div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Legal Identifier</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.awardingOpportunity.awardingBody.eidasLegalIdentifier }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Name</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.awardingOpportunity.awardingBody.preferredName }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Registration</div>
                        <div class="font-bold">
                            {{ jwtJson?.credentialSubject?.awardingOpportunity.awardingBody.registration }}
                        </div>
                    </div>
                </div>

                <!-- Open Badge 3.0 -->
                <div v-else-if="jwtJson?.type[jwtJson?.type.length - 1] == 'OpenBadgeCredential'">
                    <hr class="my-5" />
                    <div class="flex items-center">
                        <div>
                            <div class="text-gray-500 mb-4 font-bold">Subject</div>
                            <div class="md:flex text-gray-500 mb-3 md:mb-1">
                                <div class="min-w-[19vw]">Name</div>
                                <div class="font-bold">
                                    {{ jwtJson?.credentialSubject?.achievement.name }}
                                </div>
                            </div>
                            <div class="md:flex text-gray-500 mb-3 md:mb-1">
                                <div class="min-w-[19vw]">Description</div>
                                <div class="font-bold">
                                    {{ jwtJson?.credentialSubject?.achievement.description }}
                                </div>
                            </div>
                            <div class="md:flex text-gray-500 mb-3 md:mb-1">
                                <div class="min-w-[19vw]">Criteria</div>
                                <div class="font-bold grow-0">
                                    {{ jwtJson?.credentialSubject?.achievement.criteria?.narrative }}
                                </div>
                            </div>
                        </div>
                        <img :src="jwtJson?.credentialSubject?.achievement.image?.id" class="w-32 h-20 hidden md:block" />
                    </div>
                </div>

                <!-- Permanent Resident Card -->
                <div v-else-if="jwtJson?.type[jwtJson?.type.length - 1] == 'PermanentResidentCard'">
                    <hr class="my-5" />
                    <div>
                        <div class="text-gray-500 mb-4 font-bold">Subject Info</div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Given Name</div>
                            <div class="font-bold">
                                {{ jwtJson?.credentialSubject?.givenName }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Surname</div>
                            <div class="font-bold">
                                {{ jwtJson?.credentialSubject?.familyName }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Date Of Birth</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.birthDate }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Sex</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.gender }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Country Of Birth</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.birthCountry }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Category</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.lprCategory }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">USCIS</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.lprNumber }}
                            </div>
                        </div>
                        <div class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Resident Since</div>
                            <div class="font-bold grow-0">
                                {{ jwtJson?.credentialSubject?.residentSince }}
                            </div>
                        </div>
                    </div>
                </div>

                <div v-if="issuerName || issuerDid || credentialIssuerService">
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">Issuer</div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Name</div>
                        <div class="font-bold">{{ issuerName }}</div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">DID</div>
                        <div class="font-bold truncate hover:overflow-auto">
                            {{ issuerDid }}
                        </div>
                    </div>
                    <div class="md:flex text-gray-500 mb-3 md:mb-1">
                        <div class="min-w-[19vw]">Service endpoint</div>
                        <NuxtLink class="font-bold truncate" :to="credentialIssuerService ?? ''" _blank>
                            {{ credentialIssuerService }}
                        </NuxtLink>
                    </div>
                </div>

                <div v-if="manifestClaims">
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">Entra Manifest Claims</div>
                    <ul>
                        <li v-for="[jsonKey, nameDescriptor] in Object.entries(manifestClaims)" class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">{{ nameDescriptor?.label ?? "Unknown" }}</div>
                            <div class="font-bold truncate hover:overflow-auto">
                                {{
                                    credential
                                        ? JSONPath({
                                              path: jsonKey.replace(/^vc\./, ""),
                                              json: jwtJson,
                                          }).find((elem) => elem) ?? `Not found: ${jsonKey}`
                                        : null
                                }}
                            </div>
                        </li>
                    </ul>
                </div>

                <div v-if="disclosures">
                    <hr class="my-5" />
                    <div class="text-gray-500 mb-4 font-bold">Selectively disclosable attributes</div>
                    <ul v-if="disclosures?.length > 0">
                        <li v-for="disclosure in disclosures" class="md:flex text-gray-500 mb-3 md:mb-1">
                            <div class="min-w-[19vw]">Attribute "{{ disclosure[1] }}"</div>
                            <div class="font-bold">{{ disclosure[2] }}</div>
                        </li>
                    </ul>
                    <div v-else>No disclosable attributes!</div>
                </div>

                <div class="text-gray-600 flex justify-between">
                    <hr class="mt-5 mb-3" />
                    <div>
                        {{
                            jwtJson?.expirationDate && jwtJson?.issuanceDate
                                ? "Valid from " + new Date(jwtJson?.issuanceDate).toISOString().slice(0, 10) + " to " + new Date(jwtJson?.expirationDate).toISOString().slice(0, 10)
                                : ""
                        }}
                    </div>
                    <div class="text-gray-900">
                        Issued:
                        {{ issuanceDate ? issuanceDate : "No issuance date" }}
                    </div>
                </div>
            </div>
        </div>
        <div class="flex justify-between mt-12">
            <div class="flex gap-3">
                <button
                    class="rounded bg-primary-400 px-2 py-1 text-white shadow-sm hover:bg-primary-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-400"
                    type="button"
                    @click="showCredentialJson = !showCredentialJson"
                >
                    View Credential
                </button>
                <button
                    v-if="manifest"
                    class="rounded bg-primary-400 px-2 py-1 text-white shadow-sm hover:bg-primary-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-400"
                    type="button"
                    @click="showCredentialManifest = !showCredentialManifest"
                >
                    View Credential Manifest
                </button>
            </div>
            <button
                class="rounded bg-red-500 px-2 py-1 text-white shadow-sm hover:bg-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-500"
                type="button"
                @click="deleteCredential"
            >
                Delete Credential
            </button>
        </div>
        <div v-if="showCredentialJson">
            <div class="flex space-x-3 mt-10">
                <div class="min-w-0 flex-1">
                    <p class="text-sm font-semibold text-gray-900 whitespace-pre-wrap">
                        {{ credentialId }}
                    </p>
                    <p class="text-sm text-gray-500">Verifiable Credential data below:</p>
                </div>
            </div>
            <div class="p-3 shadow mt-3">
                    <h3 class="font-semibold mb-2">QR code</h3>
                    <div v-if="credential && credential.document">
                        <qrcode-vue v-if="credential.document && credential.document.length <= 4296" :value="credential.document" level="L" size="500" class="m-5++++++++++----------------------------------++++++++++++++++++++++++++++" />
                        <p v-else>Unfortunately, this Verifiable Credential is too big to be viewable as QR code (credential size is {{ credential.document.length }} characters, but the maximum a QR code
                            can hold is 4296).</p>
                    </div>
                </div>
            <div class="shadow p-3 mt-2 font-mono overflow-scroll">
                <h3 class="font-semibold mb-2">JWT</h3>
                <pre v-if="credential && credential?.document">{{
                    /*JSON.stringify(JSON.parse(*/
                    credential.document /*), null, 2)*/ ?? ""
                }}</pre>
            </div>
            <div class="shadow p-3 mt-2 font-mono overflow-scroll">
                <h3 class="font-semibold mb-2">JSON</h3>
                <pre v-if="credential && credential?.document">{{ jwtJson }} </pre>
            </div>
        </div>

        <div v-if="showCredentialManifest">
            <div class="flex space-x-3 mt-10">
                <div class="min-w-0 flex-1">
                    <p class="text-sm font-semibold text-gray-900 whitespace-pre-wrap">
                        {{ credentialId }}
                    </p>
                    <p class="text-sm text-gray-500">Verifiable Credential Manifest below:</p>
                </div>
            </div>
            <div class="shadow p-3 mt-2 font-mono overflow-scroll">
                <h3 class="font-semibold mb-2">Credential manifest</h3>
                <pre v-if="manifest">{{ manifest }} </pre>
            </div>
        </div>
    </CenterMain>
</template>

<script lang="ts" setup>
import LoadingIndicator from "~/components/loading/LoadingIndicator.vue";
import CenterMain from "~/components/CenterMain.vue";
import BackButton from "~/components/buttons/BackButton.vue";
import { ref } from "vue";
import { decodeBase64ToUtf8 } from "~/composables/base64";
import VerifiableCredentialCard from "~/components/credentials/VerifiableCredentialCard.vue";
import { parseDisclosures } from "~/composables/disclosures";
import { JSONPath } from "jsonpath-plus";
import QrcodeVue from 'qrcode.vue'

const route = useRoute();
const credentialId = route.params.credentialId as string;
const currentWallet = useCurrentWallet();

const showCredentialJson = ref(false);
const showCredentialManifest = ref(false);

const jwtJson = computed(() => {
    if (credential.value) {
        const vcData = credential.value.document.split(".")[1];
        console.log("Credential is: ", vcData);

        const vcBase64 = vcData.replaceAll("-", "+").replaceAll("_", "/");
        console.log("Base64 from base64url: ", vcBase64);

        const decodedBase64 = decodeBase64ToUtf8(vcBase64).toString();
        console.log("Decoded: ", decodedBase64);

        let parsed
        try {
            parsed = JSON.parse(decodedBase64);
        } catch (e) {
            console.log(e)
        }

        if (parsed?.vc) return parsed.vc;
        else return parsed;
    } else return null;
});

const disclosures = computed(() => {
    if (credential.value && credential.value.disclosures) {
        return parseDisclosures(credential.value.disclosures);
    } else return null;
});

type WalletCredential = {
    wallet: string;
    id: string;
    document: string;
    disclosures: string | null;
    addedOn: string;
    manifest: string | null;
    parsedDocument: object | null;
};

const { data: credential, pending, refresh, error } = await useLazyFetch<WalletCredential>(`/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`);
refreshNuxtData();

const manifest = computed(() => (credential.value?.manifest && credential.value?.manifest != "{}" ? (typeof credential.value?.manifest === 'string' ? JSON.parse(credential.value?.manifest) : credential.value?.manifest) : null));
const manifestClaims = computed(() => manifest.value?.display?.claims);

const issuerName = ref(null);
const issuerDid = ref(null);
const credentialIssuerService = ref(null);

watchEffect(() =>{
    issuerName.value = manifest.value?.display?.card?.issuedBy ?? jwtJson.value?.issuer?.name;
    issuerDid.value = manifest.value?.input?.issuer ?? jwtJson.value?.issuer?.id ?? jwtJson.value?.issuer;
    credentialIssuerService.value = manifest.value?.input?.credentialIssuer;
});

const issuanceDate = computed(() => {
    if (jwtJson?.issuanceDate) {
        return new Date(jwtJson?.issuanceDate).toISOString().slice(0, 10);
    } else if (jwtJson?.validFrom) {
        return new Date(jwtJson?.validFrom).toISOString().slice(0, 10);
    } else {
        return null;
    }
});

useHead({ title: "View credential - walt.id" });

async function deleteCredential() {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials/${encodeURIComponent(credentialId)}`, {
        method: "DELETE",
    });
    await navigateTo({ path: `/wallet/${currentWallet.value}` });
}
</script>
