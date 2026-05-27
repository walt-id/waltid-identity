package id.walt.etsi.validator

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.JwtBasedSignature
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * ETSI TS 119 472-1 specific checks for SD-JWT VC EAAs.
 *
 * These rules are purely from the ETSI spec and not present in the base IETF SD-JWT VC spec,
 * so they live here in the plugtest CLI rather than in the shared policy library.
 *
 * Checks performed:
 * - EAA-5.2.4.1-03: issuing_authority SHALL NOT be present with a qualified certificate
 * - EAA-5.2.4.1-07: issuing_country SHALL NOT be present with a qualified certificate
 * - EAA-5.2.4.1-11: iss_reg_id SHALL NOT be present with a qualified certificate
 * - EAA-5.2.10.1-04..11: status object (if present) MUST have type, purpose, index, uri members
 */
class EtsiSdJwtVcPolicy {
    val id = "etsi/sd-jwt-vc"

    // qcStatements extension OID (RFC 3739 / ETSI EN 319 412-5)
    private val QC_STATEMENTS_OID = "1.3.6.1.5.5.7.1.3"

    fun verify(credential: DigitalCredential): Result<JsonObject> {
        val violations = mutableListOf<String>()

        val jwtHeader = (credential.signature as? JwtBasedSignature)?.jwtHeader
        val payload = credential.credentialData

        // SD-JWT VC spec §3.2.1.1: typ MUST be "dc+sd-jwt"
        val typ = (jwtHeader?.get("typ") as? JsonPrimitive)?.content
        if (typ != null && typ != "dc+sd-jwt") {
            violations += "Header 'typ' is \"$typ\", MUST be \"dc+sd-jwt\" (SD-JWT VC spec §3.2.1.1)"
        }

        // --- QC certificate check: extract leaf cert and check for qcStatements ---
        val leafCertIsQualified: Boolean = runCatching {
            val x5cArray = jwtHeader?.get("x5c") as? JsonArray
            val x5cList = x5cArray?.mapNotNull { (it as? JsonPrimitive)?.content }
            if (!x5cList.isNullOrEmpty()) {
                val certDer = Base64.getDecoder().decode(x5cList[0])
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certDer.inputStream()) as X509Certificate
                cert.criticalExtensionOIDs.contains(QC_STATEMENTS_OID) ||
                cert.nonCriticalExtensionOIDs.contains(QC_STATEMENTS_OID)
            } else false
        }.getOrElse { false }

        if (leafCertIsQualified) {
            // EAA-5.2.4.1-03/07/11: forbidden claims when signing cert is qualified
            for ((claim, reqId) in listOf(
                "issuing_authority" to "EAA-5.2.4.1-03",
                "issuing_country"   to "EAA-5.2.4.1-07",
                "iss_reg_id"        to "EAA-5.2.4.1-11"
            )) {
                if (payload.containsKey(claim)) {
                    violations += "'$claim' SHALL NOT be present when signing certificate is qualified ($reqId)"
                }
            }
        }

        // --- status structure check (EAA-5.2.10.1-04..11) ---
        val statusObj = runCatching { payload["status"]?.jsonObject }.getOrNull()
        if (statusObj != null) {
            for ((member, reqId) in listOf(
                "type"    to "EAA-5.2.10.1-04",
                "purpose" to "EAA-5.2.10.1-06",
                "index"   to "EAA-5.2.10.1-08",
                "uri"     to "EAA-5.2.10.1-10"
            )) {
                if (!statusObj.containsKey(member)) {
                    violations += "'status' missing required member '$member' ($reqId)"
                }
            }
        }

        return if (violations.isEmpty()) {
            Result.success(buildJsonObject { put("etsi_checks_passed", true) })
        } else {
            Result.failure(IllegalArgumentException(
                "ETSI TS 119 472-1 violations: ${violations.joinToString("; ")}"
            ))
        }
    }
}
