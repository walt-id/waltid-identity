package id.walt.wallet2.mobile.test
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.mobile.WalletConfiguration
import id.walt.wallet2.mobile.WalletFactory
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.mobile.identity.WalletKeyAuthorization
import kotlin.test.*
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

class SigningIdentityInitializationDeviceTest {
    @Test fun unauthenticatedDefaultInitializesOnAvailableNativeStorage() = runTest {
        val wallet = WalletFactory(InstrumentationRegistry.getInstrumentation().targetContext).open(
            WalletConfiguration(
                walletId = "wal749-review-${Uuid.random()}",
                keyAuthorization = WalletKeyAuthorization(policy = KeyUseAuthorizationPolicy.None),
            ))
        try {
            val initialized = wallet.signingIdentity.initialize()
            println("WAL749_REVIEW initialize=$initialized")
            assertIs<SigningIdentityOperationResult.Active>(initialized)
        } finally { wallet.deleteLocalData() }
    }
}
