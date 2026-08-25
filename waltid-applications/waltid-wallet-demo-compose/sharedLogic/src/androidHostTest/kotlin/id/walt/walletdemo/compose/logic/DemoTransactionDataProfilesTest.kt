package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DemoTransactionDataProfilesTest {
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
