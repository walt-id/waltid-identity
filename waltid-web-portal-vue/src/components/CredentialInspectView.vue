<template>
    <div>
        <div v-if="loading || credentialData?.pending || credentialMapping?.pending" class="flex items-center gap-2">
            <Spinner class="h-5 w-5"/>
            Loading...
        </div>
        <div v-else>
            <div v-if="credentialData?.data && credentialMapping?.data">
                <div class="">
                    <span class="text-lg font-semibold">Credential template data</span>
                    <ClientOnly>
                        <highlightjs :code="`${JSON.stringify(credentialData?.data, null, 4)}`" language="json" />
                    </ClientOnly>
                </div>

                <hr/>
                <div class="">
                    <span class="text-lg font-semibold">Credential template mapping</span>
                    <ClientOnly>
                        <highlightjs :code="`${JSON.stringify(credentialMapping?.data, null, 4)}`" language="json" />
                    </ClientOnly>
                </div>
            </div>
            <div v-else>
                No credential or mapping data.
            </div>
            <!--            <p><code v-if="credentialData?.data">{{ credentialData?.data }}</code></p>-->
            <!--            <p><code v-if="credentialMapping?.data">{{ credentialMapping?.data }}</code></p>-->
        </div>
    </div>
</template>

<script lang="ts" setup>
import Spinner from "~/components/Spinner.vue";
import type { Ref } from "vue";
import type { _AsyncData } from "#app/composables/asyncData";
import { FetchError } from "ofetch";

const props = defineProps({
    template: {
        type: String,
        required: true
    }
});

const config = useRuntimeConfig()

const loading = ref(false)
const credentialData: Ref<_AsyncData<any, FetchError | null> | null> = ref(null);
const credentialMapping: Ref<_AsyncData<any, FetchError | null> | null> = ref(null);

async function getCredentialData() {
    loading.value = true
    const templateId = props.template;

    credentialData.value = await useFetch<string>(`${config.public.credentialRepository}/api/vc/${templateId}`);
    credentialMapping.value = await useFetch<string>(`${config.public.credentialRepository}/api/mapping/${templateId}`);

    loading.value = false
}


watch(props, async (value, oldValue) => {
    await getCredentialData();
});

getCredentialData();
</script>
