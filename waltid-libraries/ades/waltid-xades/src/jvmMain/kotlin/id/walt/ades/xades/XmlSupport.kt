package id.walt.ades.xades

import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.apache.xml.security.signature.XMLSignatureByteInput
import org.apache.xml.security.signature.XMLSignatureInput
import org.apache.xml.security.utils.resolver.ResourceResolverContext
import org.apache.xml.security.utils.resolver.ResourceResolverException
import org.apache.xml.security.utils.resolver.ResourceResolverSpi
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

/** Secure DOM parsing/serialization and canonicalization helpers. */
internal object Xml {
    private const val XMLNS = "http://www.w3.org/2000/xmlns/"

    private val santuario: Unit by lazy {
        Init.init()
    }

    fun init() = santuario

    private fun builderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isValidating = false
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
    }

    /** Parses untrusted XML: DOCTYPE declarations (and therefore entities / XXE) are rejected. */
    fun parse(bytes: ByteArray): Document {
        init()
        val builder = builderFactory().newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw XadesException("External entities are not allowed") }
        builder.setErrorHandler(object : ErrorHandler {
            override fun warning(exception: SAXParseException) {}
            override fun error(exception: SAXParseException) = throw exception
            override fun fatalError(exception: SAXParseException) = throw exception
        })
        return try {
            builder.parse(ByteArrayInputStream(bytes))
        } catch (e: XadesException) {
            throw e
        } catch (e: Exception) {
            throw XadesException("Invalid XML: ${e.message}", e)
        }
    }

    fun newDocument(): Document {
        init()
        return builderFactory().newDocumentBuilder().newDocument()
    }

    fun serialize(doc: Document): ByteArray {
        val factory = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "") }
        }
        val transformer = factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.METHOD, "xml")
        }
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    fun c14n(node: Node, algorithm: String): ByteArray {
        init()
        val out = ByteArrayOutputStream()
        Canonicalizer.getInstance(algorithm).canonicalizeSubtree(node, out)
        return out.toByteArray()
    }

    /** Octets of a reference processing result; node-sets are canonicalized with [algorithm] (XMLDSig default if null). */
    fun octets(input: XMLSignatureInput, algorithm: String?): ByteArray {
        if (!input.isNodeSet && !input.isElement) return input.bytes
        if (algorithm == null) return input.bytes
        val c = Canonicalizer.getInstance(algorithm)
        val out = ByteArrayOutputStream()
        if (input.isElement && input.excludeNode == null) c.canonicalizeSubtree(input.subNode, out)
        else c.canonicalizeXPathNodeSet(input.nodeSet, out)
        return out.toByteArray()
    }

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getMimeDecoder().decode(text.filterNot { it.isWhitespace() })

    fun prefixFor(ns: String) = when (ns) {
        DS -> "ds"
        XADES -> "xades"
        XADES_141 -> "xades141"
        else -> null
    }

    /** Creates a namespaced element with an explicit namespace declaration when the prefix is not yet in scope of [parent]. */
    fun Document.element(ns: String, localName: String, parent: Element? = null, text: String? = null): Element {
        val prefix = prefixFor(ns)
        val el = createElementNS(ns, if (prefix != null) "$prefix:$localName" else localName)
        if (prefix != null && parent?.lookupNamespaceURI(prefix) != ns) el.setAttributeNS(XMLNS, "xmlns:$prefix", ns)
        if (text != null) el.textContent = text
        parent?.appendChild(el)
        return el
    }

    fun Element.add(ns: String, localName: String, text: String? = null): Element = ownerDocument.element(ns, localName, this, text)

    fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element) out += n
            n = n.nextSibling
        }
        return out
    }

    fun Element.children(ns: String, localName: String): List<Element> =
        childElements().filter { it.namespaceURI == ns && it.localName == localName }

    fun Element.child(ns: String, localName: String): Element? = children(ns, localName).firstOrNull()

    fun Element.isA(ns: String, localName: String) = namespaceURI == ns && this.localName == localName

    fun Element.attr(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null

    fun Element.textOf(ns: String, localName: String): String? = child(ns, localName)?.textContent?.trim()

    fun Node.hasAncestor(ns: String, localName: String): Boolean {
        var p = parentNode
        while (p != null) {
            if (p is Element && p.isA(ns, localName)) return true
            p = p.parentNode
        }
        return false
    }

    /** All elements in [doc] carrying an Id/ID/id attribute with [value] (used to detect duplicate IDs / wrapping). */
    fun elementsWithId(doc: Document, value: String): List<Element> {
        val all = doc.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }.filter { e ->
            listOf("Id", "ID", "id").any { e.hasAttribute(it) && e.getAttribute(it) == value }
        }
    }

    fun registerId(element: Element) {
        for (name in listOf("Id", "ID", "id")) if (element.hasAttribute(name)) element.setIdAttribute(name, true)
    }
}

/** Resolves non same-document reference URIs from a fixed map and refuses everything else (no file/HTTP access). */
internal class DetachedContentResolver(private val contents: Map<String, ByteArray>) : ResourceResolverSpi() {
    override fun engineCanResolveURI(context: ResourceResolverContext): Boolean {
        val uri = context.uriToResolve ?: return false
        return uri.isNotEmpty() && !uri.startsWith("#")
    }

    override fun engineResolveURI(context: ResourceResolverContext): XMLSignatureInput {
        val uri = context.uriToResolve
        val bytes = contents[uri] ?: throw ResourceResolverException("Detached content not available: $uri", context.uriToResolve, context.baseUri)
        return XMLSignatureByteInput(bytes).apply { sourceURI = uri }
    }
}
