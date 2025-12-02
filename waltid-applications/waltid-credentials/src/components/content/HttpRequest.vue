<template>
<!--    <div class="border pt-2 pb-3 px-3">
        <div class="px-4 sm:px-0 flex justify-between items-center">
            <h3 class="text-base leading-7 text-white">
                <span class="text-white mr-1">HTTP:</span> <span class="font-extrabold mr-0.5">{{ method }}</span> <a :href="url">{{ url }}</a>
                <hr />
            </h3>
            &lt;!&ndash;            <span class="isolate inline-flex rounded-md shadow-sm">&ndash;&gt;
            &lt;!&ndash;                <button&ndash;&gt;
            &lt;!&ndash;                    class="relative inline-flex items-center gap-x-1.5 rounded-l-md bg-gray-800 px-3 py-2 text-sm font-semibold text-white ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-10"&ndash;&gt;
            &lt;!&ndash;                    type="button"&ndash;&gt;
            &lt;!&ndash;                >&ndash;&gt;
            &lt;!&ndash;                    <BookmarkIcon aria-hidden="true" class="-ml-0.5 h-5 w-5 text-gray-400" />&ndash;&gt;
            &lt;!&ndash;                    Bookmark for your use-case&ndash;&gt;
            &lt;!&ndash;                </button>&ndash;&gt;
            &lt;!&ndash;                <button&ndash;&gt;
            &lt;!&ndash;                    class="relative -ml-px inline-flex items-center rounded-r-md bg-gray-600 px-3 py-2 text-sm font-semibold text-gray-200 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 focus:z-10"&ndash;&gt;
            &lt;!&ndash;                    type="button"&ndash;&gt;
            &lt;!&ndash;                >&ndash;&gt;
            &lt;!&ndash;                    12&ndash;&gt;
            &lt;!&ndash;                </button>&ndash;&gt;
            &lt;!&ndash;            </span>&ndash;&gt;
        </div>
        <p v-if="description" class="max-w-2xl text-sm leading-6 text-gray-400">{{ description }}</p>
        <div class="mt-2 border-t border-white/10">
            <dl class="divide-y divide-white/10">
                <div v-for="[headerKey, headerValue] in headersMap" v-if="headersMap && headersMap.size >= 1" class="px-4 py-1 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0">
                    <dt class="text-sm font-medium leading-6 text-white">
                        Header:
                        <span class="ml-1 font-mono">
                            {{ headerKey }}<span v-if="headerValue.required" class="text-red-600">*<span class="text-xs font-sans font-light">required</span></span>
                        </span>
                    </dt>
                    <dd class="mt-1 text-sm leading-6 text-gray-400 sm:col-span-2 sm:mt-0">
                        <input :placeholder="headerValue.example" class="bg-gray-700 text-white px-1 w-full" v-bind="headerValue.value" />
                    </dd>
                </div>
                <div v-for="[key, value] in queryParamMap" v-if="queryParamMap && queryParamMap.size >= 1" class="px-4 py-1 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0">
                    <dt class="text-sm font-medium leading-6 text-white">
                        Query: <span class="ml-1 font-mono">{{ key }}</span>
                    </dt>
                    <dd class="mt-1 text-sm leading-6 text-gray-400 sm:col-span-2 sm:mt-0">{{ value }}</dd>
                </div>
                <div v-for="[key, value] in pathParamMap" v-if="pathParamMap && pathParamMap.size >= 1" class="px-4 py-1 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0">
                    <dt class="text-sm font-medium leading-6 text-white">
                        Path: <span class="ml-1 font-mono">{{ key }}</span>
                    </dt>
                    <dd class="mt-1 text-sm leading-6 text-gray-400 sm:col-span-2 sm:mt-0">{{ value }}</dd>
                </div>
            </dl>
        </div>

        &lt;!&ndash;            v-if="body"&ndash;&gt;
        &lt;!&ndash;        @update:model-value="$emit('update:modelValue', $event.target.value)"&ndash;&gt;
        <div v-if="body" class="">
            Body:
            <LazyMonacoEditor v-if="body" :model-value="bodyString" :options="{ theme: 'vs-dark', fontSize: 12 }" class="h-56 w-full" lang="json"> Loading editor... </LazyMonacoEditor>
        </div>

        <span class="flex gap-3 items-center mt-1">
            <button
                class="rounded-md bg-indigo-500 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-400 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                type="button"
                @click="sendRequest"
            >
                Try {{ method }} request
            </button>
            <button
                class="rounded-md bg-indigo-700 px-3 py-2 text-sm font-semibold text-gray-200 shadow-sm hover:bg-indigo-400 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500"
                type="button"
                @click="toCurl"
            >
                View as curl
            </button>
        </span>
        <div v-if="loading">Loading...</div>
        <div v-if="result">
            Result:
            <LazyMonacoEditor
                v-if="result"
                :lang="resultLang"
                :model-value="result"
                :options="{
                    theme: 'vs-dark',
                }"
                class="h-44"
            >
                Loading result...
            </LazyMonacoEditor>
        </div>
        <div v-if="requestSendError">
            Error:
            {{ requestSendError }}
        </div>
    </div>-->
</template>

<script lang="ts" setup>
/*import { Ref } from "@vue/reactivity";
import fetchToCurl from "fetch-to-curl";

export interface Props {
    method?: string;
    url: string;
    description?: string;
    headers?: Map<string, string> | object;
    queryParam?: Map<string, string> | object;
    pathParam?: Map<string, string> | object;
    body?: string | object;
}

const props = withDefaults(defineProps<Props>(), {
    method: "GET",
    description: undefined,
    headers: new Map<string, string>(),
    queryParam: new Map<string, string>(),
    pathParam: new Map<string, string>(),
    body: undefined,
});

const emit = defineEmits(["update:modelValue"]);

const bodyString = computed(() => {
    if (props.body instanceof String) {
        return props.body;
    } else if (props.body instanceof Object) {
        return JSON.stringify(props.body, null, 2);
    } else {
        return undefined;
    }
});

const headersMap = computed(() => {
    if (props.headers instanceof Map) {
        return props.headers;
    } else if (props.headers instanceof Object) {
        return new Map(Object.entries(props.headers));
    } else {
        return undefined;
    }
});

const queryParamMap = computed(() => {
    if (props.queryParam instanceof Map) {
        return props.queryParam;
    } else if (props.queryParam instanceof Object) {
        return new Map(Object.entries(props.queryParam));
    } else {
        return undefined;
    }
});

const pathParamMap = computed(() => {
    if (props.pathParam instanceof Map) {
        return props.pathParam;
    } else if (props.pathParam instanceof Object) {
        return new Map(Object.entries(props.pathParam));
    } else {
        return undefined;
    }
});

const loading = ref(false);
const result: Ref<string | undefined> = ref(undefined);
const resultLang: Ref<string | undefined> = ref(undefined);

const requestSendError: Ref<string | undefined> = ref(undefined);

async function sendRequest() {
    requestSendError.value = undefined;
    loading.value = true;
    try {
        const fetched = await fetch(props.url, {
            method: props.method,
        });
        console.log("Fetched: ", fetched);

        const contentType = fetched.headers.get("Content-Type")!!.toLowerCase();

        if (contentType == "application/json") {
            resultLang.value = "json";
        } /!*if (contentType == "text/plain")*!/ else {
            resultLang.value = "text";
        }
        result.value = await fetched.text();
    } catch (error) {
        requestSendError.value = error;
    }
    loading.value = false;
}

function toCurl() {
    //<div v-for="[headerKey, heIaderValue] in headersMap" v-if="headersMap && headersMap.size >= 1";
    headersMap.value?.entries;

    const fetched = fetchToCurl(props.url, {
        method: props.method,
    });
    window.alert(fetched);
}*/
</script>
