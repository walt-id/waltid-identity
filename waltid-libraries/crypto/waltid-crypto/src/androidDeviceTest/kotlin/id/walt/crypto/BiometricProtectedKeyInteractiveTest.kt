package id.walt.crypto

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Minimal host for Signum's BiometricPrompt CryptoObject flow. */
class BiometricTestActivity : FragmentActivity()

/**
 * Interactive device coverage for the protected-key acceptance path.
 *
 * Run these tests manually on an emulator or device with a strong biometric enrolled. The operator
 * must authenticate or cancel the operating-system prompt as described by each test; they are
 * ignored in unattended CI so no test harness can silently substitute device credentials.
 */
@RunWith(AndroidJUnit4::class)
class BiometricProtectedKeyInteractiveTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(BiometricTestActivity::class.java)

    private val aliases = mutableListOf<String>()

    @After
    fun cleanup() = runTest {
        aliases.forEach { alias -> runCatching { AndroidKey.Platform.delete(alias) } }
    }

    @Ignore("Interactive: authenticate the strong-biometric prompt twice on an enrolled device")
    @Test
    fun protectedP256PromptsForEverySigningOperationAndVerifies() = runTest {
        val activity = activityRule.scenario.withActivity()
        val alias = "test-biometric-every-use-${System.currentTimeMillis()}".also(aliases::add)
        val key = AndroidKey.Platform.create(
            AndroidKey.Options(
                kid = alias,
                keyType = KeyType.secp256r1,
                keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                interactionContextProvider = { activity },
            )
        )
        val plaintext = "authorize every key use".encodeToByteArray()

        val first = key.signRaw(plaintext)
        val second = key.signRaw(plaintext)

        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
        assertTrue(key.verifyRaw(first, plaintext).isSuccess)
        assertTrue(key.verifyRaw(second, plaintext).isSuccess)
    }

    @Ignore("Interactive: cancel the strong-biometric prompt on an enrolled device")
    @Test
    fun cancelledPromptProducesNoSignatureAndStableFailure() = runTest {
        val activity = activityRule.scenario.withActivity()
        val alias = "test-biometric-cancel-${System.currentTimeMillis()}".also(aliases::add)
        val key = AndroidKey.Platform.create(
            AndroidKey.Options(
                kid = alias,
                keyType = KeyType.secp256r1,
                keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                interactionContextProvider = { activity },
            )
        )

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            key.signRaw("must not be signed".encodeToByteArray())
        }

        assertEquals(KeyUseAuthorizationFailure.AuthorizationFailed, failure.failure)
    }

    private fun <A : FragmentActivity> androidx.test.core.app.ActivityScenario<A>.withActivity(): A {
        lateinit var activity: A
        onActivity { activity = it }
        return activity
    }
}
