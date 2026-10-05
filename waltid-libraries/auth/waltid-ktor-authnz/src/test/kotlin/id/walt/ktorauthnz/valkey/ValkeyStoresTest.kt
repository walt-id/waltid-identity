package id.walt.ktorauthnz.valkey

import id.walt.ktorauthnz.ephemeral.ValkeyExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.sessions.ValkeySessionStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * The Valkey stores against a real Valkey. Runs only with `VALKEY_TEST_PORT` set (e.g. a throwaway
 * `valkey-server --port 16390 --save ""`); skipped otherwise.
 */
class ValkeyStoresTest {

    private val port = System.getenv("VALKEY_TEST_PORT")?.toIntOrNull()

    private fun withValkey(block: suspend () -> Unit) = runBlocking {
        if (port == null) {
            println("ValkeyStoresTest skipped: VALKEY_TEST_PORT is not set")
            return@runBlocking
        }
        block()
    }

    private fun key() = Uuid.random().toString()

    @Test
    fun `expiring store keeps values for their lifetime and counts within a window`() = withValkey {
        val store = ValkeyExpiringStore(null, "127.0.0.1", port, null, null)
        store.tryConnect()

        val k = key()
        store.put(k, "v", 300.milliseconds)
        assertEquals("v", store.get(k))
        Thread.sleep(450)
        assertNull(store.get(k))

        val once = key()
        assertTrue(store.putIfAbsent(once, "used", 1.minutes))
        assertFalse(store.putIfAbsent(once, "used", 1.minutes))

        val counter = key()
        assertEquals(1, store.increment(counter, 400.milliseconds))
        Thread.sleep(250)
        assertEquals(2, store.increment(counter, 400.milliseconds), "later increments do not extend the window")
        Thread.sleep(250)
        assertEquals(1, store.increment(counter, 400.milliseconds), "the window of the first increment ended")
    }

    @Test
    fun `revoking an account's sessions deletes them, and a dropped session leaves its account index`() = withValkey {
        val store = ValkeySessionStore(null, "127.0.0.1", port, null, null)
        store.tryConnect()
        val account = key()
        val flow = AuthFlow(method = "userpass", success = true)
        suspend fun finished() = SessionManager.newSession(flow, persist = false).copy(status = AuthSessionStatus.SUCCESS, flows = null, accountId = account)

        val a = finished(); val b = finished()
        store.storeSession(a); store.storeSession(b)
        store.dropSession(a.id)
        assertNull(store.findSessionById(a.id))
        assertNotNull(store.findSessionById(b.id))

        store.invalidateAllSessionsForAccount(account)
        assertNull(store.findSessionById(b.id))
    }

    @Test
    fun `an account's sessions are listed, without expired ones`() = withValkey {
        val store = ValkeySessionStore(null, "127.0.0.1", port, null, null)
        val account = key()
        val flow = AuthFlow(method = "userpass", success = true)
        val live = SessionManager.newSession(flow, persist = false).copy(status = AuthSessionStatus.SUCCESS, flows = null, accountId = account)
        val ending = live.copy(id = key(), expiration = Clock.System.now() + 300.milliseconds)
        val other = live.copy(id = key(), accountId = key())
        store.storeSession(live); store.storeSession(ending); store.storeSession(other)

        assertEquals(setOf(live.id, ending.id), store.listSessionsForAccount(account).map { it.id }.toSet())
        Thread.sleep(450)
        assertEquals(listOf(live.id), store.listSessionsForAccount(account).map { it.id })
    }

    @Test
    fun `unfinished sessions get the short lifetime`() = withValkey {
        val store = ValkeySessionStore(null, "127.0.0.1", port, null, null, pendingSessionLifetime = 300.milliseconds)
        val flow = AuthFlow(method = "userpass", success = true)
        val pending = SessionManager.newSession(flow, persist = false)
        val finished = pending.copy(id = key(), status = AuthSessionStatus.SUCCESS, flows = null, expiration = Clock.System.now() + 1.minutes)
        store.storeSession(pending)
        store.storeSession(finished)

        Thread.sleep(450)
        assertNull(store.findSessionById(pending.id))
        assertNotNull(store.findSessionById(finished.id))
    }
}
