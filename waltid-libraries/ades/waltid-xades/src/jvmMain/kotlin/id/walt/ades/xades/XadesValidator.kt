package id.walt.ades.xades

import id.walt.ades.asn1.Der
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
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataResolver
import id.walt.ades.x509.DistinguishedName
import id.walt.ades.x509.X509Certificate
import id.walt.ades.xades.Xml.attr
import id.walt.ades.xades.Xml.child
import id.walt.ades.xades.Xml.children
import id.walt.ades.xades.Xml.isA
import id.walt.ades.xades.Xml.textOf
import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import org.apache.xml.security.signature.XMLSignature
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Validates XAdES signatures: XMLDSig core validation (reference digests and signature value through
 * [AdesCrypto]), XAdES format checks, signing certificate identification (SigningCertificateV2), time-stamp
 * imprints per ETSI EN 319 132-1 and level detection; the EN 319 102-1 processes run in [AdesSignatureValidator].
 */
class XadesValidator(
    private val policy: AdesValidationPolicy = AdesValidationPolicy(),
    /** Resolves detached data objects not given to [validate]. */
    private val documentResolver: XadesDocumentResolver? = null,
) {
    suspend fun validate(signed: ByteArray, detachedContents: List<XadesDocument> = emptyList()): DocumentValidationResult {
        val doc = try {
            Xml.parse(signed)
        } catch (e: XadesException) {
            return DocumentValidationResult(FORMAT, emptyList(), listOf(ValidationCheck("format:xml", CheckStatus.FAILED, e.message ?: "invalid XML")))
        }
        val elements = XadesSignatureElement.findAllElements(doc)
        if (elements.isEmpty()) {
            return DocumentValidationResult(FORMAT, emptyList(), listOf(ValidationCheck("format:no-signature", CheckStatus.FAILED, "no ds:Signature element found")))
        }
        val results = elements.mapIndexed { index, element ->
            val id = element.attr("Id") ?: "signature-${index + 1}"
            val sig = runCatching { XadesSignatureElement(element) }.getOrElse { e ->
                return@mapIndexed AdesSignatureValidator(policy).validate(
                    SignatureEvidence(id, listOf(formatFailure("xmldsig", e.message ?: "malformed ds:Signature", "XMLDSig 4")), null, null, false)
                )
            }
            validateSignature(id, sig, detachedContents)
        }
        return DocumentValidationResult(FORMAT, results)
    }

    private suspend fun validateSignature(id: String, sig: XadesSignatureElement, detachedContents: List<XadesDocument>): SignatureValidationResult {
        val checks = mutableListOf<ValidationCheck>()
        fun fail(check: String, message: String, reference: String) {
            checks += formatFailure(check, message, reference)
        }

        fun warn(check: String, message: String, reference: String) {
            checks += ValidationCheck("xades:$check", CheckStatus.WARNING, message, reference)
        }

        fun pass(check: String, message: String, reference: String) {
            checks += ValidationCheck(check, CheckStatus.PASSED, message, reference)
        }

        // Structure and ID registration (signature wrapping protection)
        sig.registerIds().forEach { fail("id", it, "XMLDSig 4.4.3.3") }
        val qps = sig.qualifyingPropertiesAll
        when {
            qps.isEmpty() -> fail("qualifying-properties", "no xades:QualifyingProperties (not a XAdES signature)", "EN 319 132-1 4.4.1")
            qps.size > 1 -> fail("qualifying-properties", "more than one xades:QualifyingProperties", "EN 319 132-1 4.4.1")
        }
        if (sig.objects.flatMap { it.children(XADES, "QualifyingPropertiesReference") }.isNotEmpty()) {
            warn("qualifying-properties-reference", "indirect incorporation is not allowed in baseline signatures and is ignored", "EN 319 132-1 6.3")
        }
        sig.qualifyingProperties?.let { qp ->
            val target = qp.attr("Target")
            if (sig.id == null || target != "#${sig.id}") fail("qualifying-properties-target", "Target '$target' does not reference the signature Id '${sig.id}'", "EN 319 132-1 4.3.1")
        }
        if (sig.qualifyingProperties != null && sig.signedProperties == null) fail("signed-properties", "xades:SignedProperties missing", "EN 319 132-1 4.3.2")
        if (sig.signedProperties != null && sig.signedSignatureProperties == null) fail("signed-properties", "xades:SignedSignatureProperties missing", "EN 319 132-1 6.3")

        val c14nMethod = sig.signedInfo.child(DS, "CanonicalizationMethod")?.attr("Algorithm")
        if (c14nMethod !in XadesCanonicalization.ALLOWED_URIS) fail("canonicalization", "ds:CanonicalizationMethod '$c14nMethod' is not allowed", "EN 319 132-1 6.3 d)")
        val algorithm = sig.signatureMethod?.let { runCatching { AdesSignatureAlgorithm.fromXmlUri(it) }.getOrNull() }
        if (algorithm == null) fail("signature-method", "unsupported ds:SignatureMethod ${sig.signatureMethod}", "XMLDSig 6.4")

        // Detached content
        val detached = HashMap(XadesSigner.detachedMap(detachedContents))
        val unresolved = mutableSetOf<String>()
        for (uri in sig.references.mapNotNull { it.attr("URI") }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            if (uri in detached) continue
            val content = detachedContents.firstOrNull { it.name == XadesSigner.decodeUri(uri) }?.content
                ?: resolveExternal(uri)
            if (content != null) detached[uri] = content else unresolved += uri
        }

        val xmlSignature: XMLSignature? = try {
            sig.santuario(detached)
        } catch (e: Exception) {
            fail("xmldsig", "ds:Signature cannot be processed: ${e.message}", "XMLDSig 4")
            null
        }

        // XMLDSig reference validation
        var hashFailure = false
        var signedDataFound = true
        if (xmlSignature != null) {
            val references = XadesImprints.references(xmlSignature)
            val spRefs = references.filter { it.isSignedPropertiesReference }
            if (spRefs.size != 1) fail("signed-properties-reference", "expected exactly one ds:Reference of Type SignedProperties, found ${spRefs.size}", "EN 319 132-1 4.4.2")
            spRefs.firstOrNull()?.let { ref ->
                val node = runCatching { ref.contentsBeforeTransformation.subNode }.getOrNull()
                if (sig.signedProperties == null || node !== sig.signedProperties) {
                    fail("signed-properties-reference", "the SignedProperties reference does not point to this signature's xades:SignedProperties", "EN 319 132-1 4.4.2")
                }
            }
            if (references.none { !it.isSignedPropertiesReference }) warn("references", "no data object is signed", "EN 319 132-1 6.3")
            for (ref in references) {
                val uri = ref.uri ?: ""
                val label = "crypto:reference:${ref.id ?: uri}"
                if (uri in unresolved || ref.uri == null) {
                    signedDataFound = false
                    checks += ValidationCheck(label, CheckStatus.FAILED, "signed data object '$uri' not available", "EN 319 102-1 5.2.7")
                    continue
                }
                try {
                    val digestAlgorithm = AdesDigestAlgorithm.fromXmlUri(ref.messageDigestAlgorithm.algorithmURI)
                    if (digestAlgorithm.displayName in policy.forbiddenDigests) fail("digest-method", "${digestAlgorithm.displayName} is not allowed", "EN 319 132-1 6.2.1")
                    val expected = Xml.unb64(ref.referenceElement().child(DS, "DigestValue")?.textContent ?: "")
                    val actual = digestAlgorithm.digest(XadesImprints.referenceOctets(ref, null))
                    if (actual.contentEquals(expected)) pass(label, "digest of '$uri' matches", "XMLDSig 3.2.1")
                    else {
                        hashFailure = true
                        checks += ValidationCheck(label, CheckStatus.FAILED, "digest of '$uri' does not match", "XMLDSig 3.2.1")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail("reference", "reference '$uri' cannot be processed: ${e.message}", "XMLDSig 4.4.3")
                }
            }
        }

        // Signing certificate identification (SigningCertificateV2)
        val embedded = sig.embeddedValidationData()
        val certRefs = sig.signingCertificateReferences()
        if (sig.signedSignatureProperties != null && certRefs.isEmpty()) fail("signing-certificate", "SigningCertificateV2 missing", "EN 319 132-1 5.2.2 / 6.3")
        if (certRefs.firstOrNull()?.legacy == true) warn("signing-certificate", "obsolete SigningCertificate used instead of SigningCertificateV2", "EN 319 132-1 4.3.4")
        certRefs.firstOrNull()?.takeIf { it.algorithm == null }?.let { fail("signing-certificate", "unsupported CertDigest algorithm ${it.algorithmUri}", "EN 319 132-1 5.2.2") }
        val signer = sig.findSigningCertificate(embedded.certificates)
        if (signer == null && certRefs.isNotEmpty()) {
            checks += ValidationCheck("xades:signing-certificate", CheckStatus.FAILED, "no embedded certificate matches the SigningCertificateV2 digest", "EN 319 132-1 5.2.2")
        }
        if (signer != null) {
            pass("xades:signing-certificate", "SigningCertificateV2 references ${signer.subject}", "EN 319 132-1 5.2.2")
            if (signer !in sig.keyInfoCertificates) warn("key-info", "signing certificate is not in ds:KeyInfo/X509Data", "EN 319 132-1 6.3 a)")
            certRefs.first().issuerSerialV2?.let { if (!issuerSerialMatches(it, signer)) warn("issuer-serial", "IssuerSerialV2 does not match the signing certificate", "EN 319 132-1 5.2.2") }
        }

        // Signature value
        val signatureValueValid = if (xmlSignature != null && signer != null && algorithm != null) {
            val signedInfo = runCatching { xmlSignature.signedInfo.canonicalizedOctetStream }.getOrNull()
            val value = runCatching { sig.signatureValueBytes }.getOrNull()
            signedInfo != null && value != null && AdesCrypto.verify(signer, algorithm, signedInfo, value, derEcdsa = false)
        } else false

        // Signed properties
        val ssp = sig.signedSignatureProperties
        val signingTimeText = ssp?.textOf(XADES, "SigningTime")
        val claimedSigningTime = signingTimeText?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (ssp != null && signingTimeText == null) warn("signing-time", "SigningTime missing", "EN 319 132-1 6.3 h)")
        if (signingTimeText != null && claimedSigningTime == null) fail("signing-time", "invalid SigningTime '$signingTimeText'", "EN 319 132-1 5.2.1")
        ssp?.let { p ->
            if (p.child(XADES, "SignatureProductionPlace") != null || p.child(XADES, "SignerRole") != null) {
                warn("obsolete-property", "obsolete SignatureProductionPlace/SignerRole present", "EN 319 132-1 4.3.4")
            }
        }
        checkDataObjectFormats(sig, ::warn)

        val policyError = checkSignaturePolicy(sig, checks)

        // Time-stamps
        val contentTimestamps = mutableListOf<TimestampEvidence>()
        val signatureTimestamps = mutableListOf<TimestampEvidence>()
        val archiveTimestamps = mutableListOf<TimestampEvidence>()
        for (type in TimestampContainerType.entries) for (container in sig.timestampContainers(type)) {
            if (container.canonicalization !in XadesCanonicalization.ALLOWED_URIS) {
                fail("timestamp-canonicalization", "unsupported canonicalization ${container.canonicalization} in ${container.element.localName}", "EN 319 132-1 4.5")
                continue
            }
            if (container.xmlTimestamps > 0) warn("xml-timestamp", "XML time-stamps are not supported in baseline signatures", "EN 319 132-1 6.3")
            if (type == TimestampContainerType.SIGNATURE && container.tokens.size != 1) warn("signature-timestamp", "SignatureTimeStamp shall contain exactly one time-stamp", "EN 319 132-1 6.3 n)")
            val covered = runCatching {
                when (type) {
                    TimestampContainerType.CONTENT -> xmlSignature?.let { XadesImprints.contentTimestampInput(it, container.canonicalization) }
                    TimestampContainerType.SIGNATURE -> XadesImprints.signatureTimestampInput(sig, container.canonicalization)
                    TimestampContainerType.ARCHIVE -> xmlSignature?.let { XadesImprints.archiveTimestampInput(sig, it, container.canonicalization, container.element) }
                }
            }.getOrNull()
            container.tokens.forEachIndexed { i, token ->
                val name = "${container.element.localName}${container.id?.let { " $it" } ?: ""}${if (container.tokens.size > 1) " #${i + 1}" else ""}"
                when (type) {
                    TimestampContainerType.CONTENT -> contentTimestamps += TimestampEvidence(TimestampType.CONTENT, token, covered, name)
                    TimestampContainerType.SIGNATURE -> signatureTimestamps += TimestampEvidence(TimestampType.SIGNATURE, token, covered, name)
                    TimestampContainerType.ARCHIVE -> archiveTimestamps += TimestampEvidence(TimestampType.ARCHIVE, token, covered, name)
                }
            }
        }
        if (sig.unsignedSignaturePropertyElements.any { it.isA(XADES, "ArchiveTimeStamp") }) {
            warn("legacy-archive-timestamp", "v1.3.2 ArchiveTimeStamp is obsolete and not processed", "EN 319 132-1 4.3.6")
        }

        val level = detectLevel(sig, signer, embedded, signatureTimestamps.isNotEmpty(), archiveTimestamps.isNotEmpty())
        level?.let { checks += ValidationCheck("xades:level", CheckStatus.INFO, "XAdES-${it.code}", "EN 319 132-1 6.1") }

        val evidence = SignatureEvidence(
            signatureId = id,
            checks = checks,
            signingCertificate = signer,
            signatureAlgorithm = algorithm,
            signatureValueValid = signatureValueValid,
            signedDataFound = signedDataFound,
            hashFailure = hashFailure,
            policyError = policyError,
            claimedSigningTime = claimedSigningTime,
            certificates = embedded.certificates,
            crls = embedded.crls,
            ocsps = embedded.ocsps,
            contentTimestamps = contentTimestamps,
            signatureTimestamps = signatureTimestamps,
            archiveTimestamps = archiveTimestamps,
            level = level,
        )
        return AdesSignatureValidator(policy).validate(evidence)
    }

    private suspend fun resolveExternal(uri: String): ByteArray? {
        documentResolver?.let { r ->
            try {
                r.resolve(uri)?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        val transport = policy.transport ?: return null
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) return null
        return try {
            transport.get(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun checkDataObjectFormats(sig: XadesSignatureElement, warn: (String, String, String) -> Unit) {
        val referenceIds = sig.references.filter { it.attr("Type") != XadesNamespaces.SIGNED_PROPERTIES_TYPE }.mapNotNull { it.attr("Id") }
        val formats = sig.signedDataObjectProperties?.children(XADES, "DataObjectFormat") ?: emptyList()
        val referenced = formats.mapNotNull { it.attr("ObjectReference")?.removePrefix("#") }.toSet()
        formats.forEach { f ->
            val ref = f.attr("ObjectReference")
            if (ref == null || ref.removePrefix("#") !in referenceIds) warn("data-object-format", "DataObjectFormat ObjectReference '$ref' does not reference a signed ds:Reference", "EN 319 132-1 5.2.4")
            if (f.child(XADES, "MimeType") == null) warn("data-object-format", "DataObjectFormat without MimeType", "EN 319 132-1 6.3 l)")
        }
        val dataReferences = sig.references.count { it.attr("Type") != XadesNamespaces.SIGNED_PROPERTIES_TYPE }
        if (formats.size < dataReferences && referenceIds.any { it !in referenced }) {
            warn("data-object-format", "not every signed data object has a DataObjectFormat", "EN 319 132-1 6.3 k)")
        }
    }

    /** Checks SigPolicyHash against an embedded SignaturePolicyStore document; returns a policy processing error. */
    private suspend fun checkSignaturePolicy(sig: XadesSignatureElement, checks: MutableList<ValidationCheck>): String? {
        val spi = sig.signedSignatureProperties?.child(XADES, "SignaturePolicyIdentifier") ?: return null
        val spId = spi.child(XADES, "SignaturePolicyId") ?: return null
        val hash = spId.child(XADES, "SigPolicyHash") ?: return "SigPolicyHash missing"
        val algorithm = hash.child(DS, "DigestMethod")?.attr("Algorithm")?.let { runCatching { AdesDigestAlgorithm.fromXmlUri(it) }.getOrNull() }
            ?: return "unsupported SigPolicyHash digest algorithm"
        val expected = hash.child(DS, "DigestValue")?.textContent?.let { runCatching { Xml.unb64(it) }.getOrNull() } ?: return "invalid SigPolicyHash"
        if (spId.child(DS, "Transforms") != null) {
            checks += ValidationCheck("xades:signature-policy", CheckStatus.WARNING, "signature policy transforms are not processed", "EN 319 132-1 5.2.9")
            return null
        }
        val store = sig.unsignedSignaturePropertyElements.firstOrNull { it.isA(XADES_141, "SignaturePolicyStore") }
        val document = store?.child(XADES_141, "SignaturePolicyDocument")?.textContent?.let { runCatching { Xml.unb64(it) }.getOrNull() }
        if (document == null) {
            checks += ValidationCheck("xades:signature-policy", CheckStatus.INFO, "signature policy document not embedded; SigPolicyHash not checked", "EN 319 132-1 5.2.10")
            return null
        }
        return if (algorithm.digest(document).contentEquals(expected)) {
            checks += ValidationCheck("xades:signature-policy", CheckStatus.PASSED, "SignaturePolicyStore document matches SigPolicyHash", "EN 319 132-1 5.2.10")
            null
        } else "SignaturePolicyStore document does not match SigPolicyHash"
    }

    private fun issuerSerialMatches(issuerSerial: ByteArray, certificate: X509Certificate): Boolean = runCatching {
        val seq = Der.parse(issuerSerial)
        val name = seq.child(0).children.first { it.isContext(4) }.explicitInner()
        DistinguishedName.of(name).matches(certificate.issuer) && seq.child(1).asIntegerBytes().contentEquals(certificate.serialNumber)
    }.getOrDefault(false)

    /** Structural level; B-LT additionally requires embedded revocation data for the signer chain and the signature time-stamp units. */
    private suspend fun detectLevel(
        sig: XadesSignatureElement,
        signer: X509Certificate?,
        embedded: ValidationData,
        hasSignatureTimestamp: Boolean,
        hasArchiveTimestamp: Boolean,
    ): AdesLevel? {
        if (sig.signedProperties == null || sig.signingCertificateReferences().isEmpty()) return null
        if (!hasSignatureTimestamp) return AdesLevel.B_B
        if (signer == null) return AdesLevel.B_T
        val tsaCertificates = sig.timestampContainers(TimestampContainerType.SIGNATURE).flatMap { it.parsedTokens() }
            .mapNotNull { it.findSigner(embedded.certificates) }
        if (!(listOf(signer) + tsaCertificates).all { covered(it, embedded) }) return AdesLevel.B_T
        return if (hasArchiveTimestamp) AdesLevel.B_LTA else AdesLevel.B_LT
    }

    private suspend fun covered(certificate: X509Certificate, embedded: ValidationData): Boolean {
        val resolver = ValidationDataResolver(embedded.copy(), null, policy.trustAnchors, fetchOnline = false)
        val chain = resolver.buildChains(certificate).firstOrNull() ?: return false
        for (i in 0 until chain.size - 1) {
            if (chain[i].hasOcspNoCheck) continue
            if (resolver.localStatuses(chain[i], chain[i + 1]).isEmpty()) return false
        }
        return true
    }

    companion object {
        const val FORMAT = "XAdES"

        private fun formatFailure(check: String, message: String, reference: String) =
            ValidationCheck("${SignatureEvidence.FORMAT_PREFIX}$check", CheckStatus.FAILED, message, reference)
    }
}
