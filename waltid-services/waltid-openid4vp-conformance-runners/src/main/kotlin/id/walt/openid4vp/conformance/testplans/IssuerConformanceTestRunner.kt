package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.IssuerTestPlan
import id.walt.openid4vp.conformance.testplans.plans.Oid4vciIssuerClientAttestationDpop
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestPlanRunner
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertNotNull

/**
 * Phase-1 OpenID4VCI issuer conformance runner.
 *
 * Runs the conformance suite against an already-running issuer service.
 * Intended for issuer2 / Enterprise integration where the issuer is the SUT.
 */
class IssuerConformanceTestRunner(
    private val credentialIssuerUrl: String,
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443,
    private val sdJwtCredentialConfigurationId: String? = null,
    private val mdocCredentialConfigurationId: String? = null,
    private val clientAttestationIssuer: String = "https://client-attestation.example.com",
    private val clientAttesterJwks: JsonObject,
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
) {
    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun run(): List<TestPlanResult> {
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        val conformanceVersion = conformance.getServerVersion()
        assertNotNull(conformanceVersion)
        println("✅ Conformance server version $conformanceVersion available!")

        val metadata = fetchIssuerMetadata()
        val testPlans = buildTestPlans(metadata)
        require(testPlans.isNotEmpty()) {
            "No phase-1 issuer plans could be constructed for $credentialIssuerUrl"
        }

        return testPlans.flatMap { plan ->
            val planName = plan::class.simpleName ?: plan::class.jvmName
            println("Running issuer plan: $planName")
            IssuerTestPlanRunner(plan.config, conformanceHost, conformancePort).test()
        }
    }

    private suspend fun fetchIssuerMetadata(): JsonObject {
        val metadataUrl = buildIssuerMetadataUrl(credentialIssuerUrl)
        val metadata = http.get(metadataUrl).body<JsonObject>()
        println("✅ Issuer metadata endpoint responding: $metadataUrl")
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

        println("Resolved phase-1 issuer credential configuration ids:")
        println("  sd-jwt-vc -> ${discoveredSdJwtId ?: "<not found>"}")
        println("  mdoc      -> ${discoveredMdocId ?: "<not found>"}")

        return buildList {
            discoveredSdJwtId?.let {
                add(
                    Oid4vciIssuerClientAttestationDpop(
                        issuerUrl = credentialIssuerUrl,
                        credentialConfigurationId = it,
                        credentialFormat = Oid4vciIssuerClientAttestationDpop.CredentialFormatVariant.SD_JWT_VC,
                        clientAttestationIssuer = clientAttestationIssuer,
                        clientAttesterJwks = clientAttesterJwks,
                        authorizationServer = authorizationServer,
                        credentialProofTypeHint = credentialProofTypeHint,
                    )
                )
            }
            discoveredMdocId?.let {
                add(
                    Oid4vciIssuerClientAttestationDpop(
                        issuerUrl = credentialIssuerUrl,
                        credentialConfigurationId = it,
                        credentialFormat = Oid4vciIssuerClientAttestationDpop.CredentialFormatVariant.MDOC,
                        clientAttestationIssuer = clientAttestationIssuer,
                        clientAttesterJwks = clientAttesterJwks,
                        authorizationServer = authorizationServer,
                        credentialProofTypeHint = credentialProofTypeHint,
                    )
                )
            }
        }
    }

    private fun buildIssuerMetadataUrl(issuerUrl: String): String {
        val issuerUri = URI.create(issuerUrl)
        val issuerPath = issuerUri.path.trimStart('/')
        return "${issuerUri.scheme}://${issuerUri.authority}/.well-known/openid-credential-issuer/$issuerPath"
    }
}
