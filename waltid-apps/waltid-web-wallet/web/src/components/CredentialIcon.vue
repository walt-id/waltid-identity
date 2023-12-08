<template>
    <AcademicCapIcon v-if="credentialType === 'VerifiableDiploma'" />
    <IdentificationIcon v-else-if="credentialType === 'VerifiableId' || credentialType === 'Europass'" />
    <HomeIcon v-else-if="credentialType === 'ProofOfResidence'" />
    <UserGroupIcon v-else-if="credentialType === 'ParticipantCredential'" />
    <CheckBadgeIcon v-else-if="credentialType === 'OpenBadgeCredential'" />
    <EyeDropperIcon v-else-if="credentialType === 'VerifiableVaccinationCertificate'" />
</template>

<script setup>
import { AcademicCapIcon, CheckBadgeIcon, EyeDropperIcon, HomeIcon, IdentificationIcon, UserGroupIcon } from "@heroicons/vue/24/outline";

const props = defineProps(["credentialType"]);
</script>

<style scoped></style>
