package id.walt.ades.xades

import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.ValidationData
import id.walt.ades.validation.ValidationDataCollector
import id.walt.ades.x509.X509Certificate
import id.walt.ades.xades.Xml.add
import id.walt.ades.xades.Xml.child
import id.walt.ades.xades.Xml.isA
import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Element
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Augments XAdES signatures: B-T (SignatureTimeStamp), B-LT (CertificateValues, RevocationValues and
 * TimeStampValidationData) and B-LTA (ArchiveTimeStamp in the v1.4.1 namespace, ETSI EN 319 132-1 5.5.2).
 * Augmenting a B-LTA signature to B-LTA again adds missing validation data and a further archive time-stamp.
 */
class XadesAugmenter(private val options: XadesAugmentationOptions = XadesAugmentationOptions()) {

    suspend fun augment(
        signed: ByteArray,
        targetLevel: AdesLevel,
        detachedContents: List<XadesDocument> = emptyList(),
        signatureId: String? = null,
    ): ByteArray {
        val detached = XadesSigner.detachedMap(detachedContents)
        var bytes = signed
        if (targetLevel >= AdesLevel.B_T) bytes = step(bytes, signatureId) { sig ->
            val missing = sig.timestampContainers(TimestampContainerType.SIGNATURE).isEmpty() &&
                sig.timestampContainers(TimestampContainerType.ARCHIVE).isEmpty()
            if (missing) addSignatureTimestamp(sig)
            missing
        }
        if (targetLevel >= AdesLevel.B_LT) bytes = step(bytes, signatureId) { sig ->
            sig.timestampContainers(TimestampContainerType.ARCHIVE).isEmpty() && addLongTermValidationData(sig)
        }
        if (targetLevel >= AdesLevel.B_LTA) {
            bytes = step(bytes, signatureId) { sig ->
                sig.timestampContainers(TimestampContainerType.ARCHIVE).isNotEmpty() && addRenewalValidationData(sig)
            }
            bytes = step(bytes, signatureId) { sig -> addArchiveTimestamp(sig, detached); true }
            bytes = step(bytes, signatureId) { sig -> addValidationDataForLastArchiveTimestamp(sig) }
        }
        return bytes
    }

    private suspend fun step(bytes: ByteArray, signatureId: String?, block: suspend (XadesSignatureElement) -> Boolean): ByteArray {
        val doc = Xml.parse(bytes)
        val signatures = XadesSignatureElement.findAll(doc).filter { signatureId == null || it.id == signatureId }
        if (signatures.isEmpty()) throw XadesException("No ds:Signature${signatureId?.let { " with Id $it" } ?: ""} found")
        var changed = false
        for (sig in signatures) {
            if (sig.qualifyingProperties == null) throw XadesException("Signature ${sig.id} is not a XAdES signature")
            val problems = sig.registerIds()
            if (problems.isNotEmpty()) throw XadesException("Cannot augment signature ${sig.id}: ${problems.joinToString()}")
            if (block(sig)) changed = true
        }
        return if (changed) Xml.serialize(doc) else bytes
    }

    internal suspend fun timestamp(source: TimestampSource, data: ByteArray): ByteArray {
        val algorithm = options.timestampDigestAlgorithm
        val digest = algorithm.digest(data)
        val token = source.timestamp(algorithm, digest)
        if (!TimeStampToken(token).tstInfo.messageImprint.contentEquals(digest)) throw XadesException("TSA returned a token for a different imprint")
        return token
    }

    private suspend fun addSignatureTimestamp(sig: XadesSignatureElement) {
        val source = options.timestampSource ?: throw XadesException("A time-stamp source is required for XAdES-B-T")
        val c14n = options.canonicalization.uri
        val token = timestamp(source, XadesImprints.signatureTimestampInput(sig, c14n))
        timestampContainer(sig.ensureUnsignedSignatureProperties(), XADES, "SignatureTimeStamp", "TS-${UUID.randomUUID()}", c14n, token)
    }

    private suspend fun signingCertificate(sig: XadesSignatureElement, pool: ValidationData): X509Certificate =
        sig.findSigningCertificate(pool.certificates) ?: sig.keyInfoCertificates.firstOrNull()
        ?: throw XadesException("Signing certificate of ${sig.id} not found")

    private fun collector() = ValidationDataCollector(options.transport, options.trustAnchors)

    private suspend fun collect(targets: List<X509Certificate>, existing: ValidationData, what: String): ValidationData {
        if (targets.isEmpty()) return ValidationData()
        val result = collector().collect(targets, existing)
        if (result.missing.isNotEmpty()) {
            val message = "Incomplete validation data for $what: ${result.missing.joinToString("; ")}"
            if (options.requireCompleteValidationData) throw XadesException(message)
            log.warn { message }
        }
        // certificates already embedded in the collected OCSP responses are not repeated (EN 319 132-1 5.4.2 4)
        val known = existing.copy().also { k -> result.required.ocsps.flatMap { it.certificates }.forEach(k::add) }
        return result.required.minus(known)
    }

    private fun tsaCertificates(containers: List<TimestampContainer>, pool: ValidationData): List<X509Certificate> =
        containers.flatMap { it.parsedTokens() }.mapNotNull { it.findSigner(pool.certificates) }.distinct()

    /** B-LT: validation data of the signing certificate and of the time-stamps (content and signature time-stamps). */
    private suspend fun addLongTermValidationData(sig: XadesSignatureElement): Boolean {
        val existing = sig.embeddedValidationData()
        val usp = sig.ensureUnsignedSignatureProperties()
        var changed = false
        val forSigner = collect(listOf(signingCertificate(sig, existing)), existing, "the signing certificate")
        if (!forSigner.isEmpty) {
            addValues(usp, forSigner)
            existing.addAll(forSigner)
            changed = true
        }
        for (type in listOf(TimestampContainerType.CONTENT, TimestampContainerType.SIGNATURE)) {
            for (container in sig.timestampContainers(type)) {
                val add = collect(tsaCertificates(listOf(container), existing), existing, "time-stamp ${container.id ?: type.name}")
                if (add.isEmpty) continue
                val tsvd = validationDataElement(usp, "TimeStampValidationData", add)
                if (type == TimestampContainerType.SIGNATURE) {
                    var anchor: Element = container.element
                    while ((anchor.nextSibling as? Element)?.isA(XADES_141, "TimeStampValidationData") == true) anchor = anchor.nextSibling as Element
                    usp.insertBefore(tsvd, anchor.nextSibling)
                } else {
                    container.id?.let { tsvd.setAttribute("URI", "#$it") }
                    usp.insertBefore(tsvd, usp.firstChild)
                }
                existing.addAll(add)
                changed = true
            }
        }
        return changed
    }

    /** Before a further archive time-stamp (EN 319 132-1 6.3 aa): fresh material for the signer and all previous time-stamps. */
    private suspend fun addRenewalValidationData(sig: XadesSignatureElement): Boolean {
        val existing = sig.embeddedValidationData()
        val usp = sig.ensureUnsignedSignatureProperties()
        val forSigner = collect(listOf(signingCertificate(sig, existing)), existing, "the signing certificate")
        existing.addAll(forSigner)
        val forTimestamps = collect(tsaCertificates(TimestampContainerType.entries.flatMap(sig::timestampContainers), existing), existing, "time-stamps")
        if (!forTimestamps.isEmpty) validationDataElement(usp, "TimeStampValidationData", forTimestamps)
        if (!forSigner.isEmpty) validationDataElement(usp, "AnyValidationData", forSigner)
        return !forSigner.isEmpty || !forTimestamps.isEmpty
    }

    private suspend fun addArchiveTimestamp(sig: XadesSignatureElement, detached: Map<String, ByteArray>) {
        val source = options.archiveTimestampSource ?: throw XadesException("A time-stamp source is required for XAdES-B-LTA")
        val c14n = options.canonicalization.uri
        val input = try {
            XadesImprints.archiveTimestampInput(sig, sig.santuario(detached), c14n)
        } catch (e: XadesException) {
            throw e
        } catch (e: Exception) {
            throw XadesException("Cannot compute the archive time-stamp imprint (detached content missing?): ${e.message}", e)
        }
        val token = timestamp(source, input)
        timestampContainer(sig.ensureUnsignedSignatureProperties(), XADES_141, "ArchiveTimeStamp", "ATS-${UUID.randomUUID()}", c14n, token)
    }

    private suspend fun addValidationDataForLastArchiveTimestamp(sig: XadesSignatureElement): Boolean {
        val ats = sig.timestampContainers(TimestampContainerType.ARCHIVE).lastOrNull() ?: return false
        val existing = sig.embeddedValidationData()
        val add = collect(tsaCertificates(listOf(ats), existing), existing, "archive time-stamp ${ats.id}")
        if (add.isEmpty) return false
        val usp = sig.ensureUnsignedSignatureProperties()
        usp.insertBefore(validationDataElement(usp, "TimeStampValidationData", add), ats.element.nextSibling)
        return true
    }

    /** Merges signer validation data into CertificateValues / RevocationValues (only before any archive time-stamp). */
    private fun addValues(usp: Element, data: ValidationData) {
        if (data.certificates.isNotEmpty()) {
            val cv = usp.child(XADES, "CertificateValues") ?: usp.add(XADES, "CertificateValues")
            data.certificates.forEach { cv.add(XADES, "EncapsulatedX509Certificate", Xml.b64(it.encoded)) }
        }
        if (data.crls.isNotEmpty() || data.ocsps.isNotEmpty()) {
            val rv = usp.child(XADES, "RevocationValues") ?: usp.add(XADES, "RevocationValues")
            appendRevocationValues(rv, data)
        }
    }

    companion object {
        internal fun timestampContainer(parent: Element, ns: String, localName: String, id: String, c14n: String, token: ByteArray): Element {
            val container = parent.add(ns, localName)
            container.setAttribute("Id", id)
            container.add(DS, "CanonicalizationMethod").setAttribute("Algorithm", c14n)
            container.add(XADES, "EncapsulatedTimeStamp", Xml.b64(token))
            return container
        }

        /** Builds a v1.4.1 ValidationDataType element (TimeStampValidationData / AnyValidationData) appended to [usp]. */
        internal fun validationDataElement(usp: Element, localName: String, data: ValidationData): Element {
            val el = usp.add(XADES_141, localName)
            el.setAttribute("Id", "${if (localName == "AnyValidationData") "AVD" else "TSVD"}-${UUID.randomUUID()}")
            if (data.certificates.isNotEmpty()) {
                val cv = el.add(XADES, "CertificateValues")
                data.certificates.forEach { cv.add(XADES, "EncapsulatedX509Certificate", Xml.b64(it.encoded)) }
            }
            if (data.crls.isNotEmpty() || data.ocsps.isNotEmpty()) appendRevocationValues(el.add(XADES, "RevocationValues"), data)
            return el
        }

        private fun appendRevocationValues(rv: Element, data: ValidationData) {
            if (data.crls.isNotEmpty()) {
                val crlValues = rv.child(XADES, "CRLValues") ?: rv.add(XADES, "CRLValues").also { rv.insertBefore(it, rv.firstChild) }
                data.crls.forEach { crlValues.add(XADES, "EncapsulatedCRLValue", Xml.b64(it.encoded)) }
            }
            if (data.ocsps.isNotEmpty()) {
                val ocspValues = rv.child(XADES, "OCSPValues") ?: rv.add(XADES, "OCSPValues").also { o ->
                    rv.child(XADES, "OtherValues")?.let { rv.insertBefore(o, it) }
                }
                data.ocsps.forEach { ocspValues.add(XADES, "EncapsulatedOCSPValue", Xml.b64(it.encoded)) }
            }
        }
    }
}
