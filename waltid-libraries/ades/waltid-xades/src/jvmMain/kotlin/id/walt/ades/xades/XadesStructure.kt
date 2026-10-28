package id.walt.ades.xades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl
import id.walt.ades.xades.Xml.add
import id.walt.ades.xades.Xml.attr
import id.walt.ades.xades.Xml.child
import id.walt.ades.xades.Xml.childElements
import id.walt.ades.xades.Xml.children
import id.walt.ades.xades.Xml.hasAncestor
import id.walt.ades.xades.Xml.isA
import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.signature.Reference
import org.w3c.dom.Document
import org.w3c.dom.Element

internal enum class TimestampContainerType { CONTENT, SIGNATURE, ARCHIVE }

/** A XAdES time-stamp container (XAdESTimeStampType) with its encapsulated RFC 3161 tokens. */
internal class TimestampContainer(val element: Element, val type: TimestampContainerType) {
    val id: String? = element.attr("Id")
    val canonicalization: String =
        element.child(DS, "CanonicalizationMethod")?.attr("Algorithm") ?: XadesCanonicalization.DEFAULT_URI
    val includes: List<Element> = element.children(XADES, "Include")
    val tokens: List<ByteArray?> = element.children(XADES, "EncapsulatedTimeStamp").map { runCatching { Xml.unb64(it.textContent) }.getOrNull() }
    val xmlTimestamps: Int = element.children(XADES, "XMLTimeStamp").size

    fun parsedTokens(): List<TimeStampToken> = tokens.mapNotNull { t -> t?.let { runCatching { TimeStampToken(it) }.getOrNull() } }
}

/** Accessors for one ds:Signature element and its XAdES qualifying properties (direct incorporation). */
internal class XadesSignatureElement(val element: Element) {
    val document: Document get() = element.ownerDocument
    val id: String? get() = element.attr("Id")
    val signedInfo: Element = element.child(DS, "SignedInfo") ?: throw XadesException("ds:Signature without ds:SignedInfo")
    val signatureValue: Element = element.child(DS, "SignatureValue") ?: throw XadesException("ds:Signature without ds:SignatureValue")
    val keyInfo: Element? get() = element.child(DS, "KeyInfo")
    val objects: List<Element> get() = element.children(DS, "Object")
    val qualifyingPropertiesAll: List<Element> get() = objects.flatMap { it.children(XADES, "QualifyingProperties") }
    val qualifyingProperties: Element? get() = qualifyingPropertiesAll.firstOrNull()
    val qualifyingPropertiesObject: Element? get() = qualifyingProperties?.parentNode as? Element
    val signedProperties: Element? get() = qualifyingProperties?.child(XADES, "SignedProperties")
    val signedSignatureProperties: Element? get() = signedProperties?.child(XADES, "SignedSignatureProperties")
    val signedDataObjectProperties: Element? get() = signedProperties?.child(XADES, "SignedDataObjectProperties")
    val unsignedProperties: Element? get() = qualifyingProperties?.child(XADES, "UnsignedProperties")
    val unsignedSignatureProperties: Element? get() = unsignedProperties?.child(XADES, "UnsignedSignatureProperties")

    val unsignedSignaturePropertyElements: List<Element> get() = unsignedSignatureProperties?.childElements() ?: emptyList()

    val signatureMethod: String? get() = signedInfo.child(DS, "SignatureMethod")?.attr("Algorithm")
    val signatureValueBytes: ByteArray get() = Xml.unb64(signatureValue.textContent)

    val references: List<Element> get() = signedInfo.children(DS, "Reference")

    val keyInfoCertificates: List<X509Certificate>
        get() = keyInfo?.children(DS, "X509Data")?.flatMap { it.children(DS, "X509Certificate") }
            ?.mapNotNull { runCatching { X509Certificate(Xml.unb64(it.textContent)) }.getOrNull() } ?: emptyList()

    fun timestampContainers(type: TimestampContainerType): List<TimestampContainer> = when (type) {
        TimestampContainerType.CONTENT -> signedDataObjectProperties?.children(XADES, "AllDataObjectsTimeStamp") ?: emptyList()
        TimestampContainerType.SIGNATURE -> unsignedSignaturePropertyElements.filter { it.isA(XADES, "SignatureTimeStamp") }
        TimestampContainerType.ARCHIVE -> unsignedSignaturePropertyElements.filter { it.isA(XADES_141, "ArchiveTimeStamp") }
    }.map { TimestampContainer(it, type) }

    fun allTimestampTokens(): List<TimeStampToken> = TimestampContainerType.entries.flatMap { timestampContainers(it) }.flatMap { it.parsedTokens() }

    /** Lazily creates xades:UnsignedProperties/UnsignedSignatureProperties. */
    fun ensureUnsignedSignatureProperties(): Element {
        unsignedSignatureProperties?.let { return it }
        val qp = qualifyingProperties ?: throw XadesException("Not a XAdES signature: QualifyingProperties missing")
        val up = unsignedProperties ?: qp.add(XADES, "UnsignedProperties")
        val usp = up.add(XADES, "UnsignedSignatureProperties")
        up.insertBefore(usp, up.firstChild)
        return usp
    }

    /** All certificates and revocation data embedded anywhere in the signature (KeyInfo, unsigned properties, time-stamp tokens). */
    fun embeddedValidationData(): ValidationData {
        val data = ValidationData()
        keyInfoCertificates.forEach(data::add)
        val containers = unsignedSignaturePropertyElements.flatMap { p ->
            when {
                p.isA(XADES, "CertificateValues") || p.isA(XADES, "RevocationValues") -> listOf(p)
                p.isA(XADES_141, "TimeStampValidationData") || p.isA(XADES_141, "AnyValidationData") -> p.childElements()
                else -> emptyList()
            }
        }
        for (c in containers) {
            if (c.isA(XADES, "CertificateValues")) {
                c.children(XADES, "EncapsulatedX509Certificate").forEach { e ->
                    runCatching { X509Certificate(Xml.unb64(e.textContent)) }.getOrNull()?.let(data::add)
                }
            }
            if (c.isA(XADES, "RevocationValues")) {
                c.child(XADES, "CRLValues")?.children(XADES, "EncapsulatedCRLValue")?.forEach { e ->
                    runCatching { X509Crl(Xml.unb64(e.textContent)) }.getOrNull()?.let(data::add)
                }
                c.child(XADES, "OCSPValues")?.children(XADES, "EncapsulatedOCSPValue")?.forEach { e ->
                    runCatching { OcspResponse(Xml.unb64(e.textContent)) }.getOrNull()?.let(data::add)
                }
            }
        }
        for (token in allTimestampTokens()) {
            token.certificates.forEach(data::add)
            token.cms.crls.forEach(data::add)
            token.cms.ocspResponses.forEach { o -> runCatching { OcspResponse(o) }.getOrNull()?.let(data::add) }
        }
        data.ocsps.flatMap { it.certificates }.forEach(data::add)
        return data
    }

    /**
     * Registers as XML IDs only the elements a XAdES signature legitimately references (its ds:Object children,
     * SignedProperties, SignatureValue and unsigned properties) and same-document reference targets that are
     * unique in the whole document. Returns the problems found (duplicate IDs = possible signature wrapping).
     */
    fun registerIds(): List<String> {
        val problems = mutableListOf<String>()
        val candidates = buildList {
            add(element); add(signatureValue)
            addAll(objects)
            signedProperties?.let(::add)
            addAll(unsignedSignaturePropertyElements)
        }
        candidates.forEach { e ->
            val id = e.attr("Id") ?: return@forEach
            if (Xml.elementsWithId(document, id).size > 1) problems += "duplicate Id '$id' in document"
            else Xml.registerId(e)
        }
        for (ref in references) {
            val uri = ref.attr("URI") ?: continue
            val id = bareNameId(uri) ?: continue
            val matches = Xml.elementsWithId(document, id)
            when {
                matches.isEmpty() -> problems += "reference target '#$id' not found"
                matches.size > 1 -> problems += "reference target '#$id' is ambiguous (${matches.size} elements)"
                else -> {
                    val target = matches.single()
                    if (target.hasAncestor(DS, "Signature") && target !in candidates && !target.isInside(element)) {
                        problems += "reference target '#$id' lies inside another signature"
                    } else Xml.registerId(target)
                }
            }
        }
        return problems
    }

    private fun Element.isInside(ancestor: Element): Boolean {
        var p = parentNode
        while (p != null) {
            if (p === ancestor) return true
            p = p.parentNode
        }
        return false
    }

    fun santuario(detached: Map<String, ByteArray> = emptyMap()): XMLSignature {
        Xml.init()
        return XMLSignature(element, null, true).also { it.addResourceResolver(DetachedContentResolver(detached)) }
    }

    companion object {
        fun bareNameId(uri: String): String? = when {
            uri.startsWith("#xpointer(id(") -> uri.removePrefix("#xpointer(id(").removeSuffix("))").trim('\'', '"')
            uri.startsWith("#xpointer(") -> null
            uri.startsWith("#") -> uri.substring(1)
            else -> null
        }

        /** Top-level ds:Signature elements of a document (signatures nested in other signatures, e.g. counter-signatures, are skipped). */
        fun findAllElements(doc: Document): List<Element> {
            val list = doc.getElementsByTagNameNS(DS, "Signature")
            return (0 until list.length).map { list.item(it) as Element }.filter { !it.hasAncestor(DS, "Signature") }
        }

        fun findAll(doc: Document): List<XadesSignatureElement> = findAllElements(doc).map(::XadesSignatureElement)
    }
}

internal fun Reference.referenceElement(): Element = element

internal val Reference.isSignedPropertiesReference: Boolean get() = type == XadesNamespaces.SIGNED_PROPERTIES_TYPE

/** One Cert entry of SigningCertificateV2 (or the legacy SigningCertificate). */
internal class CertReference(val algorithmUri: String?, val digest: ByteArray?, val issuerSerialV2: ByteArray?, val legacy: Boolean) {
    val algorithm: AdesDigestAlgorithm? = algorithmUri?.let { runCatching { AdesDigestAlgorithm.fromXmlUri(it) }.getOrNull() }

    suspend fun matches(certificate: X509Certificate): Boolean =
        algorithm != null && digest != null && algorithm.digest(certificate.encoded).contentEquals(digest)
}

internal fun XadesSignatureElement.signingCertificateReferences(): List<CertReference> {
    val ssp = signedSignatureProperties ?: return emptyList()
    val v2 = ssp.child(XADES, "SigningCertificateV2")
    val v1 = ssp.child(XADES, "SigningCertificate")
    val list = (v2 ?: v1)?.children(XADES, "Cert") ?: return emptyList()
    return list.map { cert ->
        val cd = cert.child(XADES, "CertDigest")
        CertReference(
            cd?.child(DS, "DigestMethod")?.attr("Algorithm"),
            cd?.child(DS, "DigestValue")?.textContent?.let { runCatching { Xml.unb64(it) }.getOrNull() },
            cert.child(XADES, "IssuerSerialV2")?.textContent?.let { runCatching { Xml.unb64(it) }.getOrNull() },
            legacy = v2 == null,
        )
    }
}

/** The certificate among [candidates] matching the first SigningCertificateV2 reference. */
internal suspend fun XadesSignatureElement.findSigningCertificate(candidates: Collection<X509Certificate>): X509Certificate? {
    val ref = signingCertificateReferences().firstOrNull() ?: return null
    return candidates.firstOrNull { ref.matches(it) }
}
