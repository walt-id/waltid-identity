package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.ConformanceTests.Companion.conformanceHost
import id.walt.openid4vp.conformance.ConformanceTests.Companion.conformancePort
import id.walt.openid4vp.conformance.testplans.IssuerConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class IssuerConformanceTests {

    companion object {
        private const val defaultClientAttesterJwkPath =
            "/home/pp/dev/walt-id/waltid-enterprise-quickstart/cli/keys/attester-key.json"
        private const val credentialIssuerUrlProperty = "openid4vci.conformance.credential-issuer-url"
        private const val credentialIssuerUrlEnv = "OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL"
        private const val enterpriseBaseUrlProperty = "openid4vci.conformance.enterprise-base-url"
        private const val enterpriseBaseUrlEnv = "OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL"
        private const val enterpriseTargetProperty = "openid4vci.conformance.enterprise-target"
        private const val enterpriseTargetEnv = "OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET"
        private const val sdJwtCredentialConfigurationIdProperty = "openid4vci.conformance.sd-jwt-credential-configuration-id"
        private const val sdJwtCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
        private const val mdocCredentialConfigurationIdProperty = "openid4vci.conformance.mdoc-credential-configuration-id"
        private const val mdocCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID"
        private const val clientAttestationIssuerProperty = "openid4vci.conformance.client-attestation-issuer"
        private const val clientAttestationIssuerEnv = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTATION_ISSUER"
        private const val clientAttesterJwksFileProperty = "openid4vci.conformance.client-attester-jwks-file"
        private const val clientAttesterJwksFileEnv = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE"
        private const val authorizationServerProperty = "openid4vci.conformance.authorization-server"
        private const val authorizationServerEnv = "OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER"
        private const val credentialProofTypeHintProperty = "openid4vci.conformance.credential-proof-type-hint"
        private const val credentialProofTypeHintEnv = "OPENID4VCI_CONFORMANCE_CREDENTIAL_PROOF_TYPE_HINT"

        private fun propertyOrEnv(property: String, env: String): String? =
            System.getProperty(property) ?: System.getenv(env)

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

        private fun loadClientAttesterJwks(): JsonObject {
            val configuredPath = propertyOrEnv(clientAttesterJwksFileProperty, clientAttesterJwksFileEnv)
                ?: defaultClientAttesterJwkPath
            val jwkJson = Files.readString(Path.of(configuredPath))
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

        private val enterpriseBaseUrl: String =
            propertyOrEnv(enterpriseBaseUrlProperty, enterpriseBaseUrlEnv)
                ?: "http://waltid.enterprise.localhost:3000"

        val credentialIssuerUrl: String? =
            propertyOrEnv(credentialIssuerUrlProperty, credentialIssuerUrlEnv)
                ?: propertyOrEnv(enterpriseTargetProperty, enterpriseTargetEnv)?.let {
                    "$enterpriseBaseUrl/v2/$it/issuer-service-api/openid4vci"
                }

        val sdJwtCredentialConfigurationId: String? =
            propertyOrEnv(sdJwtCredentialConfigurationIdProperty, sdJwtCredentialConfigurationIdEnv)

        val mdocCredentialConfigurationId: String? =
            propertyOrEnv(mdocCredentialConfigurationIdProperty, mdocCredentialConfigurationIdEnv)

        val clientAttestationIssuer: String =
            propertyOrEnv(clientAttestationIssuerProperty, clientAttestationIssuerEnv)
                ?: "https://client-attestation.example.com"

        val clientAttesterJwks: JsonObject = loadClientAttesterJwks()

        val authorizationServer: String? =
            propertyOrEnv(authorizationServerProperty, authorizationServerEnv)

        val credentialProofTypeHint: String? =
            propertyOrEnv(credentialProofTypeHintProperty, credentialProofTypeHintEnv)

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Error getting server version: $it")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        @JvmStatic
        val isIssuerConfigured = credentialIssuerUrl != null
    }

    @Test
    fun runIssuerConformanceTests() = runTest(timeout = 10.minutes) {
        assumeTrue(isConformanceAvailable, "OpenID conformance suite is not reachable")
        assumeTrue(isIssuerConfigured, "No credential issuer URL / enterprise issuer target configured")

        IssuerConformanceTestRunner(
            credentialIssuerUrl = requireNotNull(credentialIssuerUrl),
            conformanceHost = conformanceHost,
            conformancePort = conformancePort,
            sdJwtCredentialConfigurationId = sdJwtCredentialConfigurationId,
            mdocCredentialConfigurationId = mdocCredentialConfigurationId,
            clientAttestationIssuer = clientAttestationIssuer,
            clientAttesterJwks = clientAttesterJwks,
            authorizationServer = authorizationServer,
            credentialProofTypeHint = credentialProofTypeHint,
        ).run()
    }
}
