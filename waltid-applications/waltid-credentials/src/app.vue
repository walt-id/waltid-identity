<template>
  <Html class="" lang="en">

  <Body class="bg-slate-900 text-gray-200 antialiased transition-colors duration-300 font-sans">
    <NuxtLayout class="">
      <NuxtLoadingIndicator />
      <NuxtErrorBoundary>
        <NuxtPage class="h-full mx-auto" />
        <template #error="{ error, clearError }">
          <p>An error occurred: {{ error }}</p>
          <button class="rounded-md bg-gray-700 border border-gray-500 p-1 mt-2" @click="clearError">
            Reset error
          </button>
        </template>
      </NuxtErrorBoundary>
    </NuxtLayout>
  </Body>

  </Html>
</template>

<script lang="ts" setup>
import "@unocss/reset/tailwind-compat.css";
import "uno.css";
</script>
