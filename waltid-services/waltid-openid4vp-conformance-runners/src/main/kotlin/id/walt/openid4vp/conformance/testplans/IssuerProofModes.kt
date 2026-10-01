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
