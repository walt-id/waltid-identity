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
import kotlin.test.assertEquals

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
            E2ETest("127.0.0.1", 17066, failEarly = true).testBlock(
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
                            publicBaseUrl = Url("http://127.0.0.1:17066"),
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
}
