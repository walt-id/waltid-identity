package id.walt.wallet2.mobile.test
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.mobile.MobileWalletConfig
import id.walt.wallet2.mobile.MobileWalletFactory
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.mobile.identity.MobileWalletKeyAuthorization
import kotlin.test.*
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

class SigningIdentityInitializationDeviceTest {
    @Test fun unauthenticatedDefaultInitializesOnAvailableNativeStorage() = runTest {
        val wallet = MobileWalletFactory(InstrumentationRegistry.getInstrumentation().targetContext).create(
            MobileWalletConfig(
                walletId = "wal749-review-${Uuid.random()}",
                keyAuthorization = MobileWalletKeyAuthorization(policy = KeyUseAuthorizationPolicy.None),
            ))
        try {
            val initialized = wallet.signingIdentity.initialize()
            println("WAL749_REVIEW initialize=$initialized")
            assertIs<SigningIdentityOperationResult.Active>(initialized)
        } finally { wallet.deleteWallet() }
    }
}
