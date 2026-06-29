package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.IssuerConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * OpenID4VCI Issuer Conformance Tests
 * 
 * Tests the Issuer implementation against the OpenID Foundation conformance suite.
 * The conformance suite acts as a wallet testing our issuer endpoints.
 * 
 * Prerequisites:
 * 1. Conformance suite running at localhost.emobix.co.uk:8443
 * 2. Issuer service running and accessible
 * 
 * Configuration (environment variables):
 * - OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL: Direct issuer URL
 * - OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL: Enterprise base URL (default: http://waltid.enterprise.localhost:3000)
 * - OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET: Enterprise target for URL construction
 * 
 * Example configurations:
 * 
 * 1. Direct issuer URL:
 *    export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL="http://localhost:7002"
 * 
 * 2. Enterprise issuer:
 *    export OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET="my-org"
 *    # Results in: http://waltid.enterprise.localhost:3000/v2/my-org/issuer-service-api/openid4vci
 * 
 * Run:
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "IssuerConformanceTests"
 * ```
 */
class IssuerConformanceTests {

    companion object {
        private const val CREDENTIAL_ISSUER_URL_PROP = "openid4vci.conformance.credential-issuer-url"
        private const val CREDENTIAL_ISSUER_URL_ENV = "OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL"
        private const val ENTERPRISE_BASE_URL_PROP = "openid4vci.conformance.enterprise-base-url"
        private const val ENTERPRISE_BASE_URL_ENV = "OPENID4VCI_CONFORMANCE_ENTERPRISE_BASE_URL"
        private const val ENTERPRISE_TARGET_PROP = "openid4vci.conformance.enterprise-target"
        private const val ENTERPRISE_TARGET_ENV = "OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET"
        private const val SD_JWT_CREDENTIAL_ID_PROP = "openid4vci.conformance.sd-jwt-credential-configuration-id"
        private const val SD_JWT_CREDENTIAL_ID_ENV = "OPENID4VCI_CONFORMANCE_SD_JWT_CREDENTIAL_CONFIGURATION_ID"
        private const val MDOC_CREDENTIAL_ID_PROP = "openid4vci.conformance.mdoc-credential-configuration-id"
        private const val MDOC_CREDENTIAL_ID_ENV = "OPENID4VCI_CONFORMANCE_MDOC_CREDENTIAL_CONFIGURATION_ID"
        private const val CLIENT_ATTESTATION_ISSUER_PROP = "openid4vci.conformance.client-attestation-issuer"
        private const val CLIENT_ATTESTATION_ISSUER_ENV = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTATION_ISSUER"
        private const val CLIENT_ATTESTER_JWKS_FILE_PROP = "openid4vci.conformance.client-attester-jwks-file"
        private const val CLIENT_ATTESTER_JWKS_FILE_ENV = "OPENID4VCI_CONFORMANCE_CLIENT_ATTESTER_JWKS_FILE"
        private const val AUTHORIZATION_SERVER_PROP = "openid4vci.conformance.authorization-server"
        private const val AUTHORIZATION_SERVER_ENV = "OPENID4VCI_CONFORMANCE_AUTHORIZATION_SERVER"
        private const val CREDENTIAL_PROOF_TYPE_HINT_PROP = "openid4vci.conformance.credential-proof-type-hint"
        private const val CREDENTIAL_PROOF_TYPE_HINT_ENV = "OPENID4VCI_CONFORMANCE_CREDENTIAL_PROOF_TYPE_HINT"

        private fun propertyOrEnv(property: String, env: String): String? =
            System.getProperty(property) ?: System.getenv(env)

        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        private val enterpriseBaseUrl: String =
            propertyOrEnv(ENTERPRISE_BASE_URL_PROP, ENTERPRISE_BASE_URL_ENV)
                ?: "http://waltid.enterprise.localhost:3000"

        val credentialIssuerUrl: String? =
            propertyOrEnv(CREDENTIAL_ISSUER_URL_PROP, CREDENTIAL_ISSUER_URL_ENV)
                ?: propertyOrEnv(ENTERPRISE_TARGET_PROP, ENTERPRISE_TARGET_ENV)?.let {
                    "$enterpriseBaseUrl/v2/$it/issuer-service-api/openid4vci"
                }

        val sdJwtCredentialConfigurationId: String? =
            propertyOrEnv(SD_JWT_CREDENTIAL_ID_PROP, SD_JWT_CREDENTIAL_ID_ENV)

        val mdocCredentialConfigurationId: String? =
            propertyOrEnv(MDOC_CREDENTIAL_ID_PROP, MDOC_CREDENTIAL_ID_ENV)

        val clientAttestationIssuer: String =
            propertyOrEnv(CLIENT_ATTESTATION_ISSUER_PROP, CLIENT_ATTESTATION_ISSUER_ENV)
                ?: "https://client-attestation.example.com"

        val clientAttesterJwksFile: String? =
            propertyOrEnv(CLIENT_ATTESTER_JWKS_FILE_PROP, CLIENT_ATTESTER_JWKS_FILE_ENV)

        val authorizationServer: String? =
            propertyOrEnv(AUTHORIZATION_SERVER_PROP, AUTHORIZATION_SERVER_ENV)

        val credentialProofTypeHint: String? =
            propertyOrEnv(CREDENTIAL_PROOF_TYPE_HINT_PROP, CREDENTIAL_PROOF_TYPE_HINT_ENV)

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Conformance suite not available at $conformanceHost:$conformancePort")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        @JvmStatic
        val isIssuerConfigured = credentialIssuerUrl != null

        @JvmStatic
        fun canRunTests(): Boolean = isConformanceAvailable && isIssuerConfigured

        init {
            println()
            println("=" .repeat(80))
            println("OpenID4VCI Issuer Conformance Tests")
            println("=" .repeat(80))
            println()
            println("Conformance suite: $conformanceHost:$conformancePort")
            println("Conformance available: $isConformanceAvailable")
            println("Issuer URL: ${credentialIssuerUrl ?: "<not configured>"}")
            println("Issuer configured: $isIssuerConfigured")
            println()

            if (!isConformanceAvailable) {
                println("To start conformance suite:")
                println("  cd ~/dev/openid/conformance-suite")
                println("  docker compose -f docker-compose-walt.yml up -d")
                println()
            }

            if (!isIssuerConfigured) {
                println("To configure issuer URL (choose one):")
                println()
                println("  1. Direct issuer URL:")
                println("     export OPENID4VCI_CONFORMANCE_CREDENTIAL_ISSUER_URL=\"http://localhost:7002\"")
                println()
                println("  2. Enterprise issuer:")
                println("     export OPENID4VCI_CONFORMANCE_ENTERPRISE_TARGET=\"my-org\"")
                println()
            }

            println("=" .repeat(80))
            println()
        }
    }

    @Test
    @EnabledIf("canRunTests")
    fun runIssuerConformanceTests() = runTest(timeout = 30.minutes) {
        assumeTrue(isConformanceAvailable, "OpenID conformance suite is not reachable")
        assumeTrue(isIssuerConfigured, "No credential issuer URL configured")

        val clientAttesterJwks = clientAttesterJwksFile?.let {
            IssuerConformanceTestRunner.loadClientAttesterJwks(it)
        } ?: IssuerConformanceTestRunner.loadClientAttesterJwks()

        val results = IssuerConformanceTestRunner(
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

        println()
        println("=" .repeat(80))
        println("Issuer Conformance Test Results")
        println("=" .repeat(80))
        results.forEachIndexed { i, r ->
            println("  [$i] ${r.conformanceTestId}: status=${r.conformanceStatus}, result=${r.conformanceResult}")
        }
        println()
        println("Total: ${results.size} modules")
        println("Passed: ${results.count { it.conformanceResult == "PASSED" }}")
        println("=" .repeat(80))
    }
}
