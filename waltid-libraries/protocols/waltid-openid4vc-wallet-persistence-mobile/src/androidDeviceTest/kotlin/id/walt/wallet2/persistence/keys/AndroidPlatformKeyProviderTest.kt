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
    fun nonePreservesExistingAlgorithmSupport() = runTest {
        val provider = AndroidPlatformKeyProvider()

        KeyType.entries.forEach { keyType ->
            val capability = provider.capability(keyType, KeyUseAuthorizationPolicy.None)
            val expected = keyType in provider.supportedPlatformKeyTypes ||
                keyType in setOf(KeyType.Ed25519, KeyType.secp256k1)
            assertEquals(expected, capability.supported, "Unexpected None capability for $keyType")
        }
    }

    @Test
    fun protectedP256WithoutFragmentActivityFailsExplicitly() = runTest {
        val capability = AndroidPlatformKeyProvider().capability(
            KeyType.secp256r1,
            KeyUseAuthorizationPolicy.BiometricCurrentSet,
        )

        assertFalse(capability.supported)
        assertEquals(KeyUseAuthorizationFailure.InteractionContextUnavailable, capability.failure)
    }

    @Test
    fun unsupportedProtectedAlgorithmFailsBeforeSoftwareFallback() = runTest {
        val provider = AndroidPlatformKeyProvider()
        val capability = provider.capability(
            KeyType.Ed25519,
            KeyUseAuthorizationPolicy.BiometricCurrentSet,
        )

        assertFalse(capability.supported)
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, capability.failure)

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            provider.generateKey(
                PlatformKeyGenerationRequest(
                    keyType = KeyType.Ed25519,
                    keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                )
            )
        }
        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
    }

    @Test
    fun currentActivityProviderSurvivesRecreationWithoutRetainingDestroyedActivity() = runTest {
        var currentActivity = activityRule.scenario.withActivity()
        val applicationContext = currentActivity.applicationContext
        val activityScopedProvider = AndroidPlatformKeyProvider(currentActivity)
        val recreationAwareProvider = AndroidPlatformKeyProvider(
            context = applicationContext,
            interactionContextProvider = { currentActivity },
        )

        assertNotEquals(
            KeyUseAuthorizationFailure.InteractionContextUnavailable,
            recreationAwareProvider.capability(
                KeyType.secp256r1,
                KeyUseAuthorizationPolicy.BiometricCurrentSet,
            ).failure,
        )

        activityRule.scenario.recreate()
        currentActivity = activityRule.scenario.withActivity()

        assertEquals(
            KeyUseAuthorizationFailure.InteractionContextUnavailable,
            activityScopedProvider.capability(
                KeyType.secp256r1,
                KeyUseAuthorizationPolicy.BiometricCurrentSet,
            ).failure,
        )
        assertNotEquals(
            KeyUseAuthorizationFailure.InteractionContextUnavailable,
            recreationAwareProvider.capability(
                KeyType.secp256r1,
                KeyUseAuthorizationPolicy.BiometricCurrentSet,
            ).failure,
        )
    }

    private fun <A : FragmentActivity> androidx.test.core.app.ActivityScenario<A>.withActivity(): A {
        lateinit var activity: A
        onActivity { activity = it }
        return activity
    }
}
