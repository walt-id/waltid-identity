<template>
  <CenterMain>
    <ContentDoc />
  </CenterMain>
</template>

<script lang="ts" setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";

definePageMeta({
  layout: "default-reduced-nav",
});
</script>

<style scoped>
h1 {
  font-size: 40px;
}
</style>
