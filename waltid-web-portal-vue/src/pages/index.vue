<template>
    <div>

        <CredentialTemplateList :actions="actions"></CredentialTemplateList>
    </div>
</template>

<script lang="ts" setup>
// import { EnvelopeIcon, PhoneIcon } from '@heroicons/vue/20/solid'

const actions = [
    {
        name: "Issue",
        icon: "heroicons:check-badge",
        action: (template: string) => {
            navigateTo({
                path: "/issuance",
                query: {
                    credential: template
                }
            })
        }
    },
    {
        name: "Verify",
        icon: "heroicons:clipboard-document-list",
        action: (template: string) => {
            navigateTo({
                path: "/verification",
                query: {
                    credential: template
                }
            })
        }
    }
];

useHead({
    title: "walt.id Portal"
})
</script>
