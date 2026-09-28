<script setup lang="ts">
import type { ProfileCard } from "~/utils/profileCards";

defineProps<{
  cards: ProfileCard[];
}>();

const emit = defineEmits<{
  select: [profileId: string];
}>();
</script>

<template>
  <section>
    <h2 class="text-lg font-semibold mb-1">Choose a credential</h2>
    <p class="text-sm text-[--color-text-muted] mb-3">
      Select a credential. Issuance and verification options appear after the
      card enlarges.
    </p>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <button
        v-for="card in cards"
        :key="card.profileId"
        type="button"
        class="group relative w-full overflow-hidden rounded-2xl shadow-sm ring-1 ring-black/5 transition duration-150 hover:-translate-y-0.5 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900"
        :style="{ backgroundColor: card.backgroundColor }"
        :aria-label="card.name"
        @click="emit('select', card.profileId)"
      >
        <span class="block w-full" style="aspect-ratio: 1.586" />
        <img
          v-if="card.backgroundImageUri"
          :src="card.backgroundImageUri"
          :alt="card.name"
          class="absolute inset-0 h-full w-full object-cover"
        />
        <span
          v-else
          class="absolute inset-0 flex items-end p-4 text-white font-semibold"
        >
          {{ card.name }}
        </span>
      </button>
    </div>
  </section>
</template>
