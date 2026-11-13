package id.walt.walletdemo.compose.logic

import id.walt.wallet2.walletprovider.EnterpriseDemoWalletProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class DemoWalletProviderDefaultsTest {
    @Test
    fun demoAddressMatchesTheWalletLibraryConstant() {
        assertEquals(EnterpriseDemoWalletProvider.BASE_URL, DemoPublicBackendDefaults.walletProviderBaseUrl)
        assertEquals(EnterpriseDemoWalletProvider.SERVICE_PATH, DemoPublicBackendDefaults.walletProviderServicePath)
    }
}
