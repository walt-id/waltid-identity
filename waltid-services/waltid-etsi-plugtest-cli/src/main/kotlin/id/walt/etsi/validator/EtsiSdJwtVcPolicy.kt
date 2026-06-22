package id.walt.etsi.validator

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * ETSI TS 119 472-1 specific checks for SD-JWT VC EAAs.
 *
 * These rules are purely from the ETSI spec and not present in the base IETF SD-JWT VC spec,
 * so they live here in the plugtest CLI rather than in the shared policy library.
 *
 * Checks performed:
 * - SD-JWT VC §3.2.1.1: typ MUST be "dc+sd-jwt"
 * - EAA-5.2.2.1-01: plain EAA SHALL NOT include category claim
 * - QEAA-5.2.2.2-02: QEAA category SHALL be "urn:etsi:esi:eaa:eu:qualified"
 * - PuB-EAA-5.2.2.3-02: PuBEAA category SHALL be "urn:etsi:esi:eaa:eu:pub"
 * - EAA-5.2.4.1-03/07/11: issuing_authority/country/iss_reg_id SHALL NOT be present with a qualified certificate
 * - QEAA-5.2.4.2-01/02: QEAA SHALL include issuing_authority and issuing_country (or have them in QC cert)
 * - PuB-EAA-5.2.4.3-01/02: PuBEAA SHALL include issuing_authority and issuing_country (or have them in QC cert)
 * - EAA-5.2.8.2-05: oneTime claim SHALL have null JSON primitive type
 * - EAA-5.2.10.1-04..11: status object (if present) MUST have type, purpose, index, uri members
 * - QEAA-5.2.10.2-01: QEAA SHALL include status if shortLived is absent
 * - PuB-EAA-5.2.10.3-01: PuBEAA SHALL include status if shortLived is absent
 * - EAA-5.2.12-02: shortLived claim SHALL have null JSON primitive type
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
                val certDer = x5cList[0].decodeFromBase64()
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certDer.inputStream()) as X509Certificate
                cert.criticalExtensionOIDs.contains(QC_STATEMENTS_OID) ||
                cert.nonCriticalExtensionOIDs.contains(QC_STATEMENTS_OID)
            } else false
        }.getOrElse { false }

        // --- Determine credential category from the category claim ---
        val category = (payload["category"] as? JsonPrimitive)?.content
        val isQeaa   = category == "urn:etsi:esi:eaa:eu:qualified"
        val isPubEaa = category == "urn:etsi:esi:eaa:eu:pub"
        val isPlainEaa = !isQeaa && !isPubEaa

        // EAA-5.2.2.1-01: plain EAA SHALL NOT include the category claim
        if (isPlainEaa && payload.containsKey("category")) {
            violations += "'category' SHALL NOT be present for a plain EAA (EAA-5.2.2.1-01)"
        }

        // QEAA-5.2.2.2-02: QEAA category SHALL be "urn:etsi:esi:eaa:eu:qualified"
        // PuB-EAA-5.2.2.3-02: PuBEAA category SHALL be "urn:etsi:esi:eaa:eu:pub"
        // (detected via wrong value when category is present but doesn't match either known value)
        if (category != null && !isQeaa && !isPubEaa) {
            violations += "'category' value \"$category\" is not a recognised EAA category URN (QEAA-5.2.2.2-02, PuB-EAA-5.2.2.3-02)"
        }

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
        } else {
            // When cert is NOT qualified, QEAA/PuBEAA must carry issuing_authority and issuing_country
            // in the payload - unless they're derivable from the certificate subject (C= attribute covers country)
            if (isQeaa || isPubEaa) {
                val prefix = if (isQeaa) "QEAA" else "PuB-EAA"
                val (authReq, countryReq) = if (isQeaa)
                    "QEAA-5.2.4.2-01" to "QEAA-5.2.4.2-02"
                else
                    "PuB-EAA-5.2.4.3-01" to "PuB-EAA-5.2.4.3-02"

                // issuing_authority must come from the claim if the cert isn't qualified
                if (!payload.containsKey("issuing_authority")) {
                    violations += "$prefix SHALL include 'issuing_authority' when signing certificate is not qualified ($authReq)"
                }
                // issuing_country: the C= attribute in the cert subject is an acceptable alternative per
                // ETSI EN 319 412-6 - only flag if neither the claim nor a cert C= value is present
                val certHasCountry = runCatching {
                    val x5cArray = jwtHeader?.get("x5c") as? JsonArray
                    val x5cList = x5cArray?.mapNotNull { (it as? JsonPrimitive)?.content }
                    if (!x5cList.isNullOrEmpty()) {
                        val certDer = x5cList[0].decodeFromBase64()
                        val cert = CertificateFactory.getInstance("X.509")
                            .generateCertificate(certDer.inputStream()) as X509Certificate
                        cert.subjectX500Principal.name.contains("C=")
                    } else false
                }.getOrElse { false }

                if (!payload.containsKey("issuing_country") && !certHasCountry) {
                    violations += "$prefix SHALL include 'issuing_country' when signing certificate is not qualified and cert has no C= attribute ($countryReq)"
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

        // QEAA-5.2.10.2-01 / PuB-EAA-5.2.10.3-01: status is mandatory unless shortLived is present
        if ((isQeaa || isPubEaa) && !payload.containsKey("shortLived") && !payload.containsKey("status")) {
            val reqId = if (isQeaa) "QEAA-5.2.10.2-01" else "PuB-EAA-5.2.10.3-01"
            violations += "${if (isQeaa) "QEAA" else "PuB-EAA"} SHALL include 'status' when 'shortLived' is absent ($reqId)"
        }

        // --- oneTime and shortLived SHALL be null (EAA-5.2.8.2-05, EAA-5.2.12-02) ---
        for ((claim, reqId) in listOf(
            "oneTime"    to "EAA-5.2.8.2-05",
            "shortLived" to "EAA-5.2.12-02"
        )) {
            val value = payload[claim]
            if (value != null && value !is JsonNull) {
                violations += "'$claim' SHALL have null JSON primitive type, got ${value::class.simpleName} ($reqId)"
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
