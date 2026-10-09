package id.walt.walletdemo.compose.logic

import android.content.SharedPreferences
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDemoPinStoreCommitTest {
    @Test fun failedReplacementRestoresTheOldVerifierInMemoryAndAfterRecreation() = runTest {
        val preferences = failingPreferences()
        val store = createAndroidDemoPinStore(preferences, "wallet")
        store.setPin("1234")
        preferences.failNextCommit = true
        assertFailsWith<IllegalStateException> { store.setPin("5678") }
        assertTrue(store.verifyPin("1234"))
        assertFalse(store.verifyPin("5678"))
        assertTrue(createAndroidDemoPinStore(preferences.delegate, "wallet").verifyPin("1234"))
    }

    @Test fun failedPreferenceChangeRestoresThePreviousSetting() {
        val preferences = failingPreferences()
        val store = createAndroidDemoPinStore(preferences, "wallet")
        preferences.failNextCommit = true
        assertFailsWith<IllegalStateException> { store.setBiometricUnlockEnabled(true) }
        assertFalse(store.isBiometricUnlockEnabled())
        store.setBiometricSetupPending(true)
        preferences.failNextCommit = true
        assertFailsWith<IllegalStateException> { store.setBiometricSetupPending(false) }
        assertTrue(store.isBiometricSetupPending())
    }

    private fun failingPreferences(): FailingPreferences {
        val context = RuntimeEnvironment.getApplication()
        return FailingPreferences(context.getSharedPreferences("commit-test-${System.nanoTime()}", 0))
    }

    // Reproduce the important failure contract: commit may already have changed memory.
    private class FailingPreferences(val delegate: SharedPreferences) : SharedPreferences by delegate {
        var failNextCommit = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean {
                    val written = editor.commit()
                    if (!failNextCommit) return written
                    failNextCommit = false
                    return false
                }
            }
        }
    }
}
