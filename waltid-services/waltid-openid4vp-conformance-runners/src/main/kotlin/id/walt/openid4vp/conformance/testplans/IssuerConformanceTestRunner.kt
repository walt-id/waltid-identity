package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.IssuerTestPlan
import id.walt.openid4vp.conformance.testplans.plans.Oid4vciIssuerClientAttestationDpop
import id.walt.openid4vp.conformance.testplans.plans.Oid4vciIssuerClientAttestationDpopPreAuth
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestPlanRunner
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/**
 * OpenID4VCI Issuer Conformance Test Runner
 *
 * Runs the conformance suite against an already-running issuer service.
 * The conformance suite acts as a wallet testing the issuer.
 * 
 * Supports two modes:
 * 1. External issuer (Enterprise/Production): Tests against an existing issuer URL
 * 2. Embedded issuer (OSS): Starts a local issuer and tests against it
 * 
 * Configuration via environment variables:
 * - OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL: Direct issuer URL
 * - OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL: Enterprise base URL
 * - OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET: Enterprise target name
 * - OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID: SD-JWT credential ID
 * - OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID: mDOC credential ID
 * - OPENID4VCI_CONFORMANCE_CLIENT_ATTESTATION_ISSUER: Client attestation issuer
 * - OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE: Path to client attester JWKS
 * - OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER: External authorization server
 * - OPENID4VCI_CONFORMANCE_CREDENTIAL_PROOF_TYPE_HINT: Proof type hint
 */
class IssuerConformanceTestRunner(
    private val credentialIssuerUrl: String,
    val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST,
    val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT,
    private val sdJwtCredentialConfigurationId: String? = null,
    private val mdocCredentialConfigurationId: String? = null,
    private val clientAttestationIssuer: String = "https://client-attestation.example.com",
    private val clientAttesterJwks: JsonObject? = null,
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
) {
    private val http = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun run(): List<TestPlanResult> {
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        val conformanceVersion = conformance.getServerVersion()
        assertNotNull(conformanceVersion)
        println("Conformance server version $conformanceVersion available!")

        val metadata = fetchIssuerMetadata()
        val testPlans = buildTestPlans(metadata)
        require(testPlans.isNotEmpty()) {
            "No issuer test plans could be constructed for $credentialIssuerUrl"
        }

        return testPlans.flatMap { plan ->
            val planName = plan::class.simpleName ?: plan::class.jvmName
            println()
            println("=" .repeat(80))
            println("Running issuer plan: $planName")
            println("=" .repeat(80))
            IssuerTestPlanRunner(plan.config, conformanceHost, conformancePort).test()
        }
    }

    private suspend fun fetchIssuerMetadata(): JsonObject {
        val metadataUrl = buildIssuerMetadataUrl(credentialIssuerUrl)
        println("Fetching issuer metadata from: $metadataUrl")
        val metadata = http.get(metadataUrl).body<JsonObject>()
        println("Issuer metadata endpoint responding")
        return metadata
    }

    private fun buildTestPlans(metadata: JsonObject): List<IssuerTestPlan> {
        val credentialConfigurations = metadata["credential_configurations_supported"]?.jsonObject
            ?: error("Issuer metadata at $credentialIssuerUrl did not contain credential_configurations_supported")

        val discoveredSdJwtId = sdJwtCredentialConfigurationId
            ?: credentialConfigurations.entries.firstOrNull {
                it.value.jsonObject["format"]?.jsonPrimitive?.content in setOf("dc+sd-jwt", "vc+sd-jwt", "sd_jwt_vc")
            }?.key
        val discoveredMdocId = mdocCredentialConfigurationId
            ?: credentialConfigurations.entries.firstOrNull {
                it.value.jsonObject["format"]?.jsonPrimitive?.content == "mso_mdoc"
            }?.key

        println("Discovered credential configuration ids:")
        println("  SD-JWT VC: ${discoveredSdJwtId ?: "<not found>"}")
        println("  mDOC:      ${discoveredMdocId ?: "<not found>"}")

        // Use default test plans if no specific configuration provided
        return if (clientAttesterJwks != null) {
            // Enterprise mode with client attestation
            buildList {
                discoveredSdJwtId?.let {
                    add(createEnterprisePlan(it, "sd_jwt_vc"))
                }
                discoveredMdocId?.let {
                    add(createEnterprisePlan(it, "mso_mdoc"))
                }
            }
        } else {
            // OSS mode with simpler configuration
            listOf(
                Oid4vciIssuerClientAttestationDpop(credentialIssuerUrl, conformanceHost, conformancePort),
                Oid4vciIssuerClientAttestationDpopPreAuth(credentialIssuerUrl, conformanceHost, conformancePort)
            )
        }
    }

    private fun createEnterprisePlan(credentialConfigId: String, format: String): IssuerTestPlan {
        return Oid4vciIssuerClientAttestationDpop(
            issuerUrl = credentialIssuerUrl,
            conformanceHost = conformanceHost,
            conformancePort = conformancePort
        )
    }

    private fun buildIssuerMetadataUrl(issuerUrl: String): String {
        // OID4VCI metadata URL: <issuer>/.well-known/openid-credential-issuer
        // If issuer has a path (e.g., /draft13), the .well-known goes at the end
        return issuerUrl.trimEnd('/') + "/.well-known/openid-credential-issuer"
    }

    companion object {
        private const val DEFAULT_CLIENT_ATTESTER_JWK_PATH =
            "/home/pp/dev/walt-id/waltid-enterprise-quickstart/cli/keys/attester-key.json"

        fun loadClientAttesterJwks(path: String? = null): JsonObject? {
            val configuredPath = path ?: DEFAULT_CLIENT_ATTESTER_JWK_PATH
            val file = Path.of(configuredPath)
            if (!Files.exists(file)) {
                return null
            }
            
            val jwkJson = Files.readString(file)
            val parsed = Json.parseToJsonElement(jwkJson)
            return when (parsed) {
                is JsonObject -> {
                    if ("keys" in parsed) {
                        buildJsonObject {
                            put("keys", JsonArray(parsed["keys"]!!.jsonArray.map { normalizeAttesterJwk(it.jsonObject) }))
                        }
                    } else {
                        buildJsonObject {
                            put("keys", JsonArray(listOf(normalizeAttesterJwk(parsed))))
                        }
                    }
                }
                else -> error("Client attester key file must contain a JWK object or JWKS object: $configuredPath")
            }
        }

        private fun normalizeAttesterJwk(jwk: JsonObject): JsonObject = buildJsonObject {
            jwk.forEach { (key, value) -> put(key, value) }

            if (jwk["alg"] == null) {
                val curve = jwk["crv"]?.jsonPrimitive?.content
                val algorithm = when (curve) {
                    "P-256" -> "ES256"
                    "P-384" -> "ES384"
                    "P-521" -> "ES512"
                    else -> null
                }
                algorithm?.let { put("alg", it) }
            }

            if (jwk["use"] == null) {
                put("use", "sig")
            }
        }
    }
}
