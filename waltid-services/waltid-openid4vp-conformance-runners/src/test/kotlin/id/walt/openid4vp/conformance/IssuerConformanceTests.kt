package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.IssuerConformanceTestRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        private const val defaultClientAttesterJwkResource = "/keys/attester-key.json"
        private const val credentialIssuerUrlProperty = "openid4vci.conformance.credential-issuer-url"
        private const val credentialIssuerUrlEnv = "OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL"
        private const val conformanceSuiteHostProperty = "openid4vci.conformance.suite-host"
        private const val conformanceSuiteHostEnv = "OPENID4VCI_CONFORMANCE_SUITE_HOST"
        private const val conformanceSuitePortProperty = "openid4vci.conformance.suite-port"
        private const val conformanceSuitePortEnv = "OPENID4VCI_CONFORMANCE_SUITE_PORT"
        private const val enterpriseBaseUrlProperty = "openid4vci.conformance.enterprise-base-url"
        private const val enterpriseBaseUrlEnv = "OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL"
        private const val enterpriseTargetProperty = "openid4vci.conformance.enterprise-target"
        private const val enterpriseTargetEnv = "OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET"
        private const val sdJwtCredentialConfigurationIdProperty = "openid4vci.conformance.sd-jwt-credential-configuration-id"
        private const val sdJwtCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
        private const val mdocCredentialConfigurationIdProperty = "openid4vci.conformance.mdoc-credential-configuration-id"
        private const val mdocCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID"
        private const val haipSdJwtCredentialConfigurationIdProperty = "openid4vci.conformance.haip-sd-jwt-credential-configuration-id"
        private const val haipSdJwtCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_HAIP_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
        private const val haipMdocCredentialConfigurationIdProperty = "openid4vci.conformance.haip-mdoc-credential-configuration-id"
        private const val haipMdocCredentialConfigurationIdEnv = "OPENID4VCI_CONFORMANCE_HAIP_MDOC_CREDENTIAL_CONFIGURATION_ID"
        private const val clientAttestationIssuerProperty = "openid4vci.conformance.client-attestation-issuer"
        private const val clientAttestationIssuerEnv = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTATION_ISSUER"
        private const val clientAttesterJwksFileProperty = "openid4vci.conformance.client-attester-jwks-file"
        private const val clientAttesterJwksFileEnv = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE"
        private const val authorizationServerProperty = "openid4vci.conformance.authorization-server"
        private const val authorizationServerEnv = "OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER"
        private const val credentialProofTypeHintProperty = "openid4vci.conformance.credential-proof-type-hint"
        private const val credentialProofTypeHintEnv = "OPENID4VCI_CONFORMANCE_CREDENTIAL_PROOF_TYPE_HINT"
        private const val timeoutMinutesProperty = "openid4vci.conformance.timeout-minutes"
        private const val timeoutMinutesEnv = "OPENID4VCI_CONFORMANCE_TIMEOUT_MINUTES"

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
            
            val jwkJson = if (configuredPath != null) {
                // Load from file path if explicitly configured
                Files.readString(Path.of(configuredPath))
            } else {
                // Load from classpath resource
                IssuerConformanceTests::class.java.getResourceAsStream(defaultClientAttesterJwkResource)
                    ?.bufferedReader()?.readText()
                    ?: error("Default client attester key not found in classpath: $defaultClientAttesterJwkResource")
            }
            
            val parsed = Json.parseToJsonElement(jwkJson)
            val source = configuredPath ?: "classpath:$defaultClientAttesterJwkResource"
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

                else -> error("Client attester key file must contain a JWK object or JWKS object: $source")
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

        val conformanceSuiteHost: String =
            propertyOrEnv(conformanceSuiteHostProperty, conformanceSuiteHostEnv)
                ?: ConformanceConfig.CONFORMANCE_HOST

        val conformanceSuitePort: Int = propertyOrEnv(conformanceSuitePortProperty, conformanceSuitePortEnv)
            ?.toIntOrNull()
            ?: ConformanceConfig.CONFORMANCE_PORT

        val sdJwtCredentialConfigurationId: String? =
            propertyOrEnv(sdJwtCredentialConfigurationIdProperty, sdJwtCredentialConfigurationIdEnv)

        val mdocCredentialConfigurationId: String? =
            propertyOrEnv(mdocCredentialConfigurationIdProperty, mdocCredentialConfigurationIdEnv)

        val haipSdJwtCredentialConfigurationId: String? =
            propertyOrEnv(haipSdJwtCredentialConfigurationIdProperty, haipSdJwtCredentialConfigurationIdEnv)

        val haipMdocCredentialConfigurationId: String? =
            propertyOrEnv(haipMdocCredentialConfigurationIdProperty, haipMdocCredentialConfigurationIdEnv)

        val clientAttestationIssuer: String =
            propertyOrEnv(clientAttestationIssuerProperty, clientAttestationIssuerEnv)
                ?: "https://client-attestation.example.com"

        val clientAttesterJwks: JsonObject = loadClientAttesterJwks()

        val authorizationServer: String? =
            propertyOrEnv(authorizationServerProperty, authorizationServerEnv)

        val credentialProofTypeHint: String? =
            propertyOrEnv(credentialProofTypeHintProperty, credentialProofTypeHintEnv)

        val timeoutMinutes: Long =
            propertyOrEnv(timeoutMinutesProperty, timeoutMinutesEnv)?.toLongOrNull() ?: 240L
    }

    @Test
    fun runIssuerConformanceTests() {
        assumeTrue(credentialIssuerUrl != null, "No credential issuer URL / enterprise issuer target configured")

        runBlocking {
            withTimeout(timeoutMinutes.minutes) {
                IssuerConformanceTestRunner(
                    credentialIssuerUrl = requireNotNull(credentialIssuerUrl),
                    conformanceHost = conformanceSuiteHost,
                    conformancePort = conformanceSuitePort,
                    sdJwtCredentialConfigurationId = sdJwtCredentialConfigurationId,
                    mdocCredentialConfigurationId = mdocCredentialConfigurationId,
                    haipSdJwtCredentialConfigurationId = haipSdJwtCredentialConfigurationId,
                    haipMdocCredentialConfigurationId = haipMdocCredentialConfigurationId,
                    clientAttestationIssuer = clientAttestationIssuer,
                    clientAttesterJwks = clientAttesterJwks,
                    authorizationServer = authorizationServer,
                    credentialProofTypeHint = credentialProofTypeHint,
                ).run()
            }
        }
    }
}
