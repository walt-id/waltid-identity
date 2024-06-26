<template>
    <div ref="vcCardDiv"
        :class="{ 'p-6 rounded-2xl shadow-2xl sm:shadow-lg h-full text-gray-900': true, 'lg:w-[400px]': isDetailView, 'bg-gradient-to-br from-[#0573F0] to-[#03449E] border-t-white border-t-[0.5px]': isNotExpired, 'bg-[#7B8794]': !isNotExpired }">
        <div class="flex justify-end" v-if="!isNotExpired">
            <div class="text-black bg-[#CBD2D9] px-2 py-1 rounded-full text-xs">Expired</div>
        </div>

        <div class="mb-8">
            <div class="text-2xl font-bold bold text-white">
                {{ titleTitelized }}
            </div>
        </div>

        <div :class="{ 'sm:mt-18': isNotExpired, 'sm:mt-8': !isNotExpired }">
            <div :class="{ 'text-white': issuerName, 'text-[#0573f000]': !issuerName }">Issuer</div>
            <div :class="{ 'text-white': issuerName, 'text-[#0573f000]': !issuerName }" class="font-bold">
                {{ issuerName ?? 'Unknown' }}
            </div>
        </div>

        <div v-if="showId" class="font-mono mt-3">ID: {{ credential?.id }}</div>
    </div>
</template>

<script lang="ts" setup>
import { ref, watch, defineProps, onMounted } from 'vue';

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

function parseJwt(token: string) {
    var base64Url = token.split('.')[1];
    var base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    var jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function (c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));

    return JSON.parse(jsonPayload);
}

const credential = ref(props.credential?.parsedDocument ?? parseJwt(props.credential?.document).vc);
const isDetailView = ref(props.isDetailView ?? false);
const manifest = ref(props.credential?.manifest != "{}" ? props.credential?.manifest : null)
const manifestDisplay = ref(manifest.value ? (typeof manifest.value === 'string' ? JSON.parse(manifest.value) : manifest.value)?.display : null);
const manifestCard = ref(manifestDisplay.value?.card);

const titleTitelized = ref(manifestDisplay.value?.title ?? credential.value?.type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2"));
const credentialSubtitle = ref(manifestCard.value?.description ?? credential.value?.name);

const credentialImageUrl = ref(manifestCard.value?.logo?.uri ?? credential.value?.issuer?.image?.id ?? credential.value?.issuer?.image);

const isNotExpired = ref(credential.value?.expirationDate ? new Date(credential.value?.expirationDate).getTime() > new Date().getTime() : true);

const issuerName = ref(manifestCard.value?.issuedBy ?? credential.value?.issuer?.name);

const vcCardDiv: any = ref(null);

const updateComponent = () => {
    credential.value = props.credential?.parsedDocument ?? parseJwt(props.credential?.document).vc;
    isDetailView.value = props.isDetailView ?? false;
    manifest.value = props.credential?.manifest != "{}" ? props.credential?.manifest : null;
    manifestDisplay.value = manifest.value ? (typeof manifest.value === 'string' ? JSON.parse(manifest.value) : manifest.value)?.display : null;
    manifestCard.value = manifestDisplay.value?.card;
    titleTitelized.value = manifestDisplay.value?.title ?? credential.value?.type?.at(-1).replace(/([a-z0-9])([A-Z])/g, "$1 $2");
    credentialSubtitle.value = manifestCard.value?.description ?? credential.value?.name;
    credentialImageUrl.value = manifestCard.value?.logo?.uri ?? credential.value?.issuer?.image?.id ?? credential.value?.issuer?.image;
    isNotExpired.value = credential.value?.expirationDate ? new Date(credential.value?.expirationDate).getTime() > new Date().getTime() : true;
    issuerName.value = manifestCard.value?.issuedBy ?? credential.value?.issuer?.name;
};

watch(() => props, updateComponent, { deep: true });

watchEffect(async () => {
    try {
        if (vcCardDiv.value && manifestCard.value) {
            if (manifestCard.value?.backgroundColor) {
                vcCardDiv.value.style.background = manifestCard.value?.backgroundColor;
            }

            if (manifestCard.value?.textColor) {
                vcCardDiv.value.style.color = manifestCard.value?.textColor;
            }
        }
    } catch (_) { }
});

onMounted(updateComponent);

</script>
