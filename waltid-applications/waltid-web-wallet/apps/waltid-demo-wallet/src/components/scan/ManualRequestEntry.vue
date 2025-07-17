<template>
    <form action="#" @submit.prevent="startRequest" class="w-[90%]">
        <div class="my-2">
            <div class="-m-0.5 rounded-lg p-0.5">
                <label class="sm:hidden text-[#E6F6FF] font-bold" for="comment">URL</label>
                <div class="mt-2">
                    <input v-if="isMobileView" id="comment" v-model="text" autofocus="autofocus"
                        class="block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6"
                        name="comment" />
                    <textarea v-else id="comment" v-model="text" autofocus="autofocus" rows="3" cols="50" wrap="soft"
                        spellcheck="true" placeholder="Your offer URL"
                        class="block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6"
                        name="comment"></textarea>
                </div>
            </div>
        </div>
        <div v-if="text" class="mt-5 flex justify-center">
            <button v-if="status === 'empty' || status === 'ok'" ref="submitBtn"
                :disabled="status === 'empty' || text.value == ''"
                class="w-full disabled:cursor-not-allowed disabled:opacity-60 disabled:bg-gray-500 rounded-xl bg-[#002159] px-3 py-2 text-sm text-center font-semibold text-[#E6F6FF] shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                type="submit">
                {{ requestTypeName }}
            </button>
            <p v-else-if="status === 'error'" class="px-3 py-2 text-sm font-semibold text-red-600 animate-pulse">Not a
                valid SIOP request string!</p>
        </div>
    </form>
</template>

<script setup>
import { getSiopRequestType, isSiopRequest, SiopRequestType } from "@waltid-web-wallet/composables/siop-requests.ts";

const isMobileView = ref(window.innerWidth < 650);

const emit = defineEmits(["request"]);

const text = ref("");
const submitBtn = ref(null);

function startRequest() {
    emit("request", text.value);
    text.value = "";
}

const status = computed(() => {
    if (text.value === "") {
        submitBtn.disabled = true;
        return "empty";
    } else if (!isSiopRequest(text.value)) return "error";
    else return "ok";
});

const requestType = computed(() => {
    return getSiopRequestType(text.value);
});

const requestTypeName = computed(() => {
    if (requestType.value == null) {
        return "Provide URL shared by issuer or verifier";
    } else if (requestType.value === SiopRequestType.PRESENTATION) {
        return "Present credential!";
    } else if (requestType.value === SiopRequestType.ISSUANCE) {
        return "Receive credential!";
    } else {
        return "...";
    }
});
</script>

<style scoped></style>
