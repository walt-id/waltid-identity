package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.handlers.PreviewSessionException
import id.walt.wallet2.handlers.PreviewSessionFailureReason
import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationReuseEnforcement
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationReuseTimeoutVerification
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationUnsupportedReason
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WalletSdkBridgeModelsTest {

    @Test
    fun mapsThrowableCategoriesWithoutLeakingRawKotlinExceptionTypesAsTheApi() {
        val invalid = WalletBridgeError.fromThrowable(IllegalArgumentException("bad offer"))
        val cancelled = WalletBridgeError.fromThrowable(CancellationException("cancelled"))
        val unknown = WalletBridgeError.fromThrowable(IllegalStateException("boom"))
        val stalePreview = WalletBridgeError.fromThrowable(
            PreviewSessionException(PreviewSessionFailureReason.EXPIRED, "Preview expired")
        )

        assertEquals(WalletBridgeErrorCategory.invalidInput, invalid.category)
        assertEquals("bad offer", invalid.message)
        assertEquals("IllegalArgumentException", invalid.causeClass)

        assertEquals(WalletBridgeErrorCategory.cancelled, cancelled.category)
        assertEquals("cancelled", cancelled.message)

        assertEquals(WalletBridgeErrorCategory.internalFailure, unknown.category)
        assertEquals("boom", unknown.message)
        assertEquals(WalletBridgeErrorCategory.invalidInput, stalePreview.category)
        assertEquals("Preview expired", stalePreview.message)
    }

    @Test
    fun mapsPersistenceFailuresToStorageCategory() {
        val error = WalletBridgeError.fromThrowable(
            WalletPersistenceException.DatabaseUnlockFailed(walletId = "wallet-1")
        )

        assertEquals(WalletBridgeErrorCategory.storage, error.category)
        assertEquals("Wallet 'wallet-1' database could not be unlocked", error.message)
        assertEquals("DatabaseUnlockFailed", error.causeClass)
    }

    @Test
    fun mapsAuthorizationFailuresToDedicatedBridgeCategory() {
        val error = WalletBridgeError.fromThrowable(
            KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                message = "Protected key unavailable",
            )
        )

        assertEquals(WalletBridgeErrorCategory.authorization, error.category)
        assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, error.authorizationFailure)
    }

    @Test
    fun resultWrapperCarriesSuccessOrTypedFailure() {
        val success: WalletBridgeResult<List<String>> = WalletBridgeResult.Success(listOf("credential-1"))
        val failure: WalletBridgeResult<List<String>> = WalletBridgeResult.Failure(
            WalletBridgeError.fromThrowable(IllegalStateException("offline"))
        )

        assertIs<WalletBridgeResult.Success<List<String>>>(success)
        assertEquals(listOf("credential-1"), success.value)

        assertIs<WalletBridgeResult.Failure>(failure)
        assertEquals(WalletBridgeErrorCategory.internalFailure, failure.error.category)
    }

    @Test
    fun bridgePreflightRequiresFailureExactlyWhenUnsupported() {
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeKeyPreflight(supported = true, failure = KeyUseAuthorizationUnsupportedReason.BiometricUnavailable)
        }
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeKeyPreflight(supported = false)
        }
    }

    @Test
    fun bridgeTimedAuthorizationPolicyPreservesTimeoutAndProviderVerification() {
        val timed = WalletBridgeKeyUseAuthorizationPolicy(
            type = WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse,
            timeoutSeconds = 10,
        )

        assertEquals(KeyUseAuthorizationPolicy.BiometricTimedReuse(10), timed.toCorePolicy())

        val preflight = KeyUseAuthorizationSupport.Supported(
            effectivePolicy = KeyUseAuthorizationPolicy.BiometricTimedReuse(10),
            reuseEnforcement = KeyUseAuthorizationReuseEnforcement.ProviderProcess,
            timeoutVerification = KeyUseAuthorizationReuseTimeoutVerification.ProviderConfigured,
        ).toBridgeModel()

        assertEquals(timed, preflight.effectivePolicy)
        assertEquals(
            WalletBridgeKeyUseAuthorizationReuseEnforcement.ProviderProcess,
            preflight.reuseEnforcement,
        )
        assertEquals(
            WalletBridgeKeyUseAuthorizationReuseTimeoutVerification.ProviderConfigured,
            preflight.timeoutVerification,
        )
    }

    @Test
    fun bridgePreflightRejectsIncompleteOrMismatchedTimedMetadata() {
        val policy = WalletBridgeKeyUseAuthorizationPolicy(
            type = WalletBridgeKeyUseAuthorizationPolicyType.BiometricTimedReuse,
            timeoutSeconds = 10,
        )

        assertFailsWith<IllegalArgumentException> {
            WalletBridgeKeyPreflight(supported = true, effectivePolicy = policy)
        }
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeKeyPreflight(
                supported = true,
                effectivePolicy = policy,
                reuseEnforcement = WalletBridgeKeyUseAuthorizationReuseEnforcement.PlatformKeyStore,
                timeoutVerification = WalletBridgeKeyUseAuthorizationReuseTimeoutVerification.ProviderConfigured,
            )
        }
    }

    @Test
    fun bridgeErrorRequiresAuthorizationFailureExactlyForAuthorizationCategory() {
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeError(WalletBridgeErrorCategory.authorization, "missing failure")
        }
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeError(
                WalletBridgeErrorCategory.internalFailure,
                "unexpected failure field",
                authorizationFailure = KeyUseAuthorizationFailure.AuthorizationNotCompleted,
            )
        }
    }

    @Test
    fun bridgeRequiresCompleteCrossProcessConfiguration() {
        assertFailsWith<IllegalArgumentException> {
            WalletBridgeConfiguration(appGroupIdentifier = "group.example").toMobileWalletConfig()
        }

        val config = WalletBridgeConfiguration(
            appGroupIdentifier = "group.example",
            keychainAccessGroup = "TEAM.example",
        ).toMobileWalletConfig()

        assertEquals("group.example", config.crossProcessAccess?.appGroupIdentifier)
        assertEquals("TEAM.example", config.crossProcessAccess?.keychainAccessGroup)
    }
}
