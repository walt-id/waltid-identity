<script setup lang="ts">
import type { useIssuerSession } from "~/composables/useIssuerSession";
import type { useVerifierSession } from "~/composables/useVerifierSession";
import type { useProfiles } from "~/composables/useProfiles";
import {
  buildCardsFromConfigurations,
  buildProfileCards,
  type ProfileCard,
} from "~/utils/profileCards";
import type { IssuerCredentialConfiguration } from "~/composables/useIssuerMetadata";
import type { IssuerProfile } from "~/composables/useProfiles";

type SimpleAction = "issue" | "verify";

const selectedProfileId = defineModel<string | null>("profileId", {
  required: true,
});
const action = defineModel<SimpleAction>("action", { required: true });

const props = defineProps<{
  issuerSession: ReturnType<typeof useIssuerSession>;
  verifierSession: ReturnType<typeof useVerifierSession>;
  profiles: ReturnType<typeof useProfiles>;
}>();

const { walletId } = useSelectedWallet();
const cards = ref<ProfileCard[]>([]);
const cardsLoading = ref(false);
const cardsError = ref<string | null>(null);

interface IssuerMetadataResponse {
  issuerKind: "community" | "enterprise";
  credentialIssuerMetadata?: {
    credential_configurations_supported?: Record<string, IssuerCredentialConfiguration>;
  };
  profiles?: IssuerProfile[];
}

async function loadCards() {
  cardsLoading.value = true;
  cardsError.value = null;
  try {
    const response = await fetch(
      `/api/issuer-metadata?walletId=${encodeURIComponent(walletId.value)}`,
      { headers: { accept: "application/json" } },
    );
    if (!response.ok) {
      const text = await response.text();
      let message = text || `HTTP ${response.status}`;
      try {
        const parsed = JSON.parse(text) as { statusMessage?: string; message?: string };
        message = parsed.statusMessage || parsed.message || message;
      } catch {
        // Response is already plain text.
      }
      throw new Error(message);
    }
    const data = (await response.json()) as IssuerMetadataResponse;
    const configurations =
      data.credentialIssuerMetadata?.credential_configurations_supported ?? {};
    const profiles = Array.isArray(data.profiles) ? data.profiles : [];
    cards.value =
      data.issuerKind === "community" && profiles.length > 0
        ? buildProfileCards(profiles, (id) => (id ? configurations[id] : undefined))
        : buildCardsFromConfigurations(configurations);
  } catch (error) {
    cards.value = [];
    cardsError.value =
      error instanceof Error ? error.message : "Failed to load issuer metadata";
  } finally {
    cardsLoading.value = false;
  }
}

watch(walletId, () => {
  selectedProfileId.value = null;
  void loadCards();
}, { immediate: true });

const profileCards = computed(() => cards.value);

const selectedCard = computed(
  () =>
    profileCards.value.find(
      (card) => card.profileId === selectedProfileId.value,
    ) ?? null,
);

function selectCard(profileId: string) {
  selectedProfileId.value = profileId;
}

function clearSelection() {
  selectedProfileId.value = null;
}
</script>

<template>
  <div class="grid gap-5">
    <div
      v-if="cardsLoading"
      class="text-sm text-[--color-text-muted]"
    >
      Loading credential profiles…
    </div>
    <div
      v-else-if="cardsError"
      class="text-sm text-red-600"
    >
      {{ cardsError }}
    </div>

    <SimpleCredentialGrid
      v-else-if="!selectedCard"
      :cards="profileCards"
      @select="selectCard"
    />

    <template v-else>
      <div>
        <button
          type="button"
          class="text-sm font-medium text-[--color-text-muted] hover:text-[--color-text] mb-3"
          @click="clearSelection"
        >
          Back to credentials
        </button>

        <div class="lg:flex lg:items-start lg:gap-6">
          <div
            class="relative w-full overflow-hidden rounded-2xl shadow-sm ring-1 ring-black/5 lg:w-[240px] lg:shrink-0"
            :style="{ backgroundColor: selectedCard.backgroundColor }"
          >
            <span class="block w-full" style="aspect-ratio: 1.586" />
            <img
              v-if="selectedCard.backgroundImageUri"
              :src="selectedCard.backgroundImageUri"
              :alt="selectedCard.name"
              class="absolute inset-0 h-full w-full object-cover"
            />
          </div>

          <div class="mt-4 lg:mt-0 lg:min-w-0 lg:flex-1">
              <h2 class="text-lg font-semibold">{{ selectedCard.name }}</h2>
              <p
                v-if="selectedCard.description"
                class="text-sm text-[--color-text-muted] mt-1"
              >
                {{ selectedCard.description }}
              </p>

              <section class="mt-4">
                <label class="form-label">Action</label>
                <div
                  class="inline-flex rounded-lg border border-[--color-border-strong] bg-white p-1"
                >
                  <button
                    type="button"
                    class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
                    :class="
                      action === 'issue'
                        ? 'bg-slate-900 text-white'
                        : 'text-[--color-text-muted] hover:text-[--color-text]'
                    "
                    @click="action = 'issue'"
                  >
                    Issue
                  </button>
                  <button
                    type="button"
                    class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
                    :class="
                      action === 'verify'
                        ? 'bg-slate-900 text-white'
                        : 'text-[--color-text-muted] hover:text-[--color-text]'
                    "
                    @click="action = 'verify'"
                  >
                    Verify
                  </button>
                </div>
              </section>
            </div>
          </div>
        </div>

      <KeepAlive>
        <SimpleIssueEditor
          v-if="action === 'issue'"
          embedded
          :card="selectedCard"
          :profile-id="selectedCard.profileId"
          :session="issuerSession"
          :profiles="profiles"
        />
        <SimpleVerifyEditor
          v-else
          embedded
          :card="selectedCard"
          :profile-id="selectedCard.profileId"
          :session="verifierSession"
          :profiles="profiles"
        />
      </KeepAlive>
    </template>
  </div>
</template>
