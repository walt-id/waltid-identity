package id.walt.wallet2.mobile.test
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.uuid.Uuid

class WalletIdentityInitializationDeviceTest {
    @Test fun unauthenticatedDefaultInitializesOnAvailableNativeStorage() = runTest {
        val wallet = MobileWalletFactory(InstrumentationRegistry.getInstrumentation().targetContext).create(
            MobileWalletConfig(walletId = "wal749-review-${Uuid.random()}", defaultKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None))
        try {
            val initialized = wallet.identities.initialize()
            println("WAL749_REVIEW initialize=$initialized")
            assertIs<IdentityOperationResult.Active>(initialized)
        } finally { wallet.deleteWallet() }
    }
}
