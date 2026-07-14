<script setup lang="ts">
const config = useRuntimeConfig();
const issuerBase = config.public.issuerBase as string;
const verifierBase = config.public.verifierBase as string;

const activeTab = ref<"issue" | "verify">("issue");
const mode = ref<"simple" | "advanced">("simple");

const ISSUER_DOCS_URL =
  "https://docs.walt.id/community-stack/issuer2/getting-started";
const VERIFIER_DOCS_URL =
  "https://docs.walt.id/community-stack/verifier2/getting-started";

const issuerSwagger = useSwaggerExamples(
  issuerBase,
  "/issuer2/credential-offers",
);
const issuerSession = useIssuerSession(issuerBase);
const issuerProfiles = useProfiles(issuerBase);
const issuerJson = ref("");
const issuerSelectedIndex = ref(0);

const verifierSwagger = useSwaggerExamples(
  verifierBase,
  "/verification-session/create",
);
const verifierSession = useVerifierSession(verifierBase);
const verifierJson = ref("");
const verifierSelectedIndex = ref(0);

watch(issuerSwagger.examples, (list) => {
  if (list.length > 0 && !issuerJson.value) {
    issuerJson.value = JSON.stringify(list[0].payload, null, 2);
  }
});
watch(verifierSwagger.examples, (list) => {
  if (list.length > 0 && !verifierJson.value) {
    verifierJson.value = JSON.stringify(list[0].payload, null, 2);
  }
});

onMounted(() => {
  issuerSwagger.load();
  issuerProfiles.load();
  verifierSwagger.load();
});

const docsUrl = computed(() =>
  activeTab.value === "issue" ? ISSUER_DOCS_URL : VERIFIER_DOCS_URL,
);

const swaggerUrl = computed(() =>
  activeTab.value === "issue" ? issuerBase : verifierBase,
);

const activeSession = computed(() =>
  activeTab.value === "issue" ? issuerSession : verifierSession,
);
const hasResult = computed(() =>
  activeTab.value === "issue"
    ? !!issuerSession.result.value
    : !!verifierSession.result.value,
);
</script>

<template>
  <main class="max-w-[1100px] mx-auto px-5 pt-8 pb-12">
    <header class="flex items-center justify-between gap-4 flex-wrap mb-2">
      <div class="flex items-center gap-4">
        <img src="/waltid-logo.svg" alt="walt.id" class="h-[60px] w-auto" />
        <h1 class="text-3xl font-bold m-0">Demo Portal</h1>
      </div>

      <div
        class="inline-flex rounded-lg border border-[--color-border-strong] bg-white p-1"
      >
        <button
          type="button"
          class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
          :class="
            mode === 'simple'
              ? 'bg-slate-900 text-white'
              : 'text-[--color-text-muted] hover:text-[--color-text]'
          "
          @click="mode = 'simple'"
        >
          Simple
        </button>
        <button
          type="button"
          class="px-3 py-1.5 text-sm font-medium rounded-md transition-colors"
          :class="
            mode === 'advanced'
              ? 'bg-slate-900 text-white'
              : 'text-[--color-text-muted] hover:text-[--color-text]'
          "
          @click="mode = 'advanced'"
        >
          Advanced
        </button>
      </div>
    </header>

    <p class="text-[--color-text-secondary] mb-6">
      Issue and verify digital credentials using the issuer2 &amp; verifier2
      APIs.
    </p>

    <div class="flex gap-5 items-start mb-5">
      <!-- Left card: editor -->
      <div class="card flex-1 min-w-0 flex flex-col">
        <div class="flex items-center border-b border-[--color-border]">
          <button
            class="px-5 py-3 font-semibold transition-colors relative"
            :class="
              activeTab === 'issue'
                ? 'text-[--color-accent] after:absolute after:bottom-0 after:left-0 after:right-0 after:h-[2px] after:bg-[--color-accent]'
                : 'text-[--color-text-muted] hover:text-[--color-text-secondary]'
            "
            @click="activeTab = 'issue'"
          >
            Issue
          </button>
          <button
            class="px-5 py-3 font-semibold transition-colors relative"
            :class="
              activeTab === 'verify'
                ? 'text-[--color-accent] after:absolute after:bottom-0 after:left-0 after:right-0 after:h-[2px] after:bg-[--color-accent]'
                : 'text-[--color-text-muted] hover:text-[--color-text-secondary]'
            "
            @click="activeTab = 'verify'"
          >
            Verify
          </button>

          <a
            :href="docsUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="ml-auto mr-4 inline-flex items-center gap-1.5 text-sm font-medium text-[--color-text-muted] hover:text-[--color-accent] transition-colors"
          >
            Docs
            <svg class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
              <path
                d="M11 3a1 1 0 100 2h2.586l-6.293 6.293a1 1 0 101.414 1.414L15 6.414V9a1 1 0 102 0V4a1 1 0 00-1-1h-5z"
              />
              <path
                d="M5 5a2 2 0 00-2 2v8a2 2 0 002 2h8a2 2 0 002-2v-3a1 1 0 10-2 0v3H5V7h3a1 1 0 000-2H5z"
              />
            </svg>
          </a>

          <a
            :href="swaggerUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="mr-4 inline-flex items-center gap-1.5 text-sm font-medium text-[--color-text-muted] hover:text-[--color-accent] transition-colors"
          >
            Swagger
            <svg class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
              <path
                d="M11 3a1 1 0 100 2h2.586l-6.293 6.293a1 1 0 101.414 1.414L15 6.414V9a1 1 0 102 0V4a1 1 0 00-1-1h-5z"
              />
              <path
                d="M5 5a2 2 0 00-2 2v8a2 2 0 002 2h8a2 2 0 002-2v-3a1 1 0 10-2 0v3H5V7h3a1 1 0 000-2H5z"
              />
            </svg>
          </a>
        </div>

        <div class="p-5 flex-1">
          <KeepAlive>
            <SimpleIssueEditor
              v-if="mode === 'simple' && activeTab === 'issue'"
              :session="issuerSession"
            />
            <SimpleVerifyEditor
              v-else-if="mode === 'simple' && activeTab === 'verify'"
              :session="verifierSession"
            />
          </KeepAlive>
          <IssueEditor
            v-if="mode === 'advanced' && activeTab === 'issue'"
            v-model:json="issuerJson"
            v-model:selected-index="issuerSelectedIndex"
            :swagger="issuerSwagger"
            :session="issuerSession"
            :profiles="issuerProfiles"
          />
          <VerifyEditor
            v-else-if="mode === 'advanced' && activeTab === 'verify'"
            v-model:json="verifierJson"
            v-model:selected-index="verifierSelectedIndex"
            :swagger="verifierSwagger"
            :session="verifierSession"
          />
        </div>
      </div>

      <!-- Right card: QR + buttons -->
      <div
        class="card w-[340px] h-[440px] shrink-0 p-5 flex flex-col justify-center sticky top-5"
      >
        <IssueResult v-if="activeTab === 'issue'" :session="issuerSession" />
        <VerifyResult v-else :session="verifierSession" />
      </div>
    </div>

    <!-- Bottom: event log (full width) -->
    <div v-show="hasResult" class="card p-5">
      <EventLog
        :events="activeSession.sse.events.value"
        :status="activeSession.sse.status.value"
        :is-terminal="activeSession.sse.isTerminal.value"
      />
    </div>
  </main>
</template>
