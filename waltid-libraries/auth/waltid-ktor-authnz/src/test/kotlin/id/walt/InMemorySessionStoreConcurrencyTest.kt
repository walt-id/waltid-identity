package id.walt

import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class InMemorySessionStoreConcurrencyTest {

    @Test
    fun `concurrent storage and invalidation keep the account index consistent`() = runTest {
        val store = InMemorySessionStore()
        val accountId = "account"

        coroutineScope {
            repeat(500) { index ->
                launch(Dispatchers.Default) {
                    store.storeSession(session("session-$index", accountId))
                }
                launch(Dispatchers.Default) {
                    store.invalidateAllSessionsForAccount(accountId)
                }
            }
        }

        val indexed = store.accountSessions[accountId].orEmpty()
        assertTrue(store.sessions.values.filter { it.accountId == accountId }.all { it.id in indexed })
    }

    @Test
    fun `moving one session between accounts never leaves a stale index`() = runTest {
        val store = InMemorySessionStore()
        val sessionId = "shared-session"

        coroutineScope {
            repeat(500) { index ->
                launch(Dispatchers.Default) {
                    store.storeSession(session(sessionId, "account-${index % 2}"))
                }
            }
        }

        val actualAccount = requireNotNull(store.sessions.getValue(sessionId).accountId)
        assertTrue(store.accountSessions.filterKeys { it != actualAccount }.values.none { sessionId in it })
        assertTrue(sessionId in store.accountSessions.getValue(actualAccount))
    }

    @Test
    fun `external session remapping keeps forward and reverse indexes paired`() = runTest {
        val store = InMemorySessionStore()
        store.storeExternalIdMapping("oidc", "external-1", "session-1")
        store.storeExternalIdMapping("oidc", "external-1", "session-2")

        store.dropExternalIdMappingByInternal("oidc", "session-1")
        assertTrue(store.resolveExternalIdMapping("oidc", "external-1") == "session-2")

        store.storeExternalIdMapping("oidc", "external-2", "session-2")
        assertTrue(store.resolveExternalIdMapping("oidc", "external-1") == null)
        assertTrue(store.resolveExternalIdMapping("oidc", "external-2") == "session-2")
    }

    private fun session(id: String, accountId: String) = AuthSession(
        id = id,
        status = AuthSessionStatus.SUCCESS,
        expiration = Clock.System.now() + 1.hours,
        accountId = accountId,
    )
}
