<template>
    <div ref="vcCardDiv"
    :class="{ 'bg-white p-6 rounded-2xl shadow-2xl h-full text-gray-900': true, 'lg:w-[400px]': isDetailView }">
        <div class="flex justify-end gap-1.5">
            <CredentialIcon :credentialType="title" class="h-6 w-6 p-0.5 flex-none rounded-full backdrop-contrast-50 justify-self-start" />
            <div :class="credential.expirationDate ? (isNotExpired ? 'bg-cyan-100' : 'bg-red-50') : 'bg-cyan-50'" class="rounded-lg px-3 mb-2">
                <span :class="isNotExpired ? 'text-cyan-900' : 'text-orange-900'">
                    {{ isNotExpired ? "Valid" : "Expired" }}
                </span>
            </div>

            <div v-if="credential._sd" class="rounded-lg px-3 mb-2 bg-cyan-100">
                <span>SD</span>
            </div>

            <div v-if="manifest" class="rounded-lg px-3 mb-2 text-cyn-100 bg-cyan-400">
                <span>Entra</span>
            </div>
        </div>

        <div class="mb-8">
            <div class="text-2xl font-bold bold">
                    {{ titleTitelized }}
            </div>
            <p v-if="credentialSubtitle" class="text-sm text-clip">{{ credentialSubtitle }}</p>
        </div>

        <div v-if="issuerName" class="flex items-center">
            <img v-if="credentialImageUrl" :src="credentialImageUrl" alt="Issuer image" class="w-12" />
            <div class="text-natural-600 ml-2 w-32">
                {{ issuerName }}
            </div>
        </div>

        <div v-if="showId" class="font-mono mt-3">ID: {{ credential.id }}</div>
    </div>
</template>

<script lang="ts" setup>
const props = defineProps({
    credential: {
        type: Object,
        required: true,
    },
    showId: {
        type: Boolean,
        required: false,
        default: false,
    },
    isDetailView: {
        type: Boolean,
        required: false,
        default: false,
    },
});

const credential = props.credential?.parsedDocument;
const isDetailView = props.isDetailView ?? false;
const manifest = props.credential?.manifest != "{}" ? props.credential?.manifest : null
const manifestDisplay = manifest ? (typeof manifest === 'string' ? JSON.parse(manifest) : manifest)?.display : null;
const manifestCard = manifestDisplay?.card;

const title = manifestDisplay?.title ?? credential?.type?.at(-1);
const titleTitelized = manifestDisplay?.title ?? credential?.type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2");
const credentialSubtitle = manifestCard?.description ?? credential?.name;

const credentialImageUrl = manifestCard?.logo?.uri ?? credential.issuer?.image?.id ?? credential.issuer?.image;

const isNotExpired = credential.expirationDate ? new Date(credential.expirationDate).getTime() > new Date().getTime() : true;

const issuerName = manifestCard?.issuedBy ?? credential.issuer?.name;

const vcCardDiv = ref(null)

watchEffect(async () => {
    try {
        if (manifestCard) {
            if (manifestCard?.backgroundColor) {
                vcCardDiv.value.style.background = manifestCard?.backgroundColor
            }

            if (manifestCard?.textColor) {
                vcCardDiv.value.style.color = manifestCard?.textColor
            }
        }
    } catch (_) { }
})

</script>
