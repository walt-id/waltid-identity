package id.walt

import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExpiringStoresTest {

    private class TestClock(var now: Instant = Instant.parse("2030-01-01T00:00:00Z")) : Clock {
        override fun now() = now
        fun advance(by: Duration) { now += by }
    }

    @Test
    fun `entries expire after their lifetime`() = runTest {
        val clock = TestClock()
        val store = InMemoryExpiringStore(clock)
        store.put("k", "v", 10.seconds)
        clock.advance(9.seconds)
        assertEquals("v", store.get("k"))
        clock.advance(2.seconds)
        assertNull(store.get("k"))
    }

    @Test
    fun `a single-use value is stored once while it lives`() = runTest {
        val clock = TestClock()
        val store = InMemoryExpiringStore(clock)
        assertTrue(store.putIfAbsent("nonce", "used", 1.minutes))
        assertFalse(store.putIfAbsent("nonce", "used", 1.minutes))
        clock.advance(2.minutes)
        assertTrue(store.putIfAbsent("nonce", "used", 1.minutes), "free again once expired")
    }

    @Test
    fun `a counter's window starts with its first increment`() = runTest {
        val clock = TestClock()
        val store = InMemoryExpiringStore(clock)
        assertEquals(1, store.increment("c", 10.seconds))
        clock.advance(8.seconds)
        assertEquals(2, store.increment("c", 10.seconds), "later increments do not extend the window")
        clock.advance(3.seconds)
        assertEquals(1, store.increment("c", 10.seconds))
    }

    @Test
    fun `unfinished sessions expire, finished ones live until their expiration`() = runTest {
        val clock = TestClock()
        val store = InMemorySessionStore(pendingSessionLifetime = 15.minutes, clock = clock)
        val flow = AuthFlow(method = "userpass", success = true)
        val pending = SessionManager.newSession(flow, persist = false).copy(createdAt = clock.now)
        val finished = pending.copy(id = "finished", status = AuthSessionStatus.SUCCESS, flows = null, expiration = clock.now + 60.minutes)
        store.storeSession(pending)
        store.storeSession(finished)

        clock.advance(16.minutes)
        assertNull(store.findSessionById(pending.id))
        assertNotNull(store.findSessionById("finished"))

        clock.advance(45.minutes)
        assertNull(store.findSessionById("finished"))
    }

    @Test
    fun `an opaque token ends with its flow's expiration`() = runTest {
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        val handler = KtorAuthNzTokenHandler()
        KtorAuthnzManager.tokenHandler = handler
        val session = SessionManager.newSession(AuthFlow(method = "userpass", success = true, expiration = "1s"))
        session.accountId = "acc"
        session.progressFlow(UserPass)
        val token = assertNotNull(session.token)

        assertTrue(handler.validateToken(token))
        Thread.sleep(1200) // real time: the expiration is checked against the system clock
        assertFalse(handler.validateToken(token))
    }
}
