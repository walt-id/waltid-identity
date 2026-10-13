package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.http.IssuerInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.*
import id.walt.openid4vp.conformance.testplans.runner.IssuerTestPlanRunner
import id.walt.openid4vp.conformance.utils.JsonUtils.lenientJson
import io.ktor.client.*
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
    private val haipSdJwtCredentialConfigurationId: String? = null,
    private val haipMdocCredentialConfigurationId: String? = null,
    private val clientAttestationIssuer: String = "https://client-attestation.example.com",
    private val clientAttesterJwks: JsonObject,
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
    private val staticTxCode: String? = System.getenv("OPENID4VCI_CONFORMANCE_STATIC_TX_CODE")?.ifBlank { null },
    // Also accepts a *_PEM_FILE path: CI cannot pass a multi-line PEM through an environment
    // variable reliably, and inline values keep precedence so local invocations are unaffected.
    private val credentialTrustAnchorPem: String? = resolvePemFromEnvironment(
        inlinePemEnvironmentVariable = "OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM",
        pemFileEnvironmentVariable = "OPENID4VCI_CONFORMANCE_CREDENTIAL_TRUST_ANCHOR_PEM_FILE",
    ),
    private val statusListTrustAnchorPem: String? = resolvePemFromEnvironment(
        inlinePemEnvironmentVariable = "OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM",
        pemFileEnvironmentVariable = "OPENID4VCI_CONFORMANCE_STATUS_LIST_TRUST_ANCHOR_PEM_FILE",
    ),
    private val variantSelection: IssuerVariantSelection = IssuerVariantSelection.fromEnvironment(),
    private val requireBatchPass: Boolean = System.getenv("OPENID4VCI_CONFORMANCE_REQUIRE_BATCH_PASS")
        ?.toBooleanStrict() ?: false,
    private val keyAttesterJwks: JsonObject? = null,
    private val requireKeyAttestationPass: Boolean = System.getenv("OPENID4VCI_CONFORMANCE_REQUIRE_KEY_ATTESTATION_PASS")
        ?.toBooleanStrict() ?: false,
    private val proofModes: List<String> = parseIssuerProofModes(System.getenv("OPENID4VCI_CONFORMANCE_PROOF_MODES")),
) {
    suspend fun run(): List<TestPlanResult> {
        // Even failures before metadata is available must not leave an earlier run's passing reports.
        IssuerVariantReportWriter.prepareForRun(variantSelection.reportDir)
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        return try {
            val metadataHttp = HttpClient {
                install(ContentNegotiation) {
                    // Tolerate response fields added by newer conformance-suite releases
                    json(lenientJson)
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
        val selectedVariants = variantSelection.select(expandIssuerProofModes(allVariants, proofModes))
        val protocolVariants = selectedVariants.map { it.copy(credentialProofType = null) }.distinct()

        require(selectedVariants.isNotEmpty()) {
            "No OpenID4VCI issuer variants selected. Check OPENID4VCI_CONFORMANCE_VARIANTS and filter environment variables."
        }
        require(!requireBatchPass || !variantSelection.discoveryOnly) {
            "Batch acceptance requires executed modules; discovery mode cannot verify batch issuance."
        }
        require(!requireKeyAttestationPass || (!variantSelection.discoveryOnly && keyAttesterJwks != null)) {
            "Key attestation acceptance requires executed modules and OPENID4VCI_CONFORMANCE_KEY_ATTESTER_JWKS_FILE."
        }

        println("Resolved issuer credential configuration ids:")
        println("  sd-jwt-vc -> ${resolvedIds.sdJwt ?: "<not found>"}")
        println("  mdoc      -> ${resolvedIds.mdoc ?: "<not found>"}")
        println("  haip sd-jwt-vc -> ${resolvedIds.haipSdJwt ?: "<not found>"}")
        println("  haip mdoc      -> ${resolvedIds.haipMdoc ?: "<not found>"}")
        println("Selected OpenID4VCI issuer variants: ${protocolVariants.size}/${allVariants.size}; ${selectedVariants.size} proof-mode runs")

        val preflightResults = preflightIssuerConfigurations(metadata, selectedVariants) { variant ->
            credentialConfigurationIdFor(variant, resolvedIds)
        }
        IssuerVariantReportWriter.write(variantSelection.reportDir, selectedVariants, preflightResults, variantSelection.strictResults)
        requireIssuerConfigurationPreflight(preflightResults)

        if (variantSelection.discoveryOnly) {
            println("Wrote issuer conformance discovery artifacts to ${variantSelection.reportDir}")
            return emptyList()
        }

        val issuerBaseUrl = extractBaseUrl(credentialIssuerUrl)
        val issuerInterface = IssuerInterface(issuerBaseUrl)
        val results = mutableListOf<IssuerVariantRunResult>()

        try {
            selectedVariants.forEachIndexed { index, variant ->
                println("Running issuer matrix variant ${index + 1}/${selectedVariants.size}: ${variant.id}")
                val credentialConfigurationId = requireNotNull(credentialConfigurationIdFor(variant, resolvedIds))

                results += runCatching {
                    val plan = Oid4vciIssuerVariantPlan(
                        issuerUrl = credentialIssuerUrl,
                        credentialConfigurationId = credentialConfigurationId,
                        variant = variant,
                        clientAttestationIssuer = clientAttestationIssuer,
                        clientAttesterJwks = clientAttesterJwks,
                        keyAttesterJwks = keyAttesterJwks,
                        authorizationServer = authorizationServer,
                        credentialProofTypeHint = variant.credentialProofType ?: credentialProofTypeHint,
                        staticTxCode = staticTxCode,
                        credentialTrustAnchorPem = credentialTrustAnchorPem,
                        statusListTrustAnchorPem = statusListTrustAnchorPem,
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

        IssuerVariantReportWriter.write(variantSelection.reportDir, selectedVariants, results, variantSelection.strictResults)
        println("Wrote issuer conformance matrix artifacts to ${variantSelection.reportDir}")
        println(IssuerVariantReportWriter.batchCoverageSummary(results))
        results.filter { it.batchCoverage == IssuerBatchCoverageStatus.NOT_OFFERED_BY_PINNED_SUITE }.forEach {
            println("Batch not offered by pinned suite db1080a (not batch coverage): ${it.variantId}")
        }

        val testPlanResults = issuerResultsToTestPlanResults(results)
        ConformanceReportWriter.failIfNeededFromTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_ISSUER,
            results = testPlanResults,
        )

        // Ordinary runs retain capability-based skips. Batch acceptance checks raw suite outcomes.
        // Write the unmodified reports first so missing, skipped, and failed modules remain diagnosable.
        // Each proof mode must independently satisfy the coverage requirements.
        results.groupBy { it.variant["credential_proof_type"]?.jsonPrimitive?.content }.forEach { (mode, modeResults) ->
            println("Checking issuer coverage for proof mode ${mode ?: credentialProofTypeHint ?: "auto"}")
            if (requireBatchPass) requireExecutedBatchIssuance(modeResults)
            if (requireKeyAttestationPass) requireExecutedKeyAttestation(modeResults)
        }

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
        val haipSdJwt: String?,
        val haipMdoc: String?,
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
            haipSdJwt = haipSdJwtCredentialConfigurationId ?: discoveredSdJwtId,
            haipMdoc = haipMdocCredentialConfigurationId ?: discoveredMdocId,
        )
    }

    private fun credentialConfigurationIdFor(
        variant: IssuerVariant,
        resolvedIds: ResolvedCredentialConfigurationIds,
    ): String? = when (variant.credentialFormat) {
        "sd_jwt_vc" -> if (variant.isHaip) resolvedIds.haipSdJwt else resolvedIds.sdJwt
        "mdoc" -> if (variant.isHaip) resolvedIds.haipMdoc else resolvedIds.mdoc
        else -> null
    }?.let { id -> if (variant.credentialProofType == "attestation") "$id.attestation" else id }

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

internal fun issuerResultsToTestPlanResults(results: List<IssuerVariantRunResult>): List<TestPlanResult> =
    results.flatMap { result ->
        if (result.modules.isEmpty()) {
            listOf(
                TestPlanResult(
                    testName = result.variantId,
                    conformanceTestId = result.planId ?: result.variantId,
                    conformanceResult = if (result.status == IssuerVariantRunStatus.PASSED) "PASSED" else result.status.name,
                    errorMessage = result.error.takeIf { result.status != IssuerVariantRunStatus.PASSED },
                )
            )
        } else {
            result.modules.map { module ->
                val skipped = module.accepted && (
                    module.result.equals("SKIPPED", ignoreCase = true) ||
                        module.status.equals("SKIPPED", ignoreCase = true)
                )
                TestPlanResult(
                    testName = "${result.variantId}/${module.testModule}",
                    conformanceTestId = module.testId ?: result.variantId,
                    conformanceStatus = module.status ?: result.status.name,
                    conformanceResult = module.result,
                    errorMessage = (module.error ?: result.error).takeIf { !module.accepted },
                    skipReason = "Suite skipped this module".takeIf { skipped },
                )
            }
        }
    }

internal fun requireExecutedBatchIssuance(results: List<IssuerVariantRunResult>) {
    require(results.isNotEmpty()) { "Batch acceptance requires at least one executed variant." }
    val missingCoverage = results.filter {
        it.batchCoverage == IssuerBatchCoverageStatus.MISSING ||
            it.batchCoverage == IssuerBatchCoverageStatus.NOT_PASSED
    }
    require(missingCoverage.isEmpty()) {
        "Batch issuance must execute and pass for every selected variant where db1080a offers it. Missing batch coverage for: " +
            missingCoverage.joinToString { it.variantId } +
            ". Check results.json and suite logs; skipped, excluded, or unselected modules are not batch coverage."
    }
    require(results.any { it.batchCoverage == IssuerBatchCoverageStatus.PASSED }) {
        "No batch coverage: the selected variants do not offer batch in pinned suite db1080a. " +
            "Include basic VCI or plain HAIP variants when requiring batch acceptance."
    }
    val unsuccessfulVariants = results.filter { it.status != IssuerVariantRunStatus.PASSED || it.error != null }
    require(unsuccessfulVariants.isEmpty()) {
        "Batch acceptance also requires successful variant results. Unsuccessful variants: " +
            unsuccessfulVariants.joinToString { "${it.variantId} (${it.status})" } +
            ". Check summary.md and results.json."
    }
}
