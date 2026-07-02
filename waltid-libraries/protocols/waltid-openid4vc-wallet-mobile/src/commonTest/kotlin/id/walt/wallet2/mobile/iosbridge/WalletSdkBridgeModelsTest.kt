package id.walt.wallet2.mobile.iosbridge

import id.walt.wallet2.mobile.MobileWalletCredential
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WalletSdkBridgeModelsTest {

    @Test
    fun mapsMobileCredentialToSwiftSafeBridgeCredential() {
        val credential = MobileWalletCredential(
            id = "credential-1",
            format = "vc+sd-jwt",
            issuer = "https://issuer.example",
            subject = "did:key:subject",
            label = "PID",
            addedAt = "2026-07-02T08:00:00Z",
        )

        val bridged = credential.toWalletBridgeCredential()

        assertEquals("credential-1", bridged.id)
        assertEquals("vc+sd-jwt", bridged.format)
        assertEquals("https://issuer.example", bridged.issuer)
        assertEquals("did:key:subject", bridged.subject)
        assertEquals("PID", bridged.label)
        assertEquals("2026-07-02T08:00:00Z", bridged.addedAt)
    }

    @Test
    fun mapsMissingCredentialFieldsToNullInsteadOfPlaceholderStrings() {
        val credential = MobileWalletCredential(
            id = "credential-2",
            format = "jwt_vc_json",
            issuer = null,
            subject = null,
            label = null,
            addedAt = null,
        )

        val bridged = credential.toWalletBridgeCredential()

        assertEquals(null, bridged.issuer)
        assertEquals(null, bridged.subject)
        assertEquals(null, bridged.label)
        assertEquals(null, bridged.addedAt)
    }

    @Test
    fun mapsThrowableCategoriesWithoutLeakingRawKotlinExceptionTypesAsTheApi() {
        val invalid = WalletBridgeError.fromThrowable(IllegalArgumentException("bad offer"))
        val cancelled = WalletBridgeError.fromThrowable(CancellationException("cancelled"))
        val unknown = WalletBridgeError.fromThrowable(IllegalStateException("boom"))

        assertEquals(WalletBridgeErrorCategory.invalidInput, invalid.category)
        assertEquals("bad offer", invalid.message)
        assertEquals("IllegalArgumentException", invalid.causeClass)

        assertEquals(WalletBridgeErrorCategory.cancelled, cancelled.category)
        assertEquals("cancelled", cancelled.message)

        assertEquals(WalletBridgeErrorCategory.internalFailure, unknown.category)
        assertEquals("boom", unknown.message)
    }

    @Test
    fun resultWrapperCarriesSuccessOrTypedFailure() {
        val success: WalletBridgeResult<List<String>> = WalletBridgeResult.Success(listOf("credential-1"))
        val failure: WalletBridgeResult<List<String>> = WalletBridgeResult.Failure(
            WalletBridgeError(
                category = WalletBridgeErrorCategory.network,
                message = "offline",
            )
        )

        assertIs<WalletBridgeResult.Success<List<String>>>(success)
        assertEquals(listOf("credential-1"), success.value)

        assertIs<WalletBridgeResult.Failure>(failure)
        assertEquals(WalletBridgeErrorCategory.network, failure.error.category)
    }
}
