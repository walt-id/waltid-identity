<template>
    <div>
        <!-- <video :src="$globals.mediaUrl(nft)" class="img-fluid img-thumbnail" v-if="isVideo && thumbnail" autoplay muted playsinline />
        <video :src="$globals.mediaUrl(nft)" class="image-fluid mx-auto img-thumbnail nft-img" v-if="isVideo && !thumbnail" autoplay loop playsinline />
        <img :src="$globals.mediaUrl(nft)" class="image-fluid mx-auto img-thumbnail nft-img" v-if="!isVideo" /> -->
        <!-- class="h-300 w-300 flex-shrink-0 rounded-bl-3xl rounded-tr-3xl mt-1.5 bg-gray-300 -->
        <video v-if="isVideo && thumbnail" :src="url" autoplay class="relative mx-auto rounded-lg overflow-hidden mt-1" muted playsinline />
        <video v-if="isVideo && !thumbnail" :src="url" autoplay class="h-300 w-300 flex-shrink-0 rounded-bl-3xl rounded-tr-3xl overflow-hidden bg-gray-300 mt-1" loop playsinline />
        <img v-if="!isVideo && thumbnail" :src="url" class="relative mx-auto rounded-lg overflow-hidden mt-1" />
        <img v-if="!isVideo && !thumbnail" :src="url" class="h-300 w-300 flex-shrink-0 rounded-bl-3xl rounded-tr-3xl overflow-hidden bg-gray-300 mt-1" />
    </div>
</template>

<script setup>
import { useIsVideo, useMediaUrl } from "~/composables/useNftMedia";

const props = defineProps({
    art: {
        type: Object,
        required: true,
    },
    thumbnail: {
        type: Boolean,
        required: false,
        default: true,
    },
});
const art = ref(props.art);
const { url } = useMediaUrl(art.value);
const thumbnail = ref(props.thumbnail);
const { isVideo } = useIsVideo(art.value.url);
</script>

<style scoped></style>
