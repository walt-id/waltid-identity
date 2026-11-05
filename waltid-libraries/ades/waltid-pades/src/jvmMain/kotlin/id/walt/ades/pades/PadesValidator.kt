package id.walt.ades.pades

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.Oids
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesSignatureValidator
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CertificateChainValidator
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.SignatureEvidence
import id.walt.ades.validation.SignatureValidationResult
import id.walt.ades.validation.TimestampEvidence
import id.walt.ades.validation.TimestampType
import id.walt.ades.validation.TimestampValidator
import id.walt.ades.validation.ValidationCheck
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataResolver
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import org.apache.pdfbox.cos.COSName

/**
 * Validates PAdES signatures (ETSI EN 319 142-1) with the EN 319 102-1 processes of [AdesSignatureValidator].
 * Each signature yields one [SignatureValidationResult]; document time-stamps act as signature time-stamps (when the
 * signature has none) or archive time-stamps (POE) and are additionally reported in the document checks.
 *
 * @param allowOtherModifications when false, incremental updates after a signature that change anything other than
 *        DSS, document time-stamps or additional signatures make that signature fail with FORMAT_FAILURE
 * @param signaturePolicyDocuments policy documents by policy OID, used to check signature-policy-identifier digests
 */
class PadesValidator(
    private val policy: AdesValidationPolicy = AdesValidationPolicy(),
    private val allowOtherModifications: Boolean = false,
    private val signaturePolicyDocuments: Map<String, ByteArray> = emptyMap(),
) {
    private val validator = AdesSignatureValidator(policy)

    suspend fun validate(pdf: ByteArray): DocumentValidationResult {
        val inspection = runCatching { PdfInspection.open(pdf) }.getOrElse {
            return DocumentValidationResult(FORMAT, emptyList(), listOf(ValidationCheck("format:pdf", CheckStatus.FAILED, "not a readable PDF: ${it.message}")))
        }
        return inspection.use { validate(it) }
    }

    private suspend fun validate(inspection: PdfInspection): DocumentValidationResult {
        val documentChecks = mutableListOf<ValidationCheck>()
        if (inspection.fields.isEmpty()) documentChecks += ValidationCheck("pdf:signatures", CheckStatus.INFO, "document contains no signatures")
        inspection.dss.problems.forEach { documentChecks += ValidationCheck("pdf:dss", CheckStatus.WARNING, it, "EN 319 142-1 5.4.2.2") }
        if (inspection.dss.present) {
            documentChecks += ValidationCheck("pdf:dss", CheckStatus.INFO,
                "DSS with ${inspection.dss.certificates.size} certificate(s), ${inspection.dss.crls.size} CRL(s), ${inspection.dss.ocsps.size} OCSP response(s), ${inspection.dss.vriKeys.size} VRI", "EN 319 142-1 5.4.2")
        }

        for (ts in inspection.documentTimestamps) documentChecks += documentTimestampCheck(inspection, ts)
        val results = inspection.signatures.map { validator.validate(evidence(inspection, it)) }
        return DocumentValidationResult(FORMAT, results, documentChecks)
    }

    private suspend fun documentTimestampCheck(inspection: PdfInspection, ts: PdfSignatureField): List<ValidationCheck> {
        val id = "pdf:document-timestamp:${ts.name}"
        val structural = byteRangeChecks(inspection, ts).filter { it.status == CheckStatus.FAILED }
        if (ts.subFilter != PdfNames.ETSI_RFC3161.name) {
            return listOf(ValidationCheck(id, CheckStatus.WARNING, "document time-stamp SubFilter ${ts.subFilter} is not ETSI.RFC3161", "EN 319 142-1 5.4.3"))
        }
        val failure = if (policy.strictTimestamps) CheckStatus.FAILED else CheckStatus.WARNING
        if (structural.isNotEmpty()) return listOf(ValidationCheck(id, failure, structural.joinToString("; ") { it.message }, "EN 319 142-1 5.4.3"))
        val pool = validationPool(inspection, ts)
        val resolver = ValidationDataResolver(pool, policy.transport, policy.trustAnchors, policy.fetchMissingValidationData)
        val result = TimestampValidator(CertificateChainValidator(resolver, policy))
            .validate(TimestampEvidence(TimestampType.ARCHIVE, ts.der, ts.signedContent, "document-timestamp:${ts.name}"))
        return listOf(
            if (result.passed) ValidationCheck(id, CheckStatus.PASSED, "document time-stamp at ${result.genTime} by ${result.tsaCertificate?.subject}", "EN 319 142-1 5.4.3")
            else ValidationCheck(id, failure, "document time-stamp ${result.indication}/${result.subIndication}: ${result.problems.joinToString("; ")}", "EN 319 142-1 5.4.3"),
        )
    }

    private fun validationPool(inspection: PdfInspection, field: PdfSignatureField?): ValidationData {
        val pool = inspection.dss.toValidationData()
        field?.cms?.let { cms ->
            cms.certificates.forEach(pool::add)
            cms.crls.forEach(pool::add)
            cms.ocspResponses.forEach { runCatching { OcspResponse(it) }.onSuccess(pool::add) }
        }
        field?.timestampToken?.certificates?.forEach(pool::add)
        return pool
    }

    private fun byteRangeChecks(inspection: PdfInspection, field: PdfSignatureField): List<ValidationCheck> {
        val pdf = inspection.pdf
        val ref = "EN 319 142-1 6.3 k"
        val br = field.byteRange ?: return listOf(ValidationCheck("format:byte-range", CheckStatus.FAILED, "missing or malformed /ByteRange", ref))
        val problems = mutableListOf<String>()
        if (br[0] != 0) problems += "ByteRange does not start at offset 0"
        if (field.signedContent == null || br[2] - br[1] < 2) problems += "ByteRange ${br.toList()} is inconsistent with the file"
        else {
            val gapStart = br[1]
            val gapEnd = br[2]
            if (pdf[gapStart] != '<'.code.toByte() || pdf[gapEnd - 1] != '>'.code.toByte()) problems += "ByteRange gap is not exactly the /Contents hexadecimal string"
            else {
                val hex = String(pdf, gapStart + 1, gapEnd - gapStart - 2, Charsets.ISO_8859_1).filterNot { it.isWhitespace() }
                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) problems += "ByteRange gap contains non-hexadecimal data"
                else {
                    val decoded = hex.padEnd(hex.length + hex.length % 2, '0').chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    if (!decoded.contentEquals(field.contents)) problems += "ByteRange gap does not hold this signature's /Contents"
                }
            }
            if (inspection.revisionIndexEndingAt(field.revisionEnd) < 0) problems += "ByteRange does not end at a revision boundary (%%EOF)"
        }
        val trailing = field.der?.let { field.contents.size - it.size } ?: 0
        if (field.der != null && field.contents.drop(field.der.size).any { it != 0.toByte() }) problems += "/Contents contains data after the DER object"
        return if (problems.isEmpty()) listOf(ValidationCheck("format:byte-range", CheckStatus.PASSED,
            "ByteRange ${br.toList()} covers the signed revision except /Contents ($trailing bytes padding)", ref))
        else problems.map { ValidationCheck("format:byte-range", CheckStatus.FAILED, it, ref) }
    }

    private suspend fun evidence(inspection: PdfInspection, field: PdfSignatureField): SignatureEvidence {
        val checks = byteRangeChecks(inspection, field).toMutableList()
        var baseline = true
        val dictionary = field.dictionary.cosObject
        when (field.subFilter) {
            PdfNames.ETSI_CADES_DETACHED.name -> checks += ValidationCheck("format:subfilter", CheckStatus.PASSED, "SubFilter ETSI.CAdES.detached", "EN 319 142-1 6.3 l")
            PdfNames.ADBE_PKCS7_DETACHED.name -> {
                baseline = false
                checks += ValidationCheck("pdf:subfilter", CheckStatus.WARNING, "legacy SubFilter adbe.pkcs7.detached (not a PAdES baseline signature)", "EN 319 142-1 5.3")
            }

            else -> checks += ValidationCheck("format:subfilter", CheckStatus.FAILED, "unsupported SubFilter ${field.subFilter}", "EN 319 142-1 5.3")
        }
        if (dictionary.containsKey(COSName.CERT)) {
            baseline = false
            checks += ValidationCheck("pdf:cert-entry", CheckStatus.WARNING, "signature dictionary contains /Cert", "EN 319 142-1 Table 1")
        }

        val cms = field.cms
        val signerInfo = cms?.signerInfos?.singleOrNull()
        val claimedFromM = field.signingTime
        val content = field.signedContent
        if (cms == null || signerInfo == null) {
            checks += ValidationCheck("format:cms", CheckStatus.FAILED,
                if (cms == null) "/Contents is not a DER CMS SignedData" else "CMS contains ${cms.signerInfos.size} SignerInfos, exactly one is required", "EN 319 142-1 4.1 a")
            return SignatureEvidence(field.name, checks, null, null, false, claimedSigningTime = claimedFromM)
        }
        if (cms.encapsulatedContent != null) checks += ValidationCheck("format:cms", CheckStatus.FAILED, "SignedData encapsulates content (must be detached)", "ISO 32000-1 12.8.3.3.1")
        val contentType = signerInfo.signedAttribute(Oids.ATTR_CONTENT_TYPE)?.values?.firstOrNull()?.let { runCatching { it.asOid() }.getOrNull() }
        if (contentType != Oids.CMS_DATA) checks += ValidationCheck("format:content-type", CheckStatus.FAILED, "content-type attribute is $contentType, id-data required", "EN 319 142-1 6.3 c")
        val messageDigest = signerInfo.messageDigest
        if (messageDigest == null) checks += ValidationCheck("format:message-digest", CheckStatus.FAILED, "message-digest attribute missing", "EN 319 122-1 5.1.2")

        val pool = validationPool(inspection, field)
        val sidCertificate = cms.findSigner(signerInfo, pool.certificates)
        var signingCertificate = sidCertificate
        val references = runCatching { signerInfo.essCertIds }.getOrElse { emptyList() }
        if (references.isEmpty()) {
            baseline = false
            checks += ValidationCheck("cades:signing-certificate", CheckStatus.WARNING, "no ESS signing-certificate(-v2) attribute", "EN 319 142-1 6.3 e")
        } else if (sidCertificate != null) {
            val (alg, hash, issuerSerial) = references.first()
            val digestOk = alg.digest(sidCertificate.encoded).contentEquals(hash)
            val serialOk = issuerSerial == null || runCatching {
                issuerSerial.child(1).asIntegerBytes().contentEquals(sidCertificate.serialNumber)
            }.getOrDefault(false)
            if (signerInfo.signedAttribute(Oids.ATTR_SIGNING_CERTIFICATE_V2) == null) {
                checks += ValidationCheck("cades:signing-certificate", CheckStatus.INFO, "legacy ESS signing-certificate (SHA-1)", "EN 319 142-1 6.3 f")
            }
            if (!digestOk || !serialOk) {
                signingCertificate = null
                checks += ValidationCheck("cades:signing-certificate", CheckStatus.FAILED,
                    "signing certificate ${sidCertificate.subject} does not match the ESS signing-certificate reference", "EN 319 102-1 5.2.3")
            } else checks += ValidationCheck("cades:signing-certificate", CheckStatus.PASSED, "ESS signing-certificate-v2 matches ${sidCertificate.subject}", "EN 319 122-1 5.2.2.3")
        }

        val signatureAlgorithm = runCatching { signerInfo.signatureAlgorithm }.getOrNull()
        val hashFailure = content != null && messageDigest != null && !messageDigest.contentEquals(signerInfo.digestAlgorithm.digest(content))
        if (hashFailure) checks += ValidationCheck("cades:message-digest", CheckStatus.FAILED, "message-digest does not match the ByteRange content", "EN 319 102-1 5.2.7")
        val signatureValid = sidCertificate != null && signerInfo.verifySignature(sidCertificate, null)

        val signingTimeAttribute = signerInfo.signedAttribute(Oids.ATTR_SIGNING_TIME)?.values?.firstOrNull()?.let { runCatching { it.asTime() }.getOrNull() }
        if (signingTimeAttribute != null) {
            if (claimedFromM != null && signingTimeAttribute.epochSeconds != claimedFromM.epochSeconds) {
                baseline = false
                checks += ValidationCheck("pades:signing-time", CheckStatus.WARNING, "signing-time $signingTimeAttribute differs from /M $claimedFromM", "EN 319 142-1 6.3 g")
            } else checks += ValidationCheck("pades:signing-time", CheckStatus.INFO, "CMS signing-time present; PAdES recommends /M only", "EN 319 142-1 6.3 g")
        }
        if (claimedFromM == null && signingTimeAttribute == null) {
            baseline = false
            checks += ValidationCheck("pades:signing-time", CheckStatus.WARNING, "neither /M nor signing-time present", "EN 319 142-1 6.3 g")
        }
        val hasCommitment = signerInfo.signedAttribute(Oids.ATTR_COMMITMENT_TYPE) != null
        val policyAttribute = signerInfo.signedAttribute(Oids.ATTR_SIGNATURE_POLICY_ID)
        if (field.dictionary.reason != null && (hasCommitment || policyAttribute != null)) {
            baseline = false
            checks += ValidationCheck("pades:reason", CheckStatus.WARNING, "/Reason used together with commitment-type-indication or signature-policy-identifier", "EN 319 142-1 6.3 m")
        }
        val policyError = policyAttribute?.let { checkPolicy(it.values.first(), checks) }

        // time-stamps
        val signatureTimestamps = field.signatureTimestampTokens.mapIndexed { i, token ->
            TimestampEvidence(TimestampType.SIGNATURE, token, signerInfo.signature, "signature-timestamp#${i + 1}")
        }.toMutableList()
        val laterDocumentTimestamps = inspection.documentTimestamps.filter { it.revisionEnd > field.revisionEnd }
        val documentTimestampEvidence = laterDocumentTimestamps.map { ts ->
            ts to TimestampEvidence(TimestampType.ARCHIVE, ts.der, ts.signedContent?.takeIf { byteRangeChecks(inspection, ts).none { c -> c.status == CheckStatus.FAILED } },
                "document-timestamp:${ts.name}")
        }
        val archiveTimestamps = documentTimestampEvidence.map { it.second }.toMutableList()
        var timeProvidingDocumentTimestamp: PdfSignatureField? = null
        if (signatureTimestamps.isEmpty() && archiveTimestamps.isNotEmpty()) {
            val first = archiveTimestamps.removeAt(0)
            signatureTimestamps += TimestampEvidence(TimestampType.SIGNATURE, first.token, first.coveredData, first.description)
            timeProvidingDocumentTimestamp = laterDocumentTimestamps.first()
        }

        // incremental updates after the signed revision
        val changes = inspection.changesAfter(field.revisionEnd)
        val kinds = changes.flatMap { it.kinds }.toSet()
        if (changes.isEmpty()) {
            checks += ValidationCheck("pdf:coverage", CheckStatus.PASSED, "signature covers the whole document", "EN 319 142-1 4.1 a")
        } else {
            checks += ValidationCheck("pdf:coverage", CheckStatus.INFO,
                "${changes.size} incremental update(s) after the signed revision: ${kinds.joinToString { it.name.lowercase() }.ifEmpty { "no relevant changes" }}", "EN 319 142-1 5.4.2.3")
            val disallowed = changes.flatMap { c -> if (ChangeKind.OTHER in c.kinds) c.details.map { "revision ${c.revision}: $it" } else emptyList() }
            if (disallowed.isNotEmpty()) {
                checks += ValidationCheck(if (allowOtherModifications) "pdf:modification" else "format:pdf-modification",
                    if (allowOtherModifications) CheckStatus.WARNING else CheckStatus.FAILED,
                    "document modified after signing: ${disallowed.joinToString("; ")}", "ISO 32000-1 12.8.2.2")
            }
            if (inspection.docMdpPermission == 1 && ChangeKind.SIGNATURE in kinds) {
                checks += ValidationCheck("format:docmdp", CheckStatus.FAILED,
                    "signatures were added to a document certified with DocMDP P=1 (no changes allowed)", "ISO 32000-1 12.8.2.2; EN 319 142-1 5.4.3")
            }
        }

        val tokens = (signatureTimestamps + archiveTimestamps).mapNotNull { it.token?.let { t -> runCatching { TimeStampToken(t) }.getOrNull() } }
        tokens.forEach { t -> t.certificates.forEach(pool::add) }

        val level = if (!baseline || signingCertificate == null) null else determineLevel(
            inspection, field, signingCertificate, pool,
            timeTokens = field.signatureTimestampTokens.mapNotNull { runCatching { TimeStampToken(it) }.getOrNull() } +
                listOfNotNull(timeProvidingDocumentTimestamp?.timestampToken),
            archiveCandidates = laterDocumentTimestamps.filter { it !== timeProvidingDocumentTimestamp },
        )
        level?.let { checks += ValidationCheck("pades:level", CheckStatus.INFO, "PAdES-BASELINE-${it.code}", "EN 319 142-1 6.3") }

        return SignatureEvidence(
            signatureId = field.name,
            checks = checks,
            signingCertificate = signingCertificate,
            signatureAlgorithm = signatureAlgorithm,
            signatureValueValid = signatureValid,
            signedDataFound = content != null,
            hashFailure = hashFailure,
            policyError = policyError,
            claimedSigningTime = claimedFromM ?: signingTimeAttribute,
            certificates = pool.certificates,
            crls = pool.crls,
            ocsps = pool.ocsps,
            signatureTimestamps = signatureTimestamps,
            archiveTimestamps = archiveTimestamps,
            level = level,
        )
    }

    private suspend fun checkPolicy(value: DerElement, checks: MutableList<ValidationCheck>): String? = runCatching {
        if (value.isUniversal(Der.NULL)) {
            checks += ValidationCheck("cades:signature-policy", CheckStatus.INFO, "implied signature policy", "EN 319 122-1 5.2.9")
            return null
        }
        val oid = value.child(0).asOid()
        val hashAlgAndValue = value.child(1)
        val alg = AdesDigestAlgorithm.fromOid(hashAlgAndValue.child(0).child(0).asOid())
        val hash = hashAlgAndValue.child(1).asOctetString()
        val document = signaturePolicyDocuments[oid] ?: signaturePolicyDocuments["urn:oid:$oid"]
        when {
            document == null -> {
                checks += ValidationCheck("cades:signature-policy", CheckStatus.INFO, "signature policy $oid (document not available, digest not checked)", "EN 319 122-1 5.2.9")
                null
            }

            alg.digest(document).contentEquals(hash) -> {
                checks += ValidationCheck("cades:signature-policy", CheckStatus.PASSED, "signature policy $oid digest matches", "EN 319 122-1 5.2.9")
                null
            }

            else -> "signature policy $oid digest does not match the policy document"
        }
    }.getOrElse { "malformed signature-policy-identifier: ${it.message}" }

    /**
     * B-T: trusted time from a signature-time-stamp or a later document time-stamp. B-LT: the final DSS (plus CMS
     * certificates) holds the chains and revocation data of the signer and the time-stamping TSAs. B-LTA: a later
     * document time-stamp covers a revision whose DSS already satisfied B-LT.
     */
    private suspend fun determineLevel(
        inspection: PdfInspection,
        field: PdfSignatureField,
        signer: X509Certificate,
        pool: ValidationData,
        timeTokens: List<TimeStampToken>,
        archiveCandidates: List<PdfSignatureField>,
    ): AdesLevel {
        if (timeTokens.isEmpty()) return AdesLevel.B_B
        val embedded = ValidationData().also { d ->
            field.cms?.certificates?.forEach(d::add)
            field.cms?.crls?.forEach(d::add)
            timeTokens.forEach { t -> t.certificates.forEach(d::add) }
        }
        val tsaCertificates = timeTokens.mapNotNull { it.findSigner(pool.certificates) }
        val targets = listOf(signer) + tsaCertificates
        suspend fun ltSatisfied(dss: DssContent): Boolean {
            val data = embedded.copy().also { it.addAll(dss.toValidationData()) }
            return targets.all { hasValidationMaterial(it, data) }
        }
        if (!ltSatisfied(inspection.dss)) return AdesLevel.B_T
        val lta = archiveCandidates.any { ts -> ts.timestampToken != null && ltSatisfied(inspection.dssAt(ts.revisionEnd)) }
        return if (lta) AdesLevel.B_LTA else AdesLevel.B_LT
    }

    private suspend fun hasValidationMaterial(certificate: X509Certificate, data: ValidationData): Boolean {
        val resolver = ValidationDataResolver(data, null, policy.trustAnchors, fetchOnline = false)
        val chain = resolver.buildChains(certificate).firstOrNull() ?: return false
        for (i in 0 until chain.size - 1) {
            if (chain[i].hasOcspNoCheck) continue
            if (resolver.localStatuses(chain[i], chain[i + 1]).isEmpty()) return false
        }
        return true
    }

    companion object {
        const val FORMAT = "PAdES"
    }
}
