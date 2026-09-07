package id.walt.mdoc.proximity.mobile

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class WifiAwareNativeResourceTest {
    @Test fun callbackOwnershipSurvivesEveryCancellationBoundary() = runTest {
        for (boundary in listOf("before-callback", "before-resume", "after-resume")) {
            val owner = WifiAwareNativeResource<Resource>()
            val native = Resource()
            val waiter = async { owner.await() }
            runCurrent()
            when (boundary) {
                "before-callback" -> { waiter.cancelAndJoin(); owner.install(native) }
                "before-resume" -> { owner.install(native); waiter.cancelAndJoin() }
                else -> { owner.install(native); assertSame(native, waiter.await()); owner.close() }
            }
            owner.close()
            assertEquals(1, native.closes, boundary)
        }
    }

    @Test fun closeBeforeRegistrationReturnsDisposesTheLateRegistration() = runTest {
        val owner = WifiAwareNativeResource<Resource>()
        val native = Resource()
        // requestNetwork/registerReceiver can return after an onLost/state callback closes the owner.
        owner.close()
        owner.install(native)
        assertFailsWith<CancellationException> { owner.await() }
        assertEquals(1, native.closes)
    }

    @Test fun failedCallbackRejectsLateSuccessAndFreshOwnerCanSucceed() = runTest {
        val failed = WifiAwareNativeResource<Resource>()
        val failure = IllegalStateException("attach failed")
        failed.fail(failure)
        val late = Resource()
        failed.install(late)
        assertEquals(failure.message, assertFailsWith<IllegalStateException> { failed.await() }.message)
        assertEquals(1, late.closes)
        val fresh = WifiAwareNativeResource<Resource>()
        val native = Resource()
        fresh.install(native)
        assertSame(native, fresh.await())
        fresh.close()
        assertEquals(1, native.closes)
    }

    @Test fun duplicateCallbackDisposesOnlyTheUnownedResource() = runTest {
        val owner = WifiAwareNativeResource<Resource>()
        val selected = Resource()
        val duplicate = Resource()
        owner.install(selected)
        owner.install(duplicate)
        assertSame(selected, owner.await())
        assertEquals(0, selected.closes)
        assertEquals(1, duplicate.closes)
        owner.close()
        assertEquals(1, selected.closes)
    }

    private class Resource : Closeable {
        var closes = 0
        override fun close() { closes++ }
    }
}
