<template>
    <Html :lang="locale" class="h-full">
        <Head>
            <Link :href="logoImg" rel="icon" type="text/xml" />
        </Head>
        <!--<head>
          <link rel="icon" type="text/xml" href="/svg/walt-s.svg">
        </head>-->
        <Body class="bg-white text-gray-800 antialiased transition-colors duration-300 h-full">
            <!-- dark:bg-gray-900 dark:text-gray-200 -->
            <ModalBase />
            <VitePwaManifest />
            <NuxtLoadingIndicator />
<!--            {{ tenant }}-->
            <NuxtLayout class="h-full">
                <NuxtPage class="h-full" />
            </NuxtLayout>
        </Body>
    </Html>
</template>

<script lang="ts" setup>
import { InitApp } from "~/utils/initApp";
import ModalBase from "~/components/modals/ModalBase.vue";
import { $fetch } from "ofetch";
import { useTenant } from "~/composables/tenants";

InitApp();
const locale = useState<string>("locale.i18n");

const tenant = await (useTenant()).value
const name = tenant?.name
const logoImg = tenant?.logoImage
</script>

<style lang="postcss">
/*body {
    @apply bg-gray-50 dark:bg-gray-800;
}

.global-text {
    @apply text-gray-900 dark:text-gray-50;
}
*/
#__nuxt {
    height: 100%;
}
</style>
