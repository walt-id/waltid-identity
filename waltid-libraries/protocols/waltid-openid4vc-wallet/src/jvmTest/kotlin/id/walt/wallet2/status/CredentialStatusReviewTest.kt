package id.walt.wallet2.status

import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusTestIssuer
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialStatusReviewTest {
    @Test
    fun callerMapMutationCannotAdoptAnUnrenderedWarning() = runTest {
        checkMutation { _, revisions, current -> revisions["resident"] = current }
    }

    @Test
    fun callerSetMutationCannotRemoveTheReviewedCredential() = runTest {
        checkMutation { selected, _, _ -> selected.clear() }
    }

    private suspend fun TestScope.checkMutation(mutate: (MutableSet<String>, MutableMap<String, String>, String) -> Unit) {
        val issuer = StatusTestIssuer.create()
        val store = InMemoryCredentialStore().apply { addCredential(StoredCredential("resident", issuer.credential())) }
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var value = 0u
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond(issuer.list(value), headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt")) }
        }))
        val service = WalletCredentialStatusService(Wallet("wallet", credentialStores = listOf(store)), client,
            now = { at }, trustPolicyId = "test-v1", parentScope = backgroundScope)
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val initial = service.snapshot().credentials.single().presentation
            assertEquals(CredentialStatusKind.CHECKED, initial.kind)
            val revisions = mutableMapOf("resident" to initial.advisoryRevision)
            val selected = mutableSetOf("resident")
            val guard = service.reviewGuard(selected, revisions)
            guard.check()
            at += 1.seconds
            value = 1u
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val changed = service.snapshot().credentials.single().presentation
            assertEquals(CredentialStatusKind.INVALID, changed.kind)
            assertNotEquals(initial.advisoryRevision, changed.advisoryRevision)
            mutate(selected, revisions, changed.advisoryRevision)
            assertFailsWith<CredentialStatusReviewChangedException> { guard.check() }
        } finally { service.close(); client.close() }
    }
}
