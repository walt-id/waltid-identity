<template>
    <div ref="vcCardDiv"
        :class="{ 'p-6 rounded-2xl shadow-2xl sm:shadow-lg h-full text-gray-900': true, 'lg:w-[400px]': isDetailView, 'bg-gradient-to-br from-[#0573F0] to-[#03449E] border-t-white border-t-[0.5px]': isNotExpired, 'bg-[#7B8794]': !isNotExpired }">
        <div class="flex justify-end" v-if="!isNotExpired">
            <div class="text-black bg-[#CBD2D9] px-2 py-1 rounded-full text-xs">Expired</div>
        </div>

        <div class="mb-8">
            <div class="text-2xl font-bold bold text-white">
                {{ !isDetailView ? titleTitelized?.split("Credential")[0] : titleTitelized }}
            </div>
            <div class="text-2xl font-bold bold text-white">
                {{ !isDetailView ? "Credential" : "" }}
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
import { useCredential } from "../../composables/credential.ts";
import { computed, defineProps, ref, watchEffect } from "vue";

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

const { jwtJson: credential, manifest, titleTitelized, isNotExpired, issuerName } = useCredential(ref(props.credential as any));
const manifestCard = computed(() => manifest.value?.display?.card);
const isDetailView = ref(props.isDetailView ?? false);
const vcCardDiv: any = ref(null);

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
</script>
