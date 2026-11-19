package id.walt.ades.xades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.validation.AdesLevel
import id.walt.ades.xades.Xml.add
import id.walt.ades.xades.Xml.child
import id.walt.ades.xades.Xml.element
import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Creates XAdES baseline signatures (ETSI EN 319 132-1 clause 6). The signature value is produced through
 * [AdesSigningCredentials.sign], so any crypto2 key (software, KMS, HSM) can be used.
 */
class XadesSigner(
    private val credentials: AdesSigningCredentials,
    private val options: XadesSignatureOptions = XadesSignatureOptions(),
) {
    private val augmenter = XadesAugmenter(options.augmentation)

    /** Enveloped signature: the ds:Signature becomes the last child of the document element of [xml]. */
    suspend fun signEnveloped(xml: ByteArray, mimeType: String = "text/xml"): ByteArray =
        sign(XadesPackaging.ENVELOPED, listOf(XadesDocument("document.xml", xml, mimeType, xml = true)))

    /** Enveloping signature: the data objects are embedded in ds:Object elements (as XML or base64). */
    suspend fun signEnveloping(vararg documents: XadesDocument): ByteArray = sign(XadesPackaging.ENVELOPING, documents.toList())

    /** Detached signature: data objects are referenced by their (percent-encoded) names. */
    suspend fun signDetached(vararg documents: XadesDocument): ByteArray = sign(XadesPackaging.DETACHED, documents.toList())

    suspend fun sign(packaging: XadesPackaging, documents: List<XadesDocument>): ByteArray {
        require(documents.isNotEmpty()) { "Nothing to sign" }
        if (options.level >= AdesLevel.B_T || options.contentTimestamp) {
            requireNotNull(options.augmentation.timestampSource) { "A time-stamp source is required for ${options.level.code}" }
        }
        val sigId = options.signatureId ?: "id-${UUID.randomUUID()}"
        val detached = LinkedHashMap<String, ByteArray>()
        val built = buildSignature(packaging, documents, sigId, detached)
        var bytes = Xml.serialize(built)

        if (options.contentTimestamp) {
            val doc = Xml.parse(bytes)
            val sig = find(doc, sigId)
            sig.registerIds()
            val c14n = options.augmentation.canonicalization.uri
            val input = XadesImprints.contentTimestampInput(sig.santuario(detached), c14n)
            val token = augmenter.timestamp(options.augmentation.timestampSource!!, input)
            val sdop = sig.signedDataObjectProperties ?: sig.signedProperties!!.add(XADES, "SignedDataObjectProperties")
            XadesAugmenter.timestampContainer(sdop, XADES, "AllDataObjectsTimeStamp", "CTS-$sigId", c14n, token)
            bytes = Xml.serialize(doc)
        }

        val doc = Xml.parse(bytes)
        finalizeSignature(find(doc, sigId), detached, credentials)
        bytes = Xml.serialize(doc)

        if (options.level > AdesLevel.B_B) {
            bytes = augmenter.augment(bytes, options.level, documents.takeIf { packaging == XadesPackaging.DETACHED }.orEmpty(), sigId)
        }
        return bytes
    }

    /** Augments an existing XAdES signature (all top-level signatures of the document) to [targetLevel]. */
    suspend fun augment(signed: ByteArray, targetLevel: AdesLevel, detachedContents: List<XadesDocument> = emptyList()): ByteArray =
        augmenter.augment(signed, targetLevel, detachedContents)

    private class DataReference(
        val id: String,
        val uri: String,
        val type: String?,
        val transforms: List<String>,
        val mimeType: String,
        val description: String?,
        val encoding: String? = null,
    )

    private suspend fun buildSignature(
        packaging: XadesPackaging,
        documents: List<XadesDocument>,
        sigId: String,
        detached: MutableMap<String, ByteArray>,
    ): Document {
        val c14n = options.canonicalization.uri
        val doc: Document
        val parent: Element?
        val refs = mutableListOf<DataReference>()
        val objects = mutableListOf<Pair<String, XadesDocument>>()
        when (packaging) {
            XadesPackaging.ENVELOPED -> {
                val d = documents.singleOrNull() ?: throw IllegalArgumentException("Enveloped signatures sign exactly one XML document")
                doc = Xml.parse(d.content)
                parent = doc.documentElement
                val mime = d.mimeType.takeIf { it != "application/octet-stream" } ?: "text/xml"
                refs += DataReference("r-$sigId-1", "", null, listOf(XadesNamespaces.ENVELOPED_TRANSFORM, c14n), mime, d.description)
            }

            XadesPackaging.ENVELOPING -> {
                doc = Xml.newDocument()
                parent = null
                documents.forEachIndexed { i, d ->
                    val objectId = "o-$sigId-${i + 1}"
                    objects += objectId to d
                    refs += if (d.xml) DataReference("r-$sigId-${i + 1}", "#$objectId", XadesNamespaces.OBJECT_TYPE, listOf(c14n), d.mimeType, d.description)
                    else DataReference(
                        "r-$sigId-${i + 1}", "#$objectId", XadesNamespaces.OBJECT_TYPE, listOf(XadesNamespaces.BASE64_TRANSFORM), d.mimeType, d.description,
                        XadesNamespaces.ENCODING_BASE64,
                    )
                }
            }

            XadesPackaging.DETACHED -> {
                doc = Xml.newDocument()
                parent = null
                documents.forEachIndexed { i, d ->
                    val uri = encodeUri(d.name)
                    require(uri !in detached) { "Duplicate detached document name ${d.name}" }
                    detached[uri] = d.content
                    refs += DataReference("r-$sigId-${i + 1}", uri, null, emptyList(), d.mimeType, d.description)
                }
            }
        }

        val signature = doc.element(DS, "Signature", parent)
        if (parent == null) doc.appendChild(signature)
        signature.setAttribute("Id", sigId)
        val signedInfo = signature.add(DS, "SignedInfo")
        signedInfo.add(DS, "CanonicalizationMethod").setAttribute("Algorithm", c14n)
        val sigAlgUri = credentials.algorithm.xmlUri ?: throw XadesException("No XMLDSig identifier for ${credentials.algorithm}")
        signedInfo.add(DS, "SignatureMethod").setAttribute("Algorithm", sigAlgUri)
        refs.forEach { r -> addReference(signedInfo, r.id, r.uri, r.type, r.transforms) }
        addReference(signedInfo, "r-$sigId-sp", "#xades-$sigId", XadesNamespaces.SIGNED_PROPERTIES_TYPE, listOf(c14n))

        signature.add(DS, "SignatureValue").setAttribute("Id", "value-$sigId")
        val x509Data = signature.add(DS, "KeyInfo").add(DS, "X509Data")
        val chain = if (options.includeCertificateChain) credentials.certificateChain else listOf(credentials.signingCertificate)
        chain.forEach { x509Data.add(DS, "X509Certificate", Xml.b64(it.encoded)) }

        for ((objectId, d) in objects) {
            val obj = signature.add(DS, "Object")
            obj.setAttribute("Id", objectId)
            obj.setAttribute("MimeType", d.mimeType)
            if (d.xml) {
                val content = Xml.parse(d.content).documentElement
                obj.appendChild(doc.importNode(content, true))
            } else {
                obj.setAttribute("Encoding", XadesNamespaces.ENCODING_BASE64)
                obj.textContent = Xml.b64(d.content)
            }
        }

        val qp = signature.add(DS, "Object").add(XADES, "QualifyingProperties")
        qp.setAttribute("Target", "#$sigId")
        val sp = qp.add(XADES, "SignedProperties")
        sp.setAttribute("Id", "xades-$sigId")
        buildSignedSignatureProperties(sp.add(XADES, "SignedSignatureProperties"))

        val sdop = sp.add(XADES, "SignedDataObjectProperties")
        for (r in refs) {
            val dof = sdop.add(XADES, "DataObjectFormat")
            dof.setAttribute("ObjectReference", "#${r.id}")
            r.description?.let { dof.add(XADES, "Description", it) }
            dof.add(XADES, "MimeType", r.mimeType)
            r.encoding?.let { dof.add(XADES, "Encoding", it) }
        }
        for (c in options.commitments) {
            val cti = sdop.add(XADES, "CommitmentTypeIndication")
            objectIdentifier(cti, "CommitmentTypeId", commitmentUri(c), c.description, c.documentationReferences, allowOid = false)
            cti.add(XADES, "AllSignedDataObjects")
        }

        options.signaturePolicy?.document?.let { policyDocument ->
            val spec = options.signaturePolicyDocSpecification
                ?: throw IllegalArgumentException("signaturePolicyDocSpecification is required to embed the signature policy document")
            val usp = qp.add(XADES, "UnsignedProperties").add(XADES, "UnsignedSignatureProperties")
            val store = usp.add(XADES_141, "SignaturePolicyStore")
            store.setAttribute("Id", "sps-$sigId")
            objectIdentifier(store, "SPDocSpecification", spec, null, emptyList(), ns = XADES_141)
            store.add(XADES_141, "SignaturePolicyDocument", Xml.b64(policyDocument))
        }
        return doc
    }

    private suspend fun buildSignedSignatureProperties(ssp: Element) {
        val time = options.signingTime ?: Clock.System.now()
        ssp.add(XADES, "SigningTime", Instant.fromEpochSeconds(time.epochSeconds).toString())

        val cert = ssp.add(XADES, "SigningCertificateV2").add(XADES, "Cert")
        digestAlgAndValue(cert.add(XADES, "CertDigest"), options.digestAlgorithm, credentials.signingCertificate.encoded)
        if (options.includeIssuerSerial) cert.add(XADES, "IssuerSerialV2", Xml.b64(credentials.signingCertificate.issuerSerial()))

        val policy = options.signaturePolicy
        if (policy != null) {
            val spId = ssp.add(XADES, "SignaturePolicyIdentifier").add(XADES, "SignaturePolicyId")
            objectIdentifier(spId, "SigPolicyId", policy.id, policy.description, emptyList())
            val hash = spId.add(XADES, "SigPolicyHash")
            hash.add(DS, "DigestMethod").setAttribute("Algorithm", policy.digestAlgorithm.xmlUri)
            hash.add(DS, "DigestValue", Xml.b64(policy.digest))
            if (policy.uri != null || policy.userNotice != null) {
                val qualifiers = spId.add(XADES, "SigPolicyQualifiers")
                policy.uri?.let { qualifiers.add(XADES, "SigPolicyQualifier").add(XADES, "SPURI", it) }
                policy.userNotice?.let { qualifiers.add(XADES, "SigPolicyQualifier").add(XADES, "SPUserNotice").add(XADES, "ExplicitText", it) }
            }
        } else if (options.signaturePolicyImplied) {
            ssp.add(XADES, "SignaturePolicyIdentifier").add(XADES, "SignaturePolicyImplied")
        }

        options.productionPlace?.let { place ->
            val p = ssp.add(XADES, "SignatureProductionPlaceV2")
            place.locality?.let { p.add(XADES, "City", it) }
            (place.streetAddress ?: place.postOfficeBoxNumber)?.let { p.add(XADES, "StreetAddress", it) }
            place.region?.let { p.add(XADES, "StateOrProvince", it) }
            place.postalCode?.let { p.add(XADES, "PostalCode", it) }
            place.country?.let { p.add(XADES, "CountryName", it) }
        }

        if (options.claimedRoles.isNotEmpty()) {
            val roles = ssp.add(XADES, "SignerRoleV2").add(XADES, "ClaimedRoles")
            options.claimedRoles.forEach { roles.add(XADES, "ClaimedRole", it) }
        }
    }

    private fun commitmentUri(c: Commitment): String =
        CommitmentType.entries.firstOrNull { it.oid == c.id || it.oid == c.id.removePrefix("urn:oid:") }?.uri
            ?: if (OID.matches(c.id)) "urn:oid:${c.id}" else c.id

    private fun objectIdentifier(
        parent: Element,
        localName: String,
        id: String,
        description: String?,
        documentationReferences: List<String>,
        allowOid: Boolean = true,
        ns: String = XADES,
    ) {
        val oi = parent.add(ns, localName)
        val identifier = oi.add(XADES, "Identifier")
        when {
            allowOid && OID.matches(id) -> {
                identifier.textContent = "urn:oid:$id"; identifier.setAttribute("Qualifier", "OIDAsURN")
            }

            allowOid && id.startsWith("urn:oid:") -> {
                identifier.textContent = id; identifier.setAttribute("Qualifier", "OIDAsURN")
            }

            else -> identifier.textContent = id
        }
        description?.let { oi.add(XADES, "Description", it) }
        if (documentationReferences.isNotEmpty()) {
            val refs = oi.add(XADES, "DocumentationReferences")
            documentationReferences.forEach { refs.add(XADES, "DocumentationReference", it) }
        }
    }

    private suspend fun digestAlgAndValue(parent: Element, algorithm: AdesDigestAlgorithm, data: ByteArray) {
        parent.add(DS, "DigestMethod").setAttribute("Algorithm", algorithm.xmlUri)
        parent.add(DS, "DigestValue", Xml.b64(algorithm.digest(data)))
    }

    private fun addReference(signedInfo: Element, id: String, uri: String, type: String?, transforms: List<String>) {
        val ref = signedInfo.add(DS, "Reference")
        ref.setAttribute("Id", id)
        type?.let { ref.setAttribute("Type", it) }
        ref.setAttribute("URI", uri)
        if (transforms.isNotEmpty()) {
            val t = ref.add(DS, "Transforms")
            transforms.forEach { t.add(DS, "Transform").setAttribute("Algorithm", it) }
        }
        ref.add(DS, "DigestMethod").setAttribute("Algorithm", options.digestAlgorithm.xmlUri)
        ref.add(DS, "DigestValue")
    }

    companion object {
        private val OID = Regex("^[0-2](\\.\\d+)+$")

        internal fun encodeUri(name: String): String = URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")
        internal fun decodeUri(uri: String): String = runCatching { URLDecoder.decode(uri.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(uri)

        internal fun find(doc: Document, id: String): XadesSignatureElement =
            XadesSignatureElement.findAll(doc).firstOrNull { it.id == id } ?: throw XadesException("Signature $id not found")

        /** Computes all ds:DigestValue elements and the ds:SignatureValue of [signature] (the DOM must be final for signed parts). */
        internal suspend fun finalizeSignature(signature: XadesSignatureElement, detached: Map<String, ByteArray>, credentials: AdesSigningCredentials) {
            val problems = signature.registerIds()
            if (problems.isNotEmpty()) throw XadesException("Cannot sign: ${problems.joinToString()}")
            val xmlSignature = signature.santuario(detached)
            for (ref in XadesImprints.references(xmlSignature)) {
                val algorithm = AdesDigestAlgorithm.fromXmlUri(ref.messageDigestAlgorithm.algorithmURI)
                val digest = algorithm.digest(XadesImprints.referenceOctets(ref, null))
                ref.referenceElement().child(DS, "DigestValue")!!.textContent = Xml.b64(digest)
            }
            val signedInfo = signature.santuario(detached).signedInfo.canonicalizedOctetStream
            signature.signatureValue.textContent = Xml.b64(credentials.sign(signedInfo, derEcdsa = false))
        }

        /** Detached content keyed by every plausible reference URI form of the document name. */
        internal fun detachedMap(documents: List<XadesDocument>): Map<String, ByteArray> = buildMap {
            documents.forEach { d -> put(d.name, d.content); put(encodeUri(d.name), d.content) }
        }
    }
}
