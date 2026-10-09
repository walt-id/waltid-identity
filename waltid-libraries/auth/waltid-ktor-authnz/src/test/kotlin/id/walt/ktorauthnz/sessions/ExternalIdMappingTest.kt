package id.walt.ktorauthnz.sessions

import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.OIDC
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * External ids (the OIDC `sid`) are removed with the sessions they point at, however those end. The Valkey case runs
 * only with `VALKEY_TEST_PORT` set (e.g. a throwaway `valkey-server --port 16390 --save ""`).
 */
class ExternalIdMappingTest {

    private val namespace = OIDC.OIDC_SESSION_NAMESPACE
    private val valkeyPort = System.getenv("VALKEY_TEST_PORT")?.toIntOrNull()

    private suspend fun session(account: String) = SessionManager.newSession(AuthFlow(method = "userpass", success = true), persist = false)
        .copy(status = AuthSessionStatus.SUCCESS, flows = null, accountId = account)

    private fun key() = Uuid.random().toString()

    private suspend fun SessionStore.checkCleanup() {
        val account = key()
        val revoked = session(account)
        val dropped = session(account)
        storeSession(revoked); storeSession(dropped)
        val revokedSid = key(); val droppedSid = key()
        storeExternalIdMapping(namespace, revokedSid, revoked.id)
        storeExternalIdMapping(namespace, droppedSid, dropped.id)

        dropSession(dropped.id)
        assertNull(resolveExternalIdMapping(namespace, droppedSid), "a dropped session's sid is gone")

        invalidateAllSessionsForAccount(account)
        assertNull(resolveExternalIdMapping(namespace, revokedSid), "a revoked session's sid is gone")

        // A sid mapped to a newer session keeps that mapping when the older session goes.
        val older = session(account); val newer = session(account)
        storeSession(older); storeSession(newer)
        val sid = key()
        storeExternalIdMapping(namespace, sid, older.id)
        storeExternalIdMapping(namespace, sid, newer.id)
        dropSession(older.id)
        assertEquals(newer.id, resolveExternalIdMapping(namespace, sid))
    }

    @Test
    fun `in memory`() = runBlocking { InMemorySessionStore().checkCleanup() }

    @Test
    fun `in Valkey, where mappings also expire like sessions`() = runBlocking {
        val port = valkeyPort ?: return@runBlocking println("ExternalIdMappingTest: Valkey case skipped, VALKEY_TEST_PORT is not set")
        ValkeySessionStore(null, "127.0.0.1", port, null, null).apply { tryConnect() }.checkCleanup()

        val shortLived = ValkeySessionStore(null, "127.0.0.1", port, null, null, expiration = 1.seconds)
        val sid = key()
        shortLived.storeExternalIdMapping(namespace, sid, key())
        Thread.sleep(2_200)
        assertNull(shortLived.resolveExternalIdMapping(namespace, sid))
    }
}
