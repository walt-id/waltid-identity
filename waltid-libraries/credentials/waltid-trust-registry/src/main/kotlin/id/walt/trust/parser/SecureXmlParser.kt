package id.walt.trust.parser

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Secure XML parsing utilities with XXE protection.
 * 
 * All XML parsing in the trust-registry module should use these methods
 * to ensure consistent security hardening against:
 * - XXE (XML External Entity) attacks
 * - Billion laughs / entity expansion DoS
 * - External DTD loading
 * - XInclude processing
 */
object SecureXmlParser {
    
    /**
     * Creates a hardened DocumentBuilderFactory with XXE protections enabled.
     * 
     * Security features enabled:
     * - Disallows DOCTYPE declarations entirely (strongest protection)
     * - Disables external general entities
     * - Disables external parameter entities
     * - Disables external DTD loading
     * - Enables secure processing mode
     * - Disables XInclude
     * - Disables entity reference expansion
     */
    fun createSecureDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            // Enable namespace awareness for proper XML handling
            isNamespaceAware = true
            
            // XXE Protection: Disable DTD processing entirely
            // This is the strongest protection - TSL/LoTE documents don't use DTDs
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            
            // XXE Protection: Disable external entities
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            
            // XXE Protection: Disable external DTD loading
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            
            // Enable secure processing (limits entity expansion, etc.)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            
            // Disable XInclude processing
            isXIncludeAware = false
            
            // Disable entity reference expansion
            isExpandEntityReferences = false
        }
    }
    
    /**
     * Parses an XML string into a Document with XXE protections.
     * 
     * @param xml The XML content to parse
     * @return Parsed DOM Document
     * @throws org.xml.sax.SAXException if the XML is malformed or contains blocked features (like DOCTYPE)
     */
    fun parseXml(xml: String): Document {
        val factory = createSecureDocumentBuilderFactory()
        return factory.newDocumentBuilder().parse(InputSource(xml.reader()))
    }
    
    /**
     * Parses an XML byte array into a Document with XXE protections.
     * 
     * @param xml The XML content to parse
     * @return Parsed DOM Document
     */
    fun parseXml(xml: ByteArray): Document {
        val factory = createSecureDocumentBuilderFactory()
        return factory.newDocumentBuilder().parse(xml.inputStream())
    }
}

/**
 * Extension function to get first child element by local name (namespace-aware).
 * Uses getElementsByTagNameNS with wildcard namespace to handle both prefixed
 * and non-prefixed elements.
 */
fun Element.getFirstChildByLocalName(localName: String): Element? {
    // Use namespace-aware search with wildcard namespace
    val nodes = getElementsByTagNameNS("*", localName)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        // Only return direct children (parent is this element)
        if (node is Element && node.parentNode == this) {
            return node
        }
    }
    // Fallback: return first match even if not direct child
    // (for compatibility with existing behavior)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) return node
    }
    return null
}

/**
 * Extension function to get all child elements by local name (namespace-aware).
 * Uses getElementsByTagNameNS with wildcard namespace.
 */
fun Element.getChildrenByLocalName(localName: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = getElementsByTagNameNS("*", localName)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) result += node
    }
    return result
}

/**
 * Extension function to get text content of a child element by local name.
 */
fun Element.getChildTextContent(localName: String): String? {
    return getFirstChildByLocalName(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
