<template>
    <form action="#" @submit.prevent="startRequest">
        <div class="mt-2">
            <div class="-m-0.5 rounded-lg p-0.5">
                <label class="sr-only" for="comment">Manual request entry</label>
                <div>
                    <textarea
                        id="comment"
                        v-model="text"
                        autofocus="autofocus"
                        class="block w-full rounded-md border-0 px-3 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:text-sm sm:leading-6"
                        name="comment"
                        placeholder="Enter request string manually..."
                        rows="5"
                    />
                </div>
            </div>
        </div>
        <div class="mt-2 flex justify-end">
            <button
                v-if="status === 'empty' || status === 'ok'"
                ref="submitBtn"
                :disabled="status === 'empty' || text.value == ''"
                class="inline-flex disabled:cursor-not-allowed disabled:opacity-60 disabled:bg-gray-500 items-center rounded-md bg-blue-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
                type="submit"
            >
                {{ requestTypeName }}
            </button>
            <p v-else-if="status === 'error'" class="px-3 py-2 text-sm font-semibold text-red-600 animate-pulse">Not a valid SIOP request string!</p>
        </div>
    </form>
</template>

<script setup>
import { getSiopRequestType, isSiopRequest, SiopRequestType } from "~/composables/siop-requests";

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
        return "Paste request string first...";
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
