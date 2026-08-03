package id.walt.wallet2.persistence.keys

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PlatformKeyProviderTestActivity : FragmentActivity()

@RunWith(AndroidJUnit4::class)
class AndroidPlatformKeyProviderTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(PlatformKeyProviderTestActivity::class.java)

    @Test
    fun nonePreflightPreservesExistingAlgorithmSupport() = runTest {
        val context = activityRule.scenario.withActivity().applicationContext
        val provider = AndroidPlatformKeyProvider(context, interactionContextProvider = { null })

        KeyType.entries.forEach { keyType ->
            val preflight = provider.preflight(PlatformKeyRequest(keyType, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None))
            val expected = keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES ||
                keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            assertEquals(expected, preflight.supported, "Unexpected None preflight for $keyType")
        }
    }

    @Test
    fun protectedP256WithoutFragmentActivityFailsExplicitly() = runTest {
        val context = activityRule.scenario.withActivity().applicationContext
        val preflight = AndroidPlatformKeyProvider(context, interactionContextProvider = { null }).preflight(
            PlatformKeyRequest(KeyType.secp256r1, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
        )

        assertFalse(preflight.supported)
        assertEquals(KeyUseAuthorizationFailure.InteractionContextUnavailable, preflight.failure)
    }

    @Test
    fun unsupportedProtectedAlgorithmFailsBeforeSoftwareFallback() = runTest {
        val context = activityRule.scenario.withActivity().applicationContext
        val provider = AndroidPlatformKeyProvider(context, interactionContextProvider = { null })
        val preflight = provider.preflight(
            PlatformKeyRequest(KeyType.Ed25519, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
        )

        assertFalse(preflight.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, preflight.failure)

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            provider.generate(
                PlatformKeyRequest(KeyType.Ed25519, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
            )
        }
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
    }

    @Test
    fun currentActivityProviderResolvesActivityAfterRecreation() = runTest {
        var currentActivity = activityRule.scenario.withActivity()
        val applicationContext = currentActivity.applicationContext
        val provider = AndroidPlatformKeyProvider(
            context = applicationContext,
            interactionContextProvider = { currentActivity },
        )

        assertNotEquals(
            KeyUseAuthorizationFailure.InteractionContextUnavailable,
            provider.preflight(
                PlatformKeyRequest(KeyType.secp256r1, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
            ).failure,
        )

        activityRule.scenario.recreate()
        currentActivity = activityRule.scenario.withActivity()

        assertNotEquals(
            KeyUseAuthorizationFailure.InteractionContextUnavailable,
            provider.preflight(
                PlatformKeyRequest(KeyType.secp256r1, keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet)
            ).failure,
        )
    }

    private fun <A : FragmentActivity> androidx.test.core.app.ActivityScenario<A>.withActivity(): A {
        lateinit var activity: A
        onActivity { activity = it }
        return activity
    }
}
