package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.persistence.encryption.WalletPersistenceException
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class WalletSdkBridgeModelsTest {

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
    fun mapsPersistenceFailuresToStorageCategory() {
        val error = WalletBridgeError.fromThrowable(
            WalletPersistenceException.DatabaseUnlockFailed(walletId = "wallet-1")
        )

        assertEquals(WalletBridgeErrorCategory.storage, error.category)
        assertEquals("Wallet 'wallet-1' database could not be unlocked", error.message)
        assertEquals("DatabaseUnlockFailed", error.causeClass)
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
