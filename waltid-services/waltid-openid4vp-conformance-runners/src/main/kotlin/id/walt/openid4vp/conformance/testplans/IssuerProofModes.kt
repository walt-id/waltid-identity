package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.httpdata.TestLogEntry
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunStatus
import kotlinx.serialization.json.*

internal fun parseIssuerProofModes(value: String?): List<String> {
    if (value == null) return emptyList() // Preserve direct Gradle's single-hint behavior.
    val modes = value.split(',').map(String::trim).distinct()
    require(modes.isNotEmpty() && modes.all { it in setOf("jwt", "attestation") }) {
        "OPENID4VCI_CONFORMANCE_PROOF_MODES must be jwt, attestation, or jwt,attestation."
    }
    return modes
}

internal fun expandIssuerProofModes(variants: List<IssuerVariant>, modes: List<String>): List<IssuerVariant> =
    if (modes.isEmpty()) variants else modes.flatMap { mode -> variants.map { it.copy(credentialProofType = mode) } }

/** Validate the entire selection before creating any suite plans, including later proof modes. */
internal fun preflightIssuerConfigurations(
    metadata: JsonObject,
    variants: List<IssuerVariant>,
    configurationIdFor: (IssuerVariant) -> String?,
): List<IssuerVariantRunResult> = variants.map { variant ->
    val failure = runCatching {
        val configurationId = requireNotNull(configurationIdFor(variant)) {
            "No issuer metadata credential configuration id found for ${variant.credentialFormat}."
        }
        val proofType = variant.credentialProofType
        if (proofType != null) {
            requireIsolatedIssuerProofType(metadata, configurationId, proofType)
        } else {
            require(metadata["credential_configurations_supported"]?.jsonObject?.get(configurationId) is JsonObject) {
                "Issuer metadata is missing credential configuration $configurationId."
            }
        }
    }.exceptionOrNull()
    IssuerVariantRunResult(
        variantId = variant.id,
        variant = variant.toJsonObject(),
        status = if (failure == null) IssuerVariantRunStatus.GENERATED else IssuerVariantRunStatus.BLOCKED,
        error = failure?.let { "${it.javaClass.simpleName}: ${it.message}" },
    )
}

internal fun requireIssuerConfigurationPreflight(results: List<IssuerVariantRunResult>) {
    val blocked = results.filter { it.status == IssuerVariantRunStatus.BLOCKED }
    require(blocked.isEmpty()) {
        "Issuer configuration preflight failed; no conformance tests were started:\n" +
            blocked.mapNotNull { it.error }.distinct().joinToString("\n")
    }
}

internal fun requireIsolatedIssuerProofType(metadata: JsonObject, configurationId: String, proofType: String) {
    val configuration = metadata["credential_configurations_supported"]?.jsonObject?.get(configurationId)?.jsonObject
        ?: error("Issuer metadata is missing credential configuration $configurationId for proof mode $proofType.")
    val types = configuration["proof_types_supported"]?.jsonObject
    require(types?.keys == setOf(proofType)) {
        "$configurationId must advertise only $proofType proofs for isolated coverage; " +
            "suite module preferences can override the proof-type hint."
    }
    val selected = types!!.getValue(proofType).jsonObject
    require(selected["proof_signing_alg_values_supported"]?.jsonArray?.any { it.jsonPrimitive.content == "ES256" } == true) {
        "$configurationId must support ES256 $proofType proofs."
    }
    require(proofType != "jwt" || selected["key_attestations_required"] is JsonObject) {
        "$configurationId must require key attestations for JWT proof coverage."
    }
}
