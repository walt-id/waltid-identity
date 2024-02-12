<template>
    <main>



        <div class="mt-2 flex-shrink">
            <div class="font-semibold text-lg">
                <Icon class="h-5 w-5" name="carbon:prompt-template" />
                Choose a template:
            </div>
            <CredentialTemplateList :actions="actions" class="overflow-y-scroll" />
        </div>
    </main>
</template>

<script lang="ts" setup>

// /openid4vc/policy-list

const actions = [
    {
        name: "Add to request",
        icon: "carbon:list-checked",
        action: async (template: string) => {
            credentials.push({
                id: ++idx,
                name: template,
                policies: null
            });
        }
    }
];
let idx = 0;

const vpPolicies: any[] = reactive<any[]>([])
const globalVcPolicies: any[] = reactive<any[]>([])

type VerifyCredential = {
    id: number,
    name: string,
    policies: any,
    // edited: boolean,
    // status: string
}

const credentials: VerifyCredential[] = reactive([]);

useHead({
    title: "Verification | walt.id Portal"
})
</script>
