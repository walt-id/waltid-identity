@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureConfig
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.testing.E2ETest
import id.walt.commons.web.modules.AuthenticationServiceModule
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.auth.configureWallet2Auth
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import id.walt.openid4vp.clientidprefix.ClientIdX509TrustRegistry
import id.walt.trust.model.TrustedEntityType
import id.walt.wallet2.OSSWallet2Service.toDomain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Wallet2ClientIdTrustStartupTest {
    @Test
    fun `startup wires pre-registered client metadata and JWKS`() {
        val metadata = ClientMetadata(
            jwks = ClientMetadata.Jwks(
                listOf(
                    buildJsonObject {
                        put("kty", "OKP")
                        put("crv", "Ed25519")
                        put("kid", "verifier-key")
                        put("x", "11qYAYdk9J7eQc8H9r0b7G8TaG8h7s8L8Z8f8A8B8C8")
                    }
                )
            )
        )
        val authConfig = OSSWallet2AuthConfig(
            signingKey = DirectSerializedKey(runBlocking { JWKKey.generate(KeyType.Ed25519) }),
        )
        try {
            val port = freePort()
            E2ETest("127.0.0.1", port, failEarly = true).testBlock(
                features = listOf(OSSWallet2FeatureCatalog),
                featureAmendments = mapOf(
                    CommonsFeatureCatalog.authenticationServiceFeature to suspend {
                        AuthenticationServiceModule.AuthenticationServiceConfig.customAuthentication = {
                            ktorAuthnz("ktor-authnz") { }
                        }
                    }
                ),
                preload = {
                    ConfigManager.preloadConfig("_features", FeatureConfig(enabledFeatures = listOf("auth")))
                    ConfigManager.preloadConfig(
                        "wallet-service",
                        OSSWallet2ServiceConfig(
                            publicBaseUrl = Url("http://127.0.0.1:$port"),
                            clientIdTrust = ClientIdTrustConfig(
                                preRegisteredClients = mapOf("registered-verifier" to metadata),
                            ),
                        )
                    )
                    ConfigManager.preloadConfig("auth", authConfig)
                },
                init = {
                    DidService.minimalInit()
                    OSSWallet2Service.configureInMemory()
                },
                module = {
                    val loadedAuthConfig = runBlocking { configureWallet2Auth() }
                    wallet2Module(withPlugins = false, authConfig = loadedAuthConfig)
                },
            ) {
                testAndReturn("Pre-registered metadata is wired through startup") {
                    assertEquals(
                        metadata,
                        OSSWallet2Service.configuredClientIdTrustConfiguration()
                            .preRegisteredClients["registered-verifier"],
                    )
                }
            }
        } finally {
            ConfigManager.preclear()
            FeatureManager.preclear()
        }
    }

    @Test
    fun `trustRegistry config decides x509 clients through the registry`() {
        val trust = ClientIdTrustConfig(
            trustRegistry = ClientIdTrustRegistryConfig(
                resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain",
                expectedEntityTypes = setOf(TrustedEntityType.RELYING_PARTY_PROVIDER),
                requireAuthenticatedSource = true,
            ),
        ).toDomain()

        assertNull(trust.x509TrustAnchors)
        val registry = assertNotNull(trust.x509TrustRegistry)
        assertEquals(setOf(TrustedEntityType.RELYING_PARTY_PROVIDER), registry.expectedEntityTypes)
        assertEquals(true, registry.requireAuthenticatedSource)
        assertEquals(
            ClientIdX509TrustRegistry.RELYING_PARTY_ENTITY_TYPES,
            ClientIdTrustRegistryConfig("https://trust.example.com/resolve/certificate-chain").expectedEntityTypes,
        )
    }

    @Test
    fun `x509TrustAnchors are kept beside trustRegistry for verifier_attestation`() {
        val trust = ClientIdTrustConfig(
            x509TrustAnchors = listOf(DEMO_VERIFIER_PEM),
            trustRegistry = ClientIdTrustRegistryConfig("https://trust.example.com/resolve/certificate-chain"),
        ).toDomain()

        assertNotNull(trust.x509TrustAnchors)
        assertNotNull(trust.x509TrustRegistry)
    }

    @Test
    fun `trustRegistry config does not print header values`() {
        val config = ClientIdTrustRegistryConfig(
            resolveCertificateChainUrl = "https://trust.example.com/resolve/certificate-chain",
            headers = mapOf("Authorization" to "Bearer secret-token"),
        )

        assertFalse("secret-token" in config.toString(), config.toString())
    }

    private companion object {
        /** The demo verifier anchor from `config/wallet-service.conf`. */
        const val DEMO_VERIFIER_PEM = """-----BEGIN CERTIFICATE-----
MIIB2DCCAX+gAwIBAgIUHM9IDlzSNPwZcKStPxhcDrQBxPEwCgYIKoZIzj0EAwIw
MTEdMBsGA1UEAwwUdmVyaWZpZXIuZXhhbXBsZS5jb20xEDAOBgNVBAoMB3dhbHQu
aWQwHhcNMjYwODEzMDAwMDAwWhcNMjgwODEzMDAwMDAwWjAxMR0wGwYDVQQDDBR2
ZXJpZmllci5leGFtcGxlLmNvbTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49
AgEGCCqGSM49AwEHA0IABBv04AXNAZJjIqYkP+oJGpiJ95pqe4XE62bsqy05pJz9
VkRMZdXYXSMff5AJLrnHiN0x5MV6u/8vrAcytGUe4z6jdTBzMAwGA1UdEwEB/wQC
MAAwDgYDVR0PAQH/BAQDAgeAMBMGA1UdJQQMMAoGCCsGAQUFBwMCMB8GA1UdEQQY
MBaCFHZlcmlmaWVyLmV4YW1wbGUuY29tMB0GA1UdDgQWBBSCJZ2bjB1VuLM8lvN8
e/M4MYmLvzAKBggqhkjOPQQDAgNHADBEAiAI8wYnWdTWeqNLKMVw8UU4xPPnZkR4
5NhD/iUNKexRdgIgCdTIULB9hbTNdm+S7nANVTuVw3Kw0tnUjF7Ihdia/dw=
-----END CERTIFICATE-----"""
    }
}
