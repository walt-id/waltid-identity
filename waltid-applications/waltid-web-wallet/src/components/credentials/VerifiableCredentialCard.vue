<template>
    <div ref="vcCardDiv"
        :class="{ 'p-6 rounded-2xl shadow-2xl h-full text-gray-900': true, 'lg:w-[400px]': isDetailView, 'bg-gradient-to-br from-[#0573F0] to-[#03449E] border-t-white border-t-[0.5px]': isNotExpired, 'bg-[#7B8794]': !isNotExpired }">
        <div class="flex justify-end" v-if="!isNotExpired">
            <div class="text-black bg-[#CBD2D9] px-2 py-1 rounded-full text-xs">Expired</div>
        </div>

        <div class="mb-8">
            <div class="text-2xl font-bold bold text-white">
                {{ titleTitelized }}
            </div>
        </div>

        <div>
            <div :class="{ 'text-white': issuerName, 'text-[#0573f000]': !issuerName }">Issuer</div>
            <div :class="{ 'text-white': issuerName, 'text-[#0573f000]': !issuerName }" class="font-bold">
                {{ issuerName ?? 'Unknown' }}
            </div>
        </div>

        <div v-if="showId" class="font-mono mt-3">ID: {{ credential?.id }}</div>
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

const credentialImageUrl = manifestCard?.logo?.uri ?? credential?.issuer?.image?.id ?? credential?.issuer?.image;

const isNotExpired = credential?.expirationDate ? new Date(credential?.expirationDate).getTime() > new Date().getTime() : true;

const issuerName = manifestCard?.issuedBy ?? credential?.issuer?.name;

const vcCardDiv = ref(null)

watchEffect(async () => {
    try {
        if (vcCardDiv.value && manifestCard) {
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
