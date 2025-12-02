<template>
  <div>
    <div class="fixed p-4 bg-slate-900 bg-opacity-97 w-full z-20 border-b-1 border-slate-700">
      <div class="flex flex-row justify-between gap-5">
        <div class="flex flex-row gap-4 items-center">
          <div class="flex h-5 shrink-0 items-center">
            <img alt="walt.id" class="h-7 w-auto" src="/logo.png" />
          </div>
          <div class="flex flex-col">
            <span class="text-gray-50 font-bold text-2xl">walt.id
              <span class="text-primary-400 text-1xl">CREDENTIALS</span></span>
          </div>
        </div>
        <div class="flex flex-row">
          <nav>
            <ul class="hidden lg:flex flex-row gap-5 text-gray-50 font-semibold">
              <NuxtLink v-for="item in headerNavigation" :href="item.url" :key="item.url" target="_blank">
                {{ item.title }}
              </NuxtLink>
            </ul>
          </nav>
          <div class="border-l border-slate-800 mx-5"></div>
          <div class="flex flex-row">
            <NuxtLink href="https://github.com/walt-id" target="_blank">GitHub</NuxtLink>
          </div>
        </div>
      </div>
      <hr class="lg:hidden mt-2 border-0.5 border-slate-700" />
      <!--        Mobile Header Part -->
      <div class="lg:hidden cursor-pointer mt-2" @click="toggleMobileMenu">
        <Bars3Icon class="h-6" />
      </div>
    </div>
    <!--        Content Below Header -->
    <div
      class="relative w-full flex flex-col sm:flex-row flex-wrap sm:flex-nowrap py-4 flex-grow max-w-[1500px] mx-auto min-h-screen">
      <!-- Desktop Nav Left -->
      <div class="relative top-22 hidden lg:block relative w-fixed lg:w-3/12 flex-shrink flex-grow-0">
        <div class="fixed w-3/12 max-w-[370px] h-full">
          <div class="hide-nav flex flex-col overflow-y-scroll h-10/12">
            <Navigation />
          </div>
        </div>
      </div>
      <!--  CONTENT -->
      <main role="main" class="relative top-25 lg:top-22 flex-grow pt-1 px-3 lg:w-7/12 w-full px-2">
        <slot />
        <hr class="border border-0.1 border-slate-700" />
        <div class="pb-8">
          <ContentQuery :path="route.path" find="surround" v-slot="{ data }">
            <div v-if="data" class="text-sm md:text-md">
              <div :class="!data[0] ? 'justify-right' : 'justify-between'" class="flex mt-7">
                <NuxtLink v-if="data[0]" :to="data[0]._path"
                  class="flex items-center gap-2 font-bold hover:text-primary-300 text-left">
                  <ChevronLeftIcon class="h-4" />
                  {{ data[0].title }}
                </NuxtLink>

                <NuxtLink v-if="data[1]" :to="data[1]._path"
                  class="flex items-center gap-2 font-bold hover:text-primary-300 text-right">
                  {{ data[1].title }}
                  <ChevronRightIcon class="h-4" />
                </NuxtLink>
              </div>
            </div>
          </ContentQuery>
        </div>
      </main>
      <div class="hidden lg:block relative top-18 w-fixed w-2/12 flex-shrink flex-grow-0 px-2">
        <!-- fixed-width -->
        <div class="flex fixed max-w-[320px] sm:flex-col px-2 mt-1">
          <p class="font-semibold text-gray-50">On this page</p>
          <TableOfContents />
        </div>
      </div>
      <!--        Mobile Menu-->
      <div v-if="showMobileMenu" class="absolute fixed top-0 w-full left-0 z-200 bg-slate-700 h-full bg-opacity-95"
        @click="toggleMobileMenu">
        <div class="relative bg-slate-900 w-8/12 h-full" @click.stop="">
          <div class="absolute top-3 right-3 cursor-pointer">
            <XMarkIcon class="h-5" @click="toggleMobileMenu" />
          </div>
          <div class="flex flex-col h-full overflow-scroll">
            <div class="mt-10">
              <Navigation @click="toggleMobileMenu" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <!--<div class="grid grid-rows-[auto_1fr] h-screen gap-4">-->

  <!--  &lt;!&ndash; Header &ndash;&gt;-->
  <!--  <div class="bg-blue-500">-->
  <!--    Header-->
  <!--  </div>-->

  <!--  &lt;!&ndash; Grid for content &ndash;&gt;-->
  <!--  <div class="grid grid-cols-[2fr_4fr] gap-4 max-w-[1500px] m-auto">-->

  <!--    &lt;!&ndash; Menu &ndash;&gt;-->
  <!--    <div class="bg-blue-300 p-20">-->
  <!--      Menu-->
  <!--    </div>-->

  <!--    &lt;!&ndash; Main and Right Sections &ndash;&gt;-->
  <!--    <div class="grid grid-cols-[6fr_2fr] gap-4">-->

  <!--      &lt;!&ndash; Main Content &ndash;&gt;-->
  <!--      <div class="bg-blue-200">-->
  <!--        <slot />-->
  <!--      </div>-->

  <!--      &lt;!&ndash; Right Sidebar &ndash;&gt;-->
  <!--      <div class="bg-blue-400">-->
  <!--        Right-->
  <!--      </div>-->
  <!--    </div>-->
  <!--  </div>-->

  <!--</div>-->
</template>

<script setup lang="ts">
import { ref } from "vue";
import {
  XMarkIcon,
  Bars3Icon,
  ChevronRightIcon,
  ChevronLeftIcon,
} from "@heroicons/vue/24/outline";
import Navigation from "~/components/navigation/Navigation.vue";

interface NavigationItem {
  title: string;
  _path: string;
  children?: NavigationItem[];
}

const route = useRoute();
const showMobileMenu = ref(false);

function toggleMobileMenu() {
  showMobileMenu.value = !showMobileMenu.value;
}

// function filterNavigation(navigationItems: NavigationItem[]) {
//   const map: Record<string, NavigationItem> = {};
//   for (const item of navigationItems) {
//     if (!map[item._path]) {
//       map[item._path] = item;
//     }
//   }
//   console.log(Object.values(map));
//   return Object.values(map);
// }

const headerNavigation: { title: string; url: string }[] = [
  {
    title: "Homepage",
    url: "https://walt.id",
  },
  {
    title: "Community",
    url: "https://walt.id/discord"
  },
  {
    title: "Docs",
    url: "https://docs.oss.walt.id/",
  },
];
</script>

<style scoped>
.hide-nav {
  overflow: hidden;
}
</style>