<template>
    <div>

        <CredentialTemplateList :actions="x"></CredentialTemplateList>
    </div>
</template>

<script lang="ts" setup>
// import { EnvelopeIcon, PhoneIcon } from '@heroicons/vue/20/solid'

const x = [
    {
        name: "Issue",
        icon: "heroicons:check-badge",
        action: (template: string) => {
            alert("ISSUE");
        }
    },
    {
        name: "Verify",
        icon: "heroicons:clipboard-document-list",
        action: (template: string) => {
            alert("Verify");
        }
    }
];

</script>

<style>

</style>
