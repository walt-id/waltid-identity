package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWalletTransactionDataProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DemoTransactionDataProfilesTest {
    @Test
    fun deployedProfilesAreUsedWithoutLocalAdditions() = runTest {
        val profiles = listOf(MobileWalletTransactionDataProfile("deployed-payment", "Payment", listOf("amount")))
        val result = DemoWalletConfig().resolveDemoTransactionDataProfiles { profiles }
        assertEquals(profiles, result.profiles)
        assertNull(result.warning)
    }

    @Test
    fun unavailableProfilesDoNotEnableLocalPaymentTypes() = runTest {
        val result = DemoWalletConfig().resolveDemoTransactionDataProfiles { error("unavailable") }
        assertEquals(emptyList(), result.profiles)
        assertNotNull(result.warning)
    }

    @Test
    fun cancellationIsPropagatedInsteadOfBecomingAnUnavailableProfileResult() = runTest {
        val cancellation = assertFailsWith<CancellationException> {
            DemoWalletConfig().resolveDemoTransactionDataProfiles {
                throw CancellationException("foreground refresh superseded")
            }
        }

        assertEquals("foreground refresh superseded", cancellation.message)
    }
}
