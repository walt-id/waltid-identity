package id.walt.ades.jades

import id.walt.ades.crypto.AdesCrypto
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSignatureAlgorithm
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesSignatureValidator
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.SignatureEvidence
import id.walt.ades.validation.SignatureValidationResult
import id.walt.ades.validation.TimestampEvidence
import id.walt.ades.validation.TimestampType
import id.walt.ades.validation.ValidationCheck
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.Instant

/**
 * Validates JAdES signatures (ETSI TS 119 182-1) in compact, flattened or general JWS JSON serialization:
 * format checks, signature value, time-stamp imprints (adoTst, sigTst, arcTst), embedded validation data and
 * level detection, then the EN 319 102-1 processes of [AdesSignatureValidator]. Counter signatures (cSig) are
 * reported as additional signatures.
 */
class JadesValidator(
    private val policy: AdesValidationPolicy = AdesValidationPolicy(),
    private val contentResolver: DetachedContentResolver? = null,
    /** Additional crit header names the caller processes itself. */
    private val understoodCriticalHeaders: Set<String> = emptySet(),
) {
    suspend fun validate(
        signature: String,
        detachedPayload: ByteArray? = null,
        detachedObjects: List<DetachedObject> = emptyList(),
    ): DocumentValidationResult {
        val document = runCatching { JadesDocument.parse(signature) }.getOrElse {
            return DocumentValidationResult(FORMAT, emptyList(), listOf(ValidationCheck("format:jws", CheckStatus.FAILED, "not a valid JWS: ${it.message}", "RFC 7515 / TS 119 182-1 4")))
        }
        return validate(document, detachedPayload, detachedObjects)
    }

    suspend fun validate(
        document: JadesDocument,
        detachedPayload: ByteArray? = null,
        detachedObjects: List<DetachedObject> = emptyList(),
    ): DocumentValidationResult {
        val resolver = DetachedContentResolver.chain(
            DetachedContentResolver.of(detachedObjects), contentResolver, policy.transport?.let(DetachedContentResolver::http),
        )
        val documentChecks = mutableListOf<ValidationCheck>()
        if (document.serialization == JadesSerialization.JSON_GENERAL && document.signatures.map { it.base64UrlPayload }.distinct().size > 1) {
            documentChecks += ValidationCheck("format:b64", CheckStatus.FAILED, "all signatures of a JWS must use the same b64 value", "RFC 7797 3")
        }
        val results = document.signatures.flatMapIndexed { i, s -> validateSignature(document, s, "sig-$i", detachedPayload, resolver, emptyList()) }
        return DocumentValidationResult(FORMAT, results, documentChecks)
    }

    private suspend fun validateSignature(
        document: JadesDocument,
        signature: JadesSignature,
        id: String,
        detachedPayload: ByteArray?,
        resolver: DetachedContentResolver,
        initialChecks: List<ValidationCheck>,
    ): List<SignatureValidationResult> {
        val checks = initialChecks.toMutableList()
        fun fail(check: String, message: String, ref: String) {
            checks += ValidationCheck("format:$check", CheckStatus.FAILED, message, ref)
        }
        fun warn(check: String, message: String, ref: String) {
            checks += ValidationCheck("format:$check", CheckStatus.WARNING, message, ref)
        }

        val header = runCatching { signature.protectedHeader }.getOrElse {
            fail("protected-header", "JWS Protected Header is not a base64url-encoded JSON object: ${it.message}", "RFC 7515 7.2")
            return listOf(AdesSignatureValidator(policy).validate(SignatureEvidence(id, checks, null, null, false)))
        }

        // alg (5.1.2)
        val algorithm = header.string(JadesHeaders.ALG)?.let { runCatching { AdesSignatureAlgorithm.fromJwsName(it) }.getOrNull() }
        if (algorithm == null) fail("alg", "missing or unsupported alg '${header[JadesHeaders.ALG]}'", "TS 119 182-1 5.1.2")

        // crit (5.1.9) and b64 (5.1.10, RFC 7797)
        val b64Element = header[JadesHeaders.B64]
        if (b64Element != null && (b64Element as? JsonPrimitive)?.booleanOrNull == null) fail("b64", "b64 must be a boolean", "RFC 7797 3")
        val b64 = signature.base64UrlPayload
        val critElement = header[JadesHeaders.CRIT]
        val crit = if (critElement == null) emptyList() else (critElement as? JsonArray)?.strings()?.takeIf { it.isNotEmpty() }
            ?: emptyList<String>().also { fail("crit", "crit must be a non-empty array of strings", "RFC 7515 4.1.11") }
        for (name in crit) {
            when {
                name in JadesHeaders.JWS_REGISTERED -> fail("crit", "crit must not list header parameter '$name' defined by RFC 7515/7518", "RFC 7515 4.1.11")
                name !in JadesHeaders.UNDERSTOOD_CRITICAL && name !in understoodCriticalHeaders -> fail("crit", "critical header parameter '$name' is not understood", "RFC 7515 4.1.11")
                name !in header -> fail("crit", "critical header parameter '$name' is absent", "RFC 7515 4.1.11")
            }
        }
        if (!b64 && JadesHeaders.B64 !in crit) fail("crit", "b64=false requires 'b64' in crit", "RFC 7797 6")

        // Unprotected header (clause 4, 5.3.1)
        if (signature.otherUnprotected.isNotEmpty()) warn("unprotected-header", "JWS Unprotected Header contains ${signature.otherUnprotected.keys} besides etsiU", "TS 119 182-1 4")
        if (signature.etsiU.map { it.isBase64Url }.distinct().size > 1) fail("etsiU", "etsiU mixes clear and base64url-encoded components", "TS 119 182-1 5.3.1")
        if (signature.otherUnprotected.containsKey(JadesHeaders.ETSI_U)) fail("etsiU", "malformed etsiU", "TS 119 182-1 5.3.1")
        signature.etsiU.filter { it.name !in JadesHeaders.ETSI_U_COMPONENTS }.forEach {
            checks += ValidationCheck("format:etsiU", CheckStatus.INFO, "unknown etsiU component ${it.name} ignored", "TS 119 182-1 5.3.1")
        }
        listOf(JadesHeaders.X_VALS, JadesHeaders.R_VALS, JadesHeaders.AX_VALS, JadesHeaders.AR_VALS).forEach { n ->
            if (signature.components(n).size > 1) warn("etsiU", "more than one $n component", "TS 119 182-1 6.3")
        }

        // Signing certificate references (5.1.6, 5.1.7, 5.2.2)
        if (JadesHeaders.X5T in header) warn("x5t", "JAdES signatures shall not contain x5t", "TS 119 182-1 5.1.6")
        if (listOf(JadesHeaders.X5T_S256, JadesHeaders.X5C, JadesHeaders.SIG_X5TS, JadesHeaders.X5T_O).none { it in header }) {
            fail("signing-certificate-reference", "none of x5t#S256, x5c, sigX5ts or x5t#o is present", "TS 119 182-1 5.1.7")
        }
        header.obj(JadesHeaders.X5T_O)?.string("digAlg")?.let { if (JadesDigestNames.parse(it) == AdesDigestAlgorithm.SHA256) warn("x5t#o", "x5t#o must not use SHA-256", "TS 119 182-1 5.2.2.2") }
        if ((header.array(JadesHeaders.SIG_X5TS)?.size ?: 2) < 2) warn("sigX5ts", "sigX5ts must contain at least two references", "TS 119 182-1 5.2.2.3")

        // Claimed signing time (5.1.11, 5.2.1, Table 1 a)
        val iat = (header[JadesHeaders.IAT] as? JsonPrimitive)
        val sigT = header.string(JadesHeaders.SIG_T)
        var claimedTime: Instant? = null
        if (iat != null) claimedTime = iat.longOrNullStrict()?.let(Instant::fromEpochSeconds) ?: null.also { fail("iat", "iat must be an integer NumericDate", "TS 119 182-1 5.1.11") }
        if (sigT != null) {
            val parsed = runCatching { Instant.parse(sigT) }.getOrNull()
            if (parsed == null || !sigT.endsWith("Z")) fail("sigT", "sigT must be an RFC 3339 UTC date-time", "TS 119 182-1 5.2.1")
            else if (parsed.nanosecondsOfSecond != 0 || '.' in sigT) warn("sigT", "sigT shall not contain fractions of seconds", "TS 119 182-1 5.2.1")
            if (claimedTime == null) claimedTime = parsed
        }
        if (iat == null && sigT == null) fail("claimed-signing-time", "neither iat nor sigT is present", "TS 119 182-1 6.3 Table 1")
        if (iat != null && sigT != null) warn("claimed-signing-time", "both iat and sigT are present", "TS 119 182-1 6.3 a)")

        // sigD (5.2.8)
        val sigD = header[JadesHeaders.SIG_D]
        if (sigD != null) {
            checkSigD(sigD, b64, document, crit, ::fail)
            if (JadesHeaders.CTY in header) warn("cty", "cty should not be present together with sigD", "TS 119 182-1 5.1.3")
        }

        // Signed data and signature value
        val signedData = SignedDataProcessor.process(document, signature, detachedPayload, resolver)
        signedData.problems.forEach { fail("sigD", it, "TS 119 182-1 5.2.8") }
        signedData.missing.forEach { checks += ValidationCheck("signed-data", CheckStatus.FAILED, it, "TS 119 182-1 5.2.8.3") }
        signedData.hashMismatches.forEach { checks += ValidationCheck("signed-data:hashV", CheckStatus.FAILED, it, "TS 119 182-1 5.2.8.3.3") }

        val embeddedProblems = mutableListOf<String>()
        val embedded = JadesComponents.embeddedValidationData(signature, embeddedProblems)
        embeddedProblems.forEach { warn("validation-data", it, "TS 119 182-1 5.3.5") }

        val identified = SigningCertificateResolver.resolve(header, embedded.certificates + policy.trustAnchors)
        identified.problems.forEach { checks += ValidationCheck("x509:signing-certificate-reference", CheckStatus.FAILED, it, "EN 319 102-1 5.2.3") }
        identified.warnings.forEach { checks += ValidationCheck("x509:signing-certificate-reference", CheckStatus.WARNING, it, "TS 119 182-1 5.1.4") }
        val signer = identified.certificate
        if (signer != null) {
            checks += ValidationCheck("x509:signing-certificate-reference", CheckStatus.PASSED, "signing certificate ${signer.subject} identified", "EN 319 102-1 5.2.3")
            for (ref in identified.otherReferences) {
                val alg = ref.algorithm
                val ok = alg != null && ref.digest != null && embedded.certificates.any { alg.digest(it.encoded).contentEquals(ref.digest) }
                if (!ok) checks += ValidationCheck("x509:sigX5ts", CheckStatus.INFO, "sigX5ts reference not matched by an embedded certificate", "TS 119 182-1 5.2.2.3")
            }
        }

        val signatureValid = signer != null && algorithm != null && signedData.signingContribution != null &&
            AdesCrypto.verify(signer, algorithm, SignedDataProcessor.signingInput(signature.protectedB64, signedData.signingContribution), signature.signatureValue)

        // Signature policy (5.2.7, 5.3.3)
        val policyError = checkPolicy(header, signature, checks)

        // Time-stamps
        val contentTs = mutableListOf<TimestampEvidence>()
        val sigTs = mutableListOf<TimestampEvidence>()
        val arcTs = mutableListOf<TimestampEvidence>()
        header[JadesHeaders.ADO_TST]?.let { ado ->
            val c = JadesComponents.readTstContainer(ado)
            c.problems.forEach { fail("adoTst", it, "TS 119 182-1 5.4.3.3") }
            if (c.canonAlg != null) warn("adoTst", "adoTst shall not contain canonAlg", "TS 119 182-1 5.2.6")
            c.tokens.forEachIndexed { i, t -> contentTs += TimestampEvidence(TimestampType.CONTENT, t, signedData.timestampContribution, "adoTst#$i") }
        }
        signature.etsiU.forEachIndexed { index, item ->
            when (item.name) {
                JadesHeaders.SIG_TST -> {
                    val c = JadesComponents.readTstContainer(item.value)
                    c.problems.forEach { fail("sigTst", it, "TS 119 182-1 5.4.3.3") }
                    if (c.canonAlg != null) warn("sigTst", "sigTst shall not contain canonAlg", "TS 119 182-1 5.3.4")
                    if (c.tokens.size > 1) warn("sigTst", "each sigTst shall contain only one time-stamp", "TS 119 182-1 6.3 c)")
                    c.tokens.forEachIndexed { i, t ->
                        sigTs += TimestampEvidence(TimestampType.SIGNATURE, t, JadesImprints.signatureTimestampInput(signature), "sigTst[$index]#$i")
                    }
                }

                JadesHeaders.ARC_TST -> {
                    val c = JadesComponents.readTstContainer(item.value)
                    c.problems.forEach { fail("arcTst", it, "TS 119 182-1 5.4.3.3") }
                    if (item.isBase64Url && c.canonAlg != null) warn("arcTst", "canonAlg shall be absent with base64url incorporation", "TS 119 182-1 5.3.1")
                    if (!item.isBase64Url && c.canonAlg == null) warn("arcTst", "canonAlg shall be present with clear incorporation", "TS 119 182-1 5.3.1")
                    val covered = signedData.timestampContribution?.let {
                        JadesImprints.archiveTimestampInput(it, signature, signature.etsiU.subList(0, index), c.canonAlg)
                    }
                    if (signedData.timestampContribution != null && covered == null) warn("arcTst", "unsupported canonAlg ${c.canonAlg}", "TS 119 182-1 5.3.6.2.4")
                    c.tokens.forEachIndexed { i, t -> arcTs += TimestampEvidence(TimestampType.ARCHIVE, t, covered, "arcTst[$index]#$i") }
                }
            }
        }

        // Level detection (6.3 Table 1)
        val hasLtData = signature.etsiU.any { it.name in setOf(JadesHeaders.X_VALS, JadesHeaders.R_VALS, JadesHeaders.ANY_VAL_DATA) }
        var level = AdesLevel.B_B
        if (sigTs.isNotEmpty()) {
            level = AdesLevel.B_T
            if (hasLtData) {
                level = AdesLevel.B_LT
                if (arcTs.isNotEmpty()) level = AdesLevel.B_LTA
                if (signature.etsiU.any { it.name in setOf(JadesHeaders.X_REFS, JadesHeaders.R_REFS, JadesHeaders.AX_REFS, JadesHeaders.AR_REFS) }) {
                    warn("level", "xRefs/rRefs/axRefs/arRefs shall not be present at B-LT and B-LTA", "TS 119 182-1 6.3 Table 1")
                }
            }
        }
        checks += ValidationCheck("level", CheckStatus.INFO, "JAdES-${level.code}", "TS 119 182-1 6")

        val evidence = SignatureEvidence(
            signatureId = id,
            checks = checks,
            signingCertificate = signer,
            signatureAlgorithm = algorithm,
            signatureValueValid = signatureValid,
            signedDataFound = signedData.signingContribution != null && signedData.missing.isEmpty(),
            hashFailure = signedData.hashMismatches.isNotEmpty(),
            policyError = policyError,
            claimedSigningTime = claimedTime,
            certificates = embedded.certificates,
            crls = embedded.crls,
            ocsps = embedded.ocsps,
            contentTimestamps = contentTs,
            signatureTimestamps = sigTs,
            archiveTimestamps = arcTs,
            level = level,
        )
        val results = mutableListOf(AdesSignatureValidator(policy).validate(evidence))

        // Counter signatures (5.3.2): JWS whose payload is the JWS Signature Value of this signature
        signature.etsiU.forEachIndexed { index, item ->
            if (item.name != JadesHeaders.C_SIG) return@forEachIndexed
            val csId = "$id-cSig$index"
            val counter = runCatching { JadesDocument.parse(item.value) }.getOrElse {
                val bad = ValidationCheck("format:cSig", CheckStatus.FAILED, "cSig is not a JWS: ${it.message}", "TS 119 182-1 5.3.2")
                results += AdesSignatureValidator(policy).validate(SignatureEvidence(csId, listOf(bad), null, null, false))
                return@forEachIndexed
            }
            val extra = mutableListOf<ValidationCheck>()
            if (!counter.isDetached) {
                val cs = counter.signatures.first()
                val payload = runCatching { if (cs.base64UrlPayload) JadesEncoding.fromB64url(counter.payloadSegment!!) else counter.payloadSegment!!.encodeToByteArray() }.getOrNull()
                if (payload == null || !payload.contentEquals(signature.signatureValue)) {
                    extra += ValidationCheck("format:cSig", CheckStatus.FAILED, "counter signature payload is not the counter-signed JWS Signature Value", "TS 119 182-1 5.3.2")
                }
            }
            counter.signatures.forEachIndexed { j, cs ->
                results += validateSignature(counter, cs, if (counter.signatures.size > 1) "$csId-$j" else csId, signature.signatureValue, resolver, extra)
            }
        }
        return results
    }

    private fun checkSigD(
        element: JsonElement,
        b64: Boolean,
        document: JadesDocument,
        crit: List<String>,
        fail: (String, String, String) -> Unit,
    ) {
        val ref = "TS 119 182-1 5.2.8"
        val sigD = element as? JsonObject ?: return fail("sigD", "sigD must be a JSON object", ref)
        if (JadesHeaders.SIG_D !in crit) fail("crit", "sigD requires 'sigD' in crit", "TS 119 182-1 5.1.9")
        if (!document.isDetached) fail("sigD", "sigD shall not be present when the JWS Payload is attached", ref)
        val unknown = sigD.keys - setOf("mId", "pars", "hashM", "hashV", "ctys")
        if (unknown.isNotEmpty()) fail("sigD", "unexpected sigD members $unknown", ref)
        val mechanism = sigD.string("mId")?.let(SigDMechanism::fromUri)
        if (mechanism == null) fail("sigD", "missing or unsupported sigD mId ${sigD["mId"]}", ref)
        val pars = sigD.array("pars")?.strings()
        if (pars.isNullOrEmpty()) return fail("sigD", "sigD pars must be a non-empty array of strings", ref)
        val hasHash = "hashM" in sigD || "hashV" in sigD
        when (mechanism) {
            SigDMechanism.OBJECT_ID_BY_URI_HASH -> {
                if (sigD.string("hashM")?.let(JadesDigestNames::parse) == null) fail("sigD", "hashM missing or unsupported", "$ref.3.3")
                if (sigD.array("hashV")?.strings()?.size != pars.size) fail("sigD", "hashV must contain one digest per pars element", "$ref.1")
            }

            SigDMechanism.OBJECT_ID_BY_URI -> if (hasHash) fail("sigD", "hashM/hashV shall not be present for ObjectIdByURI", "$ref.3.2")
            SigDMechanism.HTTP_HEADERS -> {
                if (hasHash || "ctys" in sigD) fail("sigD", "hashM, hashV and ctys shall not be present for HttpHeaders", "$ref.2")
                if (b64) fail("sigD", "HttpHeaders requires b64=false", "TS 119 182-1 5.1.10")
            }

            null -> {}
        }
        sigD.array("ctys")?.let { if (it.strings()?.size != pars.size) fail("sigD", "ctys must contain one entry per pars element", "$ref.1") }
    }

    private suspend fun checkPolicy(header: JsonObject, signature: JadesSignature, checks: MutableList<ValidationCheck>): String? {
        val pid = header.obj(JadesHeaders.SIG_PID) ?: return null
        val ref = "TS 119 182-1 5.2.7"
        val policyId = pid.obj("id")?.string("id") ?: return "sigPId without id".also { checks += ValidationCheck("format:sigPId", CheckStatus.WARNING, it, ref) }
        val alg = pid.string("digAlg")?.let(JadesDigestNames::parse)
        val digest = pid.string("digVal")?.let { runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull() }
        val doc = signature.components(JadesHeaders.SIG_PST).firstNotNullOfOrNull { (it.value as? JsonObject)?.string("sigPolDoc") }
            ?.let { runCatching { JadesEncoding.fromAnyBase64(it) }.getOrNull() }
        if (alg == null || digest == null) {
            checks += ValidationCheck("policy", CheckStatus.INFO, "signature policy $policyId (no digest)", ref)
            return null
        }
        if (doc == null) {
            checks += ValidationCheck("policy", CheckStatus.INFO, "signature policy $policyId: document not embedded, digest not checked", ref)
            return null
        }
        if ((pid["digPSp"] as? JsonPrimitive)?.booleanOrNull == true) {
            checks += ValidationCheck("policy", CheckStatus.WARNING, "policy digest computed per a technical specification (digPSp); not checked", ref)
            return null
        }
        return if (alg.digest(doc).contentEquals(digest)) {
            checks += ValidationCheck("policy", CheckStatus.PASSED, "signature policy $policyId matches the embedded sigPSt document", ref)
            null
        } else "digest of the signature policy document in sigPSt does not match sigPId"
    }

    companion object {
        const val FORMAT = "JAdES"
    }
}
