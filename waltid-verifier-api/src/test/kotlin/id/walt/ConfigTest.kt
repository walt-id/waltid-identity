package id.walt

import id.walt.oid4vc.data.ClientIdScheme
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.OIDCVerifierService
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {

    @Test
    fun testConfig() {
        ConfigManager.loadConfigs(arrayOf())
        ConfigManager.getConfig<OIDCVerifierServiceConfig>()
        assertEquals("verifier.potential.walt-test.cloud", OIDCVerifierService.config.clientIdMap[ClientIdScheme.X509SanDns])
    }
}
