<script setup lang="ts">
const config = useRuntimeConfig()
const issuerBase = config.public.issuerBase as string
const verifierBase = config.public.verifierBase as string

const activeTab = ref<'issue' | 'verify'>('issue')

const issuerSwagger = useSwaggerExamples(issuerBase, '/issuer2/credential-offers')
const issuerSession = useIssuerSession(issuerBase)
const issuerJson = ref('')
const issuerSelectedIndex = ref(0)

const verifierSwagger = useSwaggerExamples(verifierBase, '/verification-session/create')
const verifierSession = useVerifierSession(verifierBase)
const verifierJson = ref('')
const verifierSelectedIndex = ref(0)

watch(issuerSwagger.examples, (list) => {
  if (list.length > 0 && !issuerJson.value) {
    issuerJson.value = JSON.stringify(list[0].payload, null, 2)
  }
})
watch(verifierSwagger.examples, (list) => {
  if (list.length > 0 && !verifierJson.value) {
    verifierJson.value = JSON.stringify(list[0].payload, null, 2)
  }
})

onMounted(() => {
  issuerSwagger.load()
  verifierSwagger.load()
})

const activeSession = computed(() => activeTab.value === 'issue' ? issuerSession : verifierSession)
const hasResult = computed(() =>
  activeTab.value === 'issue' ? !!issuerSession.result.value : !!verifierSession.result.value
)
</script>

<template>
  <main class="max-w-[1100px] mx-auto px-5 pt-8 pb-12">
    <header class="flex items-center gap-4 flex-wrap mb-2">
      <img src="/waltid-logo.svg" alt="walt.id" class="h-[60px] w-auto" />
      <h1 class="text-3xl font-bold m-0">Demo Portal</h1>
    </header>

    <p class="text-[--color-text-secondary] mb-6">
      Issue and verify digital credentials using the issuer2 &amp; verifier2 APIs.
    </p>

    <div class="flex gap-5 items-stretch mb-5">
      <!-- Left card: editor -->
      <div class="card flex-1 min-w-0 flex flex-col">
        <div class="flex border-b border-[--color-border]">
          <button
            class="px-5 py-3 font-semibold transition-colors relative"
            :class="activeTab === 'issue'
              ? 'text-[--color-accent] after:absolute after:bottom-0 after:left-0 after:right-0 after:h-[2px] after:bg-[--color-accent]'
              : 'text-[--color-text-muted] hover:text-[--color-text-secondary]'"
            @click="activeTab = 'issue'"
          >
            Issue
          </button>
          <button
            class="px-5 py-3 font-semibold transition-colors relative"
            :class="activeTab === 'verify'
              ? 'text-[--color-accent] after:absolute after:bottom-0 after:left-0 after:right-0 after:h-[2px] after:bg-[--color-accent]'
              : 'text-[--color-text-muted] hover:text-[--color-text-secondary]'"
            @click="activeTab = 'verify'"
          >
            Verify
          </button>
        </div>

        <div class="p-5 flex-1">
          <IssueEditor
            v-if="activeTab === 'issue'"
            v-model:json="issuerJson"
            v-model:selected-index="issuerSelectedIndex"
            :swagger="issuerSwagger"
            :session="issuerSession"
          />
          <VerifyEditor
            v-else
            v-model:json="verifierJson"
            v-model:selected-index="verifierSelectedIndex"
            :swagger="verifierSwagger"
            :session="verifierSession"
          />
        </div>
      </div>

      <!-- Right card: QR + buttons -->
      <div class="card w-[340px] shrink-0 p-5 flex flex-col justify-center min-h-[340px]">
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
