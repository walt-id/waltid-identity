package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.IssuerInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantReportWriter
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunStatus
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantSelection
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.Oid4vciIssuerVariantPlan
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestPlanRunner
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
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
    private val staticTxCode: String? = System.getenv("OPENID4VCI_CONFORMANCE_STATIC_TX_CODE")?.ifBlank { null },
    private val variantSelection: IssuerVariantSelection = IssuerVariantSelection.fromEnvironment(),
) {
    suspend fun run(): List<TestPlanResult> {
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        return try {
            val metadataHttp = HttpClient {
                install(ContentNegotiation) {
                    json()
                }
            }
            try {
                val conformanceVersion = conformance.getServerVersion()
                assertNotNull(conformanceVersion)
                println("✅ Conformance server version $conformanceVersion available!")

                val metadata = fetchIssuerMetadata(metadataHttp)
                runMatrix(metadata, conformance)
            } finally {
                metadataHttp.close()
            }
        } finally {
            conformance.close()
        }
    }

    private suspend fun runMatrix(
        metadata: JsonObject,
        conformance: ConformanceInterface,
    ): List<TestPlanResult> {
        val resolvedIds = resolveCredentialConfigurationIds(metadata)
        val allVariants = IssuerVariantMatrix.all()
        val selectedVariants = variantSelection.select(allVariants)

        require(selectedVariants.isNotEmpty()) {
            "No OpenID4VCI issuer variants selected. Check OPENID4VCI_CONFORMANCE_VARIANTS and filter environment variables."
        }

        println("Resolved issuer credential configuration ids:")
        println("  sd-jwt-vc -> ${resolvedIds.sdJwt ?: "<not found>"}")
        println("  mdoc      -> ${resolvedIds.mdoc ?: "<not found>"}")
        println("Selected OpenID4VCI issuer variants: ${selectedVariants.size}/${allVariants.size}")

        if (variantSelection.discoveryOnly) {
            val discoveryResults = selectedVariants.map { variant ->
                val credentialConfigurationId = credentialConfigurationIdFor(variant, resolvedIds)
                if (credentialConfigurationId == null) {
                    blockedResult(variant, "No issuer metadata credential configuration id found for ${variant.credentialFormat}.")
                } else {
                    IssuerVariantRunResult(
                        variantId = variant.id,
                        variant = variant.toJsonObject(),
                        status = IssuerVariantRunStatus.GENERATED,
                    )
                }
            }
            IssuerVariantReportWriter.write(variantSelection.reportDir, selectedVariants, discoveryResults)
            println("Wrote issuer conformance discovery artifacts to ${variantSelection.reportDir}")
            return emptyList()
        }

        val issuerBaseUrl = extractBaseUrl(credentialIssuerUrl)
        val issuerInterface = IssuerInterface(issuerBaseUrl)
        val results = mutableListOf<IssuerVariantRunResult>()

        try {
            selectedVariants.forEachIndexed { index, variant ->
                println("Running issuer matrix variant ${index + 1}/${selectedVariants.size}: ${variant.id}")
                val credentialConfigurationId = credentialConfigurationIdFor(variant, resolvedIds)
                if (credentialConfigurationId == null) {
                    results += blockedResult(variant, "No issuer metadata credential configuration id found for ${variant.credentialFormat}.")
                    return@forEachIndexed
                }

                results += runCatching {
                    val plan = Oid4vciIssuerVariantPlan(
                        issuerUrl = credentialIssuerUrl,
                        credentialConfigurationId = credentialConfigurationId,
                        variant = variant,
                        clientAttestationIssuer = clientAttestationIssuer,
                        clientAttesterJwks = clientAttesterJwks,
                        authorizationServer = authorizationServer,
                        credentialProofTypeHint = credentialProofTypeHint,
                        staticTxCode = staticTxCode,
                    )
                    IssuerTestPlanRunner(plan.config, conformance, issuerInterface).attempt(variant)
                }.getOrElse {
                    IssuerVariantRunResult(
                        variantId = variant.id,
                        variant = variant.toJsonObject(),
                        status = IssuerVariantRunStatus.FAILED,
                        error = "${it.javaClass.simpleName}: ${it.message}",
                    )
                }
            }
        } finally {
            issuerInterface.close()
        }

        IssuerVariantReportWriter.write(variantSelection.reportDir, selectedVariants, results)
        println("Wrote issuer conformance matrix artifacts to ${variantSelection.reportDir}")

        if (variantSelection.strictResults) {
            val failingResults = results.filter { it.status != IssuerVariantRunStatus.PASSED }
            require(failingResults.isEmpty()) {
                "OpenID4VCI issuer matrix strict mode failed for ${failingResults.size} variants. " +
                    "See ${variantSelection.reportDir}/summary.md"
            }
        }

        return results.flatMap { result ->
            result.modules.map { module ->
                TestPlanResult(
                    testName = module.testModule,
                    conformanceTestId = module.testId ?: result.variantId,
                    conformanceStatus = module.status ?: result.status.name,
                    conformanceResult = module.result,
                    errorMessage = module.error ?: result.error,
                )
            }
        }
    }

    private suspend fun fetchIssuerMetadata(http: HttpClient): JsonObject {
        val metadataUrl = buildIssuerMetadataUrl(credentialIssuerUrl)
        val response = http.get(metadataUrl)
        val responseBody = response.bodyAsText()

        require(response.status.value in 200..299) {
            "Issuer metadata endpoint returned ${response.status} for $metadataUrl. " +
                "Body: ${responseBody.take(1_000)}"
        }

        val metadata = runCatching {
            Json.parseToJsonElement(responseBody) as? JsonObject
                ?: error("Metadata response is not a JSON object")
        }.getOrElse {
            error(
                "Issuer metadata endpoint returned ${response.status} for $metadataUrl, but the body was not a JSON object. " +
                    "Content-Type: ${response.headers[HttpHeaders.ContentType] ?: "<none>"}. " +
                    "Body: ${responseBody.take(1_000)}"
            )
        }
        println("✅ Issuer metadata endpoint responding: $metadataUrl")
        return metadata
    }

    private data class ResolvedCredentialConfigurationIds(
        val sdJwt: String?,
        val mdoc: String?,
    )

    private fun resolveCredentialConfigurationIds(metadata: JsonObject): ResolvedCredentialConfigurationIds {
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

        return ResolvedCredentialConfigurationIds(
            sdJwt = discoveredSdJwtId,
            mdoc = discoveredMdocId,
        )
    }

    private fun credentialConfigurationIdFor(
        variant: IssuerVariant,
        resolvedIds: ResolvedCredentialConfigurationIds,
    ): String? = when (variant.credentialFormat) {
        "sd_jwt_vc" -> resolvedIds.sdJwt
        "mdoc" -> resolvedIds.mdoc
        else -> null
    }

    private fun blockedResult(variant: IssuerVariant, error: String): IssuerVariantRunResult =
        IssuerVariantRunResult(
            variantId = variant.id,
            variant = variant.toJsonObject(),
            status = IssuerVariantRunStatus.BLOCKED,
            error = error,
        )

    private fun buildIssuerMetadataUrl(issuerUrl: String): String {
        val issuerUri = URI.create(issuerUrl)
        val issuerPath = issuerUri.path.trimStart('/')
        return "${issuerUri.scheme}://${issuerUri.authority}/.well-known/openid-credential-issuer/$issuerPath"
    }

    /**
     * Extract the base URL (scheme + authority) from the credential issuer URL.
     * Example: "https://example.com/openid4vci" -> "https://example.com"
     */
    private fun extractBaseUrl(issuerUrl: String): String {
        val uri = URI.create(issuerUrl)
        return "${uri.scheme}://${uri.authority}"
    }

}
