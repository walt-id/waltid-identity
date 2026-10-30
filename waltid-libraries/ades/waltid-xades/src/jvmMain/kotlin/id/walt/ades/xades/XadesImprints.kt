package id.walt.ades.xades

import id.walt.ades.xades.Xml.attr
import id.walt.ades.xades.Xml.childElements
import org.apache.xml.security.signature.Reference
import org.apache.xml.security.signature.XMLSignature
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream

/** Message imprint inputs of the XAdES time-stamp containers (ETSI EN 319 132-1 clauses 5.2.8.1, 5.3, 5.5.2.3/5.5.2.4). */
internal object XadesImprints {

    fun references(xmlSignature: XMLSignature): List<Reference> =
        (0 until xmlSignature.signedInfo.length).map { xmlSignature.signedInfo.item(it) }

    /**
     * Result of the XMLDSig reference processing model; node-set results are canonicalized with [canonicalization]
     * (XMLDSig default Canonical XML 1.0 when null, as used for ds:DigestValue).
     */
    fun referenceOctets(reference: Reference, canonicalization: String?): ByteArray =
        Xml.octets(reference.contentsAfterTransformation, canonicalization)

    /** 5.3: canonicalized ds:SignatureValue element. */
    fun signatureTimestampInput(signature: XadesSignatureElement, canonicalization: String): ByteArray =
        Xml.c14n(signature.signatureValue, canonicalization)

    /** 5.2.8.1: all references except the one to SignedProperties, in order. */
    fun contentTimestampInput(xmlSignature: XMLSignature, canonicalization: String): ByteArray {
        val out = ByteArrayOutputStream()
        references(xmlSignature).filterNot { it.isSignedPropertiesReference }.forEach { out.write(referenceOctets(it, canonicalization)) }
        return out.toByteArray()
    }

    /**
     * 5.5.2.3 (not distributed) / 5.5.2.4 (distributed, Include elements). When [archiveTimestamp] is null the input
     * for a new archive time-stamp is built (all unsigned signature properties); otherwise only the properties
     * preceding it (or those it explicitly includes) contribute.
     */
    fun archiveTimestampInput(
        signature: XadesSignatureElement,
        xmlSignature: XMLSignature,
        canonicalization: String,
        archiveTimestamp: Element? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        references(xmlSignature).forEach { out.write(referenceOctets(it, canonicalization)) }
        out.write(Xml.c14n(signature.signedInfo, canonicalization))
        out.write(Xml.c14n(signature.signatureValue, canonicalization))
        signature.keyInfo?.let { out.write(Xml.c14n(it, canonicalization)) }

        val properties = signature.unsignedSignaturePropertyElements
        val includes = archiveTimestamp?.let { TimestampContainer(it, TimestampContainerType.ARCHIVE).includes }.orEmpty()
        val covered = when {
            archiveTimestamp == null -> properties
            includes.isNotEmpty() -> includes.map { inc ->
                val id = inc.attr("URI")?.let(XadesSignatureElement::bareNameId) ?: throw XadesException("ArchiveTimeStamp Include without bare-name URI")
                properties.firstOrNull { it.attr("Id") == id } ?: throw XadesException("ArchiveTimeStamp Include '#$id' not found")
            }
            else -> properties.takeWhile { it !== archiveTimestamp }
        }
        covered.forEach { out.write(Xml.c14n(it, canonicalization)) }

        val qpObject = signature.qualifyingPropertiesObject
        signature.element.childElements()
            .filter { it.namespaceURI == XadesNamespaces.DS && it.localName == "Object" && it !== qpObject }
            .forEach { out.write(Xml.c14n(it, canonicalization)) }
        return out.toByteArray()
    }
}
