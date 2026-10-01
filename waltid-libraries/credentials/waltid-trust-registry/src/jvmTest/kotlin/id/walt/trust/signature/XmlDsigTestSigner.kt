package id.walt.trust.signature

import id.walt.trust.parser.SecureXmlParser
import org.w3c.dom.Document
import java.io.StringWriter
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Produces enveloped XMLDSig signatures for test fixtures, so trust-list signature
 * tests don't depend on network access to fetch a real EU/national list.
 */
object XmlDsigTestSigner {

    /**
     * Fixed-digest RSASSA-PSS signature method (SHA-256 digest, MGF1 with SHA-256, 32-byte salt).
     * This is the exact algorithm URI used by Germany's BNetzA trusted list.
     */
    const val SHA256_RSA_MGF1 = "http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1"

    /** Ordinary PKCS#1 v1.5 RSA signature, for comparison/regression coverage. */
    const val RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"

    /** ECDSA over SHA-256, as used by trust lists signed with an EC key. */
    const val ECDSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"

    /**
     * Signs [xml] with an enveloped XMLDSig signature (exclusive C14N, whole-document reference)
     * appended as the last child of the document element, and returns the serialized result.
     */
    fun signEnveloped(
        xml: String,
        certificate: X509Certificate,
        privateKey: PrivateKey,
        signatureMethodUri: String = SHA256_RSA_MGF1
    ): String {
        val doc = SecureXmlParser.parseXml(xml)
        val factory = XMLSignatureFactory.getInstance("DOM")

        val reference = factory.newReference(
            "",
            factory.newDigestMethod(DigestMethod.SHA256, null),
            listOf(factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)),
            null,
            null
        )

        val signedInfo = factory.newSignedInfo(
            factory.newCanonicalizationMethod(
                CanonicalizationMethod.EXCLUSIVE,
                null as C14NMethodParameterSpec?
            ),
            factory.newSignatureMethod(signatureMethodUri, null),
            listOf(reference)
        )

        val keyInfoFactory = factory.keyInfoFactory
        val x509Data = keyInfoFactory.newX509Data(listOf(certificate))
        val keyInfo = keyInfoFactory.newKeyInfo(listOf(x509Data))

        val signContext = DOMSignContext(privateKey, doc.documentElement)
        signContext.defaultNamespacePrefix = "ds"

        factory.newXMLSignature(signedInfo, keyInfo).sign(signContext)

        return serialize(doc)
    }

    private fun serialize(doc: Document): String {
        val writer = StringWriter()
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }
}
