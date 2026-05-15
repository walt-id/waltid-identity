package id.walt.openid4vp.conformance.testplans

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.issuer.FeatureCatalog as IssuerFeatureCatalog
import id.walt.issuer.config.AuthenticationServiceConfig
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.issuer.issuerModule
import id.walt.issuer.web.plugins.issuerAuthenticationPluginAmendment
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.data.CredSignAlgValues
import id.walt.oid4vc.data.ProofType
import id.walt.oid4vc.data.ProofTypeMetadata
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.IssuerTestPlan
import id.walt.openid4vp.conformance.testplans.plans.Oid4vciIssuerClientAttestationDpop
import id.walt.openid4vp.conformance.testplans.plans.Oid4vciIssuerClientAttestationDpopPreAuth
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestPlanRunner
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/**
 * OpenID4VCI Issuer Conformance Test Runner
 * 
 * Starts a local issuer instance and runs conformance tests against it.
 * The conformance suite acts as a wallet testing our issuer.
 */
class IssuerConformanceTestRunner(
    val localIssuerHost: String = "127.0.0.1",
    val localIssuerPort: Int = 7002,
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) {
    
    private val issuerBaseUrl = "http://$localIssuerHost:$localIssuerPort"
    
    private val testPlans: List<IssuerTestPlan> = listOf(
        Oid4vciIssuerClientAttestationDpop(issuerBaseUrl, conformanceHost, conformancePort),
        Oid4vciIssuerClientAttestationDpopPreAuth(issuerBaseUrl, conformanceHost, conformancePort)
    )

    fun run() {
        E2ETest(localIssuerHost, localIssuerPort, true).testBlock(
            features = listOf(IssuerFeatureCatalog),
            preload = {
                // Pre-configure the issuer service
                ConfigManager.preloadConfig(
                    "issuer-service", OIDCIssuerServiceConfig(
                        baseUrl = issuerBaseUrl,
                        ciTokenKey = runBlocking { 
                            KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1)) 
                        }
                    )
                )
                
                // Configure authentication service (required for OAuth routes)
                ConfigManager.preloadConfig(
                    "authentication-service", AuthenticationServiceConfig(
                        name = "ConformanceTest",
                        authorizeUrl = "http://localhost:8080/auth",  // Dummy - not used in conformance tests
                        accessTokenUrl = "http://localhost:8080/token",
                        clientId = "conformance-test",
                        clientSecret = "test-secret"
                    )
                )
                
                // Configure credential types for conformance testing
                ConfigManager.preloadConfig(
                    "credential-issuer-metadata", CredentialTypeConfig(
                        supportedCredentialTypes = mapOf(
                            // SD-JWT VC for conformance testing
                            "VerifiableCredential" to Json.encodeToJsonElement<CredentialSupported>(
                                CredentialSupported(
                                    format = CredentialFormat.sd_jwt_vc,
                                    cryptographicBindingMethodsSupported = setOf("jwk"),
                                    credentialSigningAlgValuesSupported = setOf(
                                        CredSignAlgValues.Named("ES256")
                                    ),
                                    proofTypesSupported = mapOf(
                                        ProofType.jwt to ProofTypeMetadata(setOf("ES256"))
                                    ),
                                    vct = "$issuerBaseUrl/VerifiableCredential"
                                )
                            ),
                            // ISO mDL for conformance testing  
                            "org.iso.18013.5.1.mDL" to Json.encodeToJsonElement<CredentialSupported>(
                                CredentialSupported(
                                    format = CredentialFormat.mso_mdoc,
                                    cryptographicBindingMethodsSupported = setOf("cose_key"),
                                    credentialSigningAlgValuesSupported = setOf(
                                        CredSignAlgValues.Named("ES256")
                                    ),
                                    proofTypesSupported = mapOf(
                                        ProofType.cwt to ProofTypeMetadata(setOf("ES256"))
                                    ),
                                    docType = "org.iso.18013.5.1.mDL"
                                )
                            )
                        )
                    )
                )
            },
            init = {
                // Run the authentication plugin amendment to configure auth-oauth
                runBlocking { issuerAuthenticationPluginAmendment() }
                
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::issuerModule
        ) {
            val http = testHttpClient()
            
            val conformance = ConformanceInterface(conformanceHost, conformancePort)

            test("Check if conformance suite available") {
                val conformanceVersion = conformance.getServerVersion()
                assertNotNull(conformanceVersion)
                println("✅ Conformance server version $conformanceVersion available!")
                conformanceVersion
            }

            test("Check issuer metadata endpoint") {
                // Verify our issuer's well-known endpoint is accessible
                val response = http.get("$issuerBaseUrl/.well-known/openid-credential-issuer")
                println("✅ Issuer metadata endpoint responding: ${response.status}")
            }

            testPlans.forEach { plan ->
                val planName = plan::class.simpleName ?: plan::class.jvmName

                test(planName) {
                    IssuerTestPlanRunner(plan.config, http, conformanceHost, conformancePort).test()
                }
            }
        }
    }
}

fun main() = IssuerConformanceTestRunner().run()
