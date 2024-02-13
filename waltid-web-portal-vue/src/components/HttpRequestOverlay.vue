<template>
    <PageOverlay :is-open="isOpen" description="Below you can find the HTTP request generated with the options you applied."
                 name="HTTP Request Viewer" @close="close"
    >
        <div>
            <p>
                HTTP: <span class="font-semibold">{{ request.method }}</span> <code><span>{{ request.url }}</span></code>
            </p>
            <p>
                Body:
                <ClientOnly>
                    <highlightjs :code="`${JSON.stringify(request.body, null, 4)}`" class="" language="json" />
                </ClientOnly>
            </p>
        </div>

        <hr />

        <p class="font-semibold">As curl request:</p>

        <p>
            <code class="">
                {{ fetchToCurl(request) }}
            </code>
        </p>
    </PageOverlay>
</template>

<script lang="ts" setup>
import fetchToCurl from "fetch-to-curl";
import type { HttpRequestType } from "~/composables/network-request";

const props = defineProps<{
    isOpen: boolean,
    request: HttpRequestType
}>();

const emit = defineEmits(["close"]);

function close() {
    emit("close");
}
</script>
