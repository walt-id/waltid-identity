package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DigitalCredentialActivityModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private fun model(): Request = ViewModelProvider(owner, viewModelFactory { initializer { Request() } })[Request::class.java]

    @Test fun recreatedOwnerReusesPreparationAndDeliversOnlyOnePlatformResult() = runTest(dispatcher) {
        val first = model()
        first.start(Intent(), restored = false); runCurrent()
        val afterRotation = model()
        afterRotation.start(Intent(), restored = true)
        assertSame(first, afterRotation)
        assertEquals(1, first.preparations)
        first.gate.complete(Unit); runCurrent()
        first.respond(); first.respond()
        assertEquals(Activity.RESULT_OK, afterRotation.takeResult()?.code)
        assertNull(first.takeResult())
        store.clear(); runCurrent()
        assertEquals(1, first.releases)
    }

    @Test fun processRestorationDoesNotReplayAnUncertainRequest() = runTest(dispatcher) {
        val model = model()
        model.start(Intent(), restored = true); runCurrent()
        assertEquals(0, model.preparations)
        assertTrue(model.failure.orEmpty().contains("interrupted"))
        store.clear(); runCurrent()
    }

    @Test fun cancelledPreparationIsCleanedUpWithoutFailureOrLateCompletion() = runTest(dispatcher) {
        val model = model()
        model.start(Intent(), restored = false); runCurrent()
        model.back()
        assertEquals(Activity.RESULT_CANCELED, model.takeResult()?.code)
        store.clear(); runCurrent()
        model.gate.complete(Unit); runCurrent()
        assertEquals(1, model.releases)
        assertFalse(model.prepared)
        assertNull(model.failure)
        model.respond()
        assertNull(model.takeResult())
    }

    private class Request : DigitalCredentialActivityModel() {
        var preparations = 0
        var releases = 0
        var failure: String? = null
        var prepared = false
        val gate = CompletableDeferred<Unit>()
        override suspend fun prepare(intent: Intent) {
            preparations++
            withContext(NonCancellable) { gate.await() }
            currentCoroutineContext().ensureActive()
            prepared = true
        }
        fun respond() { finish(Intent()) }
        fun back() { finish() }
        override fun showFailure(cause: Exception) { failure = cause.message }
        override suspend fun releaseRequest() { releases++ }
    }
}
