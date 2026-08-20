package id.walt.openid4vp.conformance.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ConformanceConfigTest {

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
