package id.walt.wallet2.client

import kotlin.test.Test
import kotlin.test.assertEquals

class WalletClientEnvironmentTest {

    @Test
    fun quickstartLocalProfileUsesAndroidReachableEnterpriseDefaults() {
        val environment = WalletClientEnvironmentProfile.QuickstartLocal.toEnvironment()

        assertEquals("http://10.0.2.2", environment.enterpriseBaseUrl)
        assertEquals("waltid.enterprise.localhost", environment.enterpriseHostHeader)
        assertEquals("waltid.waltid-tenant01.wallet", environment.walletPath)
        assertEquals("waltid.waltid-tenant01.client-attester", environment.attesterServiceRef)
        assertEquals("waltid.waltid-tenant01.kms.wallet_key", environment.instanceKeyReference)
    }

    @Test
    fun derivesEnterpriseReferencesFromWalletPath() {
        val environment = WalletClientEnvironment(
            enterpriseBaseUrl = "http://10.0.2.2",
            walletPath = "org.tenant.wallet",
        ).withDerivedReferences()

        assertEquals("org.tenant.client-attester", environment.attesterServiceRef)
        assertEquals("org.tenant.kms.wallet_key", environment.instanceKeyReference)
    }
}
