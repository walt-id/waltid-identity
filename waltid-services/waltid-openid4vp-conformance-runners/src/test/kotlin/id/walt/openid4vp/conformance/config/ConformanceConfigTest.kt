package id.walt.openid4vp.conformance.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConformanceConfigTest {

    @Test
    fun fullPlanModeRejectsInheritedModuleSlicesWithoutChangingCiDefaults() {
        val slices = listOf(
            "OPENID4VCI_CONFORMANCE_MODULES" to "happy-flow",
            "OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES" to "tls-test",
            "OPENID4VCI_CONFORMANCE_ADDITIONAL_MODULES" to "happy-flow",
            "OPENID4VCI_CONFORMANCE_MODULE_GROUPS" to "metadata,positive,negative",
            "OPENID4VCI_CONFORMANCE_ISSUANCE_MODE" to "deferred",
            "OPENID4VCI_CONFORMANCE_DISCOVERY_ONLY" to "true",
            "OPENID4VCI_CONFORMANCE_MATRIX" to "discovery",
            "OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION" to "plain",
        )
        assertEquals(ConformanceExecutionMode.CI, ConformanceExecutionMode.fromEnvironment(emptyMap()))
        for ((name, value) in slices) {
            assertEquals(ConformanceExecutionMode.CI, ConformanceExecutionMode.fromEnvironment(mapOf(name to value)))
            assertFailsWith<IllegalArgumentException>(name) {
                ConformanceExecutionMode.fromEnvironment(mapOf("CONFORMANCE_EXECUTION_MODE" to "full-plan", name to value))
            }
        }
        assertEquals(ConformanceExecutionMode.FULL_PLAN, ConformanceExecutionMode.fromEnvironment(
            mapOf("CONFORMANCE_EXECUTION_MODE" to "full-plan", "OPENID4VCI_CONFORMANCE_MODULE_GROUPS" to "all")))
        assertFailsWith<IllegalStateException> {
            ConformanceExecutionMode.fromEnvironment(mapOf("CONFORMANCE_EXECUTION_MODE" to "ful-plan"))
        }
    }


    @Test
    fun walletAdapterAuthorizationUrlUsesLocalAdapterByDefault() {
        val expected =
            "http://${ConformanceConfig.ADAPTER_CALLBACK_HOST}:${ConformanceConfig.WALLET_ADAPTER_PORT}/openid4vp/authorize"
        assertEquals(expected, ConformanceConfig.walletAdapterAuthorizationUrl(publicUrl = null))
    }

    @Test
    fun walletAdapterAuthorizationUrlPrefersPublicHttpsTunnel() {
        assertEquals(
            "https://example.trycloudflare.com/openid4vp/authorize",
            ConformanceConfig.walletAdapterAuthorizationUrl(
                publicUrl = "https://example.trycloudflare.com/openid4vp/authorize",
            ),
        )
    }

    @Test
    fun vciSuiteCredentialOfferEndpointUsesAdapterHostByDefault() {
        assertEquals(
            "http://host.docker.internal:7007/credential-offer",
            ConformanceConfig.vciSuiteCredentialOfferEndpoint(
                adapterHost = "host.docker.internal",
                publicBaseUrl = null,
            ),
        )
    }

    @Test
    fun vciSuiteCredentialOfferEndpointPrefersPublicHttpsBase() {
        assertEquals(
            "https://example.trycloudflare.com/credential-offer",
            ConformanceConfig.vciSuiteCredentialOfferEndpoint(
                adapterHost = "127.0.0.1",
                publicBaseUrl = "https://example.trycloudflare.com/",
            ),
        )
    }
}
