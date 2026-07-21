package id.walt.trust.parser.lote

import org.w3c.dom.ls.LSInput
import org.w3c.dom.ls.LSResourceResolver
import org.xml.sax.SAXException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

/** Offline XSD validation for ETSI TS 119 602 V1.1.1, Annex A.2.1. */
internal object EtsiLoteXmlSchemaValidator {
    const val SCHEMA_COMMIT: String = "e84f427f0cde99513b574ef4b5a155ac4a38eab6"
    private const val BASE = "/etsi-ts-119-602/"

    private val schema: Schema by lazy {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).apply {
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            resourceResolver = BundledResolver
        }
        val resource = requireNotNull(javaClass.getResource("${BASE}1960201_xsd_schema.xsd")) {
            "Bundled ETSI TS 119 602 XML Schema is missing"
        }
        factory.newSchema(StreamSource(resource.openStream(), resource.toExternalForm()))
    }

    fun validate(xml: String) {
        try {
            schema.newValidator().apply {
                setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }.validate(StreamSource(StringReader(xml)))
        } catch (e: SAXException) {
            throw IllegalArgumentException("ETSI TS 119 602 XML Schema validation failed: ${e.message}", e)
        }
    }

    private object BundledResolver : LSResourceResolver {
        override fun resolveResource(
            type: String?, namespaceURI: String?, publicId: String?, systemId: String?, baseURI: String?
        ): LSInput? {
            val name = when (namespaceURI) {
                XMLConstants.XML_NS_URI -> "xml.xsd"
                XMLSignatureNamespace -> "xmldsig-core-schema.xsd"
                else -> return null
            }
            val resource = requireNotNull(javaClass.getResource("$BASE$name")) { "Bundled schema $name is missing" }
            return ResourceInput(publicId, resource.toExternalForm(), resource.openStream())
        }
    }

    private class ResourceInput(
        private var publicIdValue: String?,
        private var systemIdValue: String?,
        private var byteStreamValue: InputStream?
    ) : LSInput {
        override fun getCharacterStream(): Reader? = null
        override fun setCharacterStream(characterStream: Reader?) = Unit
        override fun getByteStream(): InputStream? = byteStreamValue
        override fun setByteStream(byteStream: InputStream?) { byteStreamValue = byteStream }
        override fun getStringData(): String? = null
        override fun setStringData(stringData: String?) = Unit
        override fun getSystemId(): String? = systemIdValue
        override fun setSystemId(systemId: String?) { systemIdValue = systemId }
        override fun getPublicId(): String? = publicIdValue
        override fun setPublicId(publicId: String?) { publicIdValue = publicId }
        override fun getBaseURI(): String? = null
        override fun setBaseURI(baseURI: String?) = Unit
        override fun getEncoding(): String? = "UTF-8"
        override fun setEncoding(encoding: String?) = Unit
        override fun getCertifiedText(): Boolean = false
        override fun setCertifiedText(certifiedText: Boolean) = Unit
    }

    private const val XMLSignatureNamespace = "http://www.w3.org/2000/09/xmldsig#"
}
