package id.walt.openid4vp.conformance.testplans

import com.nimbusds.jose.jwk.ECKey
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantRunStatus
import kotlinx.serialization.json.*

/** The ES256 test attester is independent of the OAuth client attester and credential proof keys. */
internal fun parseKeyAttesterJwks(text: String): JsonObject {
    val parsed = Json.parseToJsonElement(text).jsonObject
    val keys = parsed["keys"]?.jsonArray ?: JsonArray(listOf(parsed))
    require(keys.isNotEmpty()) { "Key attester JWKS must contain a private ES256 signing key." }
    val normalized = keys.map { element ->
        val jwk = element.jsonObject
        val key = ECKey.parse(jwk.toString())
        require(key.isPrivate && key.curve.name == "P-256") {
            "Key attester must contain a private P-256 key for ES256 signing."
        }
        require(jwk["alg"]?.jsonPrimitive?.content.let { it == null || it == "ES256" }) {
            "Key attester algorithm must be ES256."
        }
        require(jwk["use"]?.jsonPrimitive?.content.let { it == null || it == "sig" }) {
            "Key attester key use must be sig."
        }
        JsonObject(jwk + mapOf("alg" to JsonPrimitive("ES256"), "use" to JsonPrimitive("sig")))
    }
    return buildJsonObject { put("keys", JsonArray(normalized)) }
}

private const val invalidKeyAttestationSignatureModule = "oid4vci-1_0-issuer-fail-invalid-key-attestation-signature"

internal val keyAttestationAcceptanceModules = setOf(
    "oid4vci-1_0-issuer-happy-flow",
    invalidKeyAttestationSignatureModule,
)

/** Capability skips and locally accepted results are not evidence that attestation was checked. */
internal fun requireExecutedKeyAttestation(results: List<IssuerVariantRunResult>) {
    require(results.isNotEmpty()) { "Key attestation acceptance requires at least one executed variant." }
    val missing = results.filter { variant ->
        variant.status != IssuerVariantRunStatus.PASSED || variant.error != null ||
            keyAttestationAcceptanceModules.any { name ->
                val modules = variant.modules.filter { it.testModule == name }
                // VCIIssuerTestPlanHaip at db1080a does not offer this negative test for
                // encrypted responses. Only absence is allowed; a skip/failure never is.
                val notOffered = name == invalidKeyAttestationSignatureModule &&
                    variant.variant["fapi_profile"]?.jsonPrimitive?.content == "vci_haip" &&
                    variant.variant["vci_grant_type"]?.jsonPrimitive?.content == "authorization_code" &&
                    variant.variant["vci_credential_encryption"]?.jsonPrimitive?.content == "encrypted"
                (modules.isEmpty() && !notOffered) || modules.any {
                    it.testId.isNullOrBlank() || it.status != "FINISHED" || it.result != "PASSED" ||
                        !it.accepted || it.error != null
                }
            }
    }
    require(missing.isEmpty()) {
        "Key attestation happy-flow and invalid-signature tests must execute and pass wherever pinned suite db1080a offers them: " +
            missing.joinToString { it.variantId } +
            ". Check key_attestations_required metadata, module selection, and results.json; skips are not coverage."
    }
    require(results.any { variant -> variant.modules.any { it.testModule == invalidKeyAttestationSignatureModule } }) {
        "No key attestation negative coverage: select a Basic VCI or plain HAIP variant; " +
            "encrypted HAIP does not offer the invalid-signature test in pinned suite db1080a."
    }
}
