<template>
    <div ref="vcCardDiv"
        :class="{ 'bg-white p-6 rounded-2xl shadow-2xl h-full text-gray-900': true, 'lg:w-[400px]': isDetailView }">
        <div class="flex justify-end gap-1.5">
            <CredentialIcon :credentialType="title"
                class="h-6 w-6 p-0.5 flex-none rounded-full backdrop-contrast-50 justify-self-start" />
            <div :class="credential?.expirationDate || credential?.validUntil ? (isNotExpired ? 'bg-cyan-100' : 'bg-red-50') : 'bg-cyan-50'"
                class="rounded-lg px-3 mb-2">
                <span :class="isNotExpired ? 'text-cyan-900' : 'text-orange-900'">
                    {{ isNotExpired ? "Valid" : "Expired" }}
                </span>
            </div>

            <div v-if="credential?._sd" class="rounded-lg px-3 mb-2 bg-cyan-100">
                <span>SD</span>
            </div>

            <div v-if="manifest" class="rounded-lg px-3 mb-2 text-cyn-100 bg-cyan-400">
                <span>Entra</span>
            </div>
        </div>

        <div class="mb-8">
            <div class="text-2xl font-bold bold">
                {{ !isDetailView ? titleTitelized.length > 20 ? titleTitelized.slice(0, 20) + "..." : titleTitelized :
                    titleTitelized }}
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

function parseJwt(token) {
    var base64Url = token.split('.')[1];
    var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    var jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function (c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));

    return JSON.parse(jsonPayload);
}

let credential: any;
if (props.credential.format && props.credential.format === "mso_mdoc") {
    const resp: any = await $fetch(`/wallet-api/util/parseMDoc`, {
        method: "POST",
        body: props.credential.document,
    });
    credential = {
        type: [resp.docType],
    }
}
else {
    credential = parseJwt(props.credential?.document)
}
const vc = credential?.vc ?? credential;

const isDetailView = props.isDetailView ?? false;
const manifest = vc?.manifest != "{}" ? vc?.manifest : null
const manifestDisplay = manifest ? (typeof manifest === 'string' ? JSON.parse(manifest) : manifest)?.display : null;
const manifestCard = manifestDisplay?.card;

const title = manifestDisplay?.title ?? vc?.type?.at(-1)
const titleTitelized = manifestDisplay?.title ?? vc?.type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2") ?? vc?.vct?.replace("_vc+sd-jwt", "").replace(/([a-z0-9])([A-Z])/g, "$1 $2");
const credentialSubtitle = manifestCard?.description ?? vc?.name;

const credentialImageUrl = manifestCard?.logo?.uri ?? vc?.issuer?.image?.id ?? vc?.issuer?.image;

const isNotExpired = vc?.expirationDate ? new Date(vc?.expirationDate).getTime() > new Date().getTime() : vc?.validUntil ? new Date(vc?.validUntil).getTime() > new Date().getTime() : true;

const issuerName = manifestCard?.issuedBy ?? vc?.issuer?.name;

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
