package id.walt

import id.walt.config.ConfigManager
import id.walt.logging.TestLoggingUtils
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.OIDCVerifierService
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {

    @Test
    fun testConfig() {
        TestLoggingUtils.setupTestLogging()
        ConfigManager.testWithConfigs(testConfigs)
        ConfigManager.getConfig<OIDCVerifierServiceConfig>()
        assertEquals("verifier.potential.walt-test.cloud", OIDCVerifierService.config.clientIdMap[ClientIdScheme.X509SanDns])
    }
}
