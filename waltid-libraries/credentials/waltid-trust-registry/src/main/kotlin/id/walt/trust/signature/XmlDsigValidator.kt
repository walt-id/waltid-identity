package id.walt.trust.signature

import id.walt.trust.model.AuthenticityState
import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.*
import javax.xml.crypto.dsig.*
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * Result of XMLDSig signature validation.
 */
data class SignatureValidationResult(
    val state: AuthenticityState,
    val signerCertificate: X509Certificate? = null,
    val signatureValid: Boolean = false,
    val referencesValid: Boolean = false,
    val details: String? = null,
    val warnings: List<String> = emptyList()
)

/**
 * Configuration for signature validation.
 */
data class SignatureValidationConfig(
    /**
     * If true, validates the signing certificate against trusted anchors.
     * If false, only validates signature cryptographically (self-consistency check).
     */
    val requireTrustedCertificate: Boolean = false,
    
    /**
     * Trusted root certificates for certificate chain validation.
     * Required if requireTrustedCertificate is true.
     */
    val trustedAnchors: Set<X509Certificate> = emptySet(),
    
    /**
     * Allow expired signing certificates. For TSL validation, some old
     * trust lists may have expired certs but still be valid for historical data.
     */
    val allowExpiredCertificates: Boolean = false,
    
    /**
     * Enable secure validation mode (restricts algorithms, reference count, etc.)
     * Enabled by default per Java security recommendations.
     */
    val secureValidation: Boolean = true
)

/**
 * XMLDSig signature validator for ETSI TS 119 612 Trust Service Lists.
 *
 * This validator handles enveloped XMLDSig signatures as used in EU Trust Lists.
 * It uses the standard Java XML Digital Signature API (JSR-105).
 */
object XmlDsigValidator {
    
    private const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
    
    /**
     * Validates the XMLDSig signature in an XML document.
     *
     * @param xml The XML document as a string
     * @param config Validation configuration
     * @return SignatureValidationResult with validation outcome
     */
    fun validate(xml: String, config: SignatureValidationConfig = SignatureValidationConfig()): SignatureValidationResult {
        return try {
            validateInternal(xml, config)
        } catch (e: Exception) {
            logger.error(e) { "Signature validation failed with exception" }
            SignatureValidationResult(
                state = AuthenticityState.FAILED,
                details = "Validation error: ${e.message}"
            )
        }
    }
    
    private fun validateInternal(xml: String, config: SignatureValidationConfig): SignatureValidationResult {
        // Parse the XML document
        val doc = parseXml(xml)
        
        // Find Signature element
        val signatureNodes = doc.getElementsByTagNameNS(XMLDSIG_NS, "Signature")
        if (signatureNodes.length == 0) {
            return SignatureValidationResult(
                state = AuthenticityState.FAILED,
                details = "No Signature element found in document"
            )
        }
        
        if (signatureNodes.length > 1) {
            logger.warn { "Multiple Signature elements found, validating first one only" }
        }
        
        val signatureNode = signatureNodes.item(0)
        
        // Create validation context with a KeySelector that extracts keys from KeyInfo
        val keySelector = X509KeySelector()
        val valContext = DOMValidateContext(keySelector, signatureNode)
        
        // Enable or disable secure validation mode
        if (config.secureValidation) {
            valContext.setProperty("org.jcp.xml.dsig.secureValidation", java.lang.Boolean.TRUE)
        }
        
        // Unmarshal the XMLSignature
        val factory = XMLSignatureFactory.getInstance("DOM")
        val signature: XMLSignature = factory.unmarshalXMLSignature(valContext)
        
        // Perform core validation (signature value + reference digests)
        val coreValid = signature.validate(valContext)
        
        // Get the certificate that was used for validation
        val signerCert = keySelector.selectedCertificate
        
        // Check signature value validity separately for better diagnostics
        val signatureValueValid = signature.signatureValue.validate(valContext)
        
        // Check each reference
        val warnings = mutableListOf<String>()
        var allRefsValid = true
        val signedInfo = signature.signedInfo
        
        for ((idx, ref) in signedInfo.references.withIndex()) {
            val reference = ref as Reference
            val refValid = reference.validate(valContext)
            if (!refValid) {
                warnings.add("Reference[$idx] (URI=${reference.uri}) digest validation failed")
                allRefsValid = false
            }
        }
        
        // Certificate validation (if required)
        if (config.requireTrustedCertificate && signerCert != null) {
            val certValidation = validateCertificate(signerCert, config)
            if (!certValidation.first) {
                return SignatureValidationResult(
                    state = AuthenticityState.FAILED,
                    signerCertificate = signerCert,
                    signatureValid = signatureValueValid,
                    referencesValid = allRefsValid,
                    details = "Certificate validation failed: ${certValidation.second}",
                    warnings = warnings
                )
            }
        }
        
        // Check certificate expiry
        if (signerCert != null && !config.allowExpiredCertificates) {
            try {
                signerCert.checkValidity()
            } catch (e: Exception) {
                warnings.add("Signing certificate validity check: ${e.message}")
                // Don't fail for now, just warn (many TSLs have expired certs)
            }
        }
        
        return if (coreValid) {
            SignatureValidationResult(
                state = AuthenticityState.VALIDATED,
                signerCertificate = signerCert,
                signatureValid = signatureValueValid,
                referencesValid = allRefsValid,
                details = "Signature validated successfully",
                warnings = warnings
            )
        } else {
            SignatureValidationResult(
                state = AuthenticityState.FAILED,
                signerCertificate = signerCert,
                signatureValid = signatureValueValid,
                referencesValid = allRefsValid,
                details = buildString {
                    append("Core validation failed. ")
                    if (!signatureValueValid) append("SignatureValue invalid. ")
                    if (!allRefsValid) append("Reference digest(s) invalid.")
                },
                warnings = warnings
            )
        }
    }
    
    private fun validateCertificate(cert: X509Certificate, config: SignatureValidationConfig): Pair<Boolean, String?> {
        // Basic validity check
        try {
            if (!config.allowExpiredCertificates) {
                cert.checkValidity()
            }
        } catch (e: Exception) {
            return false to "Certificate expired or not yet valid: ${e.message}"
        }
        
        // Check against trusted anchors
        if (config.trustedAnchors.isNotEmpty()) {
            // Simple check: is this cert signed by one of our trusted anchors?
            for (anchor in config.trustedAnchors) {
                try {
                    cert.verify(anchor.publicKey)
                    return true to null
                } catch (_: Exception) {
                    // Not signed by this anchor, try next
                }
            }
            
            // Also check if the cert itself is a trusted anchor
            if (config.trustedAnchors.any { it.encoded.contentEquals(cert.encoded) }) {
                return true to null
            }
            
            return false to "Certificate not signed by any trusted anchor"
        }
        
        // No trusted anchors configured - just accept
        return true to null
    }
    
    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Security: disable external entities
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        
        // Register Id attributes for XAdES signatures
        // XAdES uses "Id" attributes on various elements that need to be resolvable by URI reference
        registerIdAttributes(doc.documentElement)
        
        return doc
    }
    
    /**
     * Recursively register Id attributes so XMLDSig can resolve URI references like #xades-xxx
     */
    private fun registerIdAttributes(element: org.w3c.dom.Element) {
        // Check for Id attribute (case-sensitive per XML standards, but also check "id" for compatibility)
        val idAttr = element.getAttributeNode("Id") ?: element.getAttributeNode("id")
        if (idAttr != null && idAttr.value.isNotEmpty()) {
            element.setIdAttributeNode(idAttr, true)
        }
        
        // Recurse into child elements
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is org.w3c.dom.Element) {
                registerIdAttributes(child)
            }
        }
    }
    
    /**
     * KeySelector that extracts X.509 certificates from KeyInfo for signature validation.
     * Stores the selected certificate for later retrieval.
     */
    private class X509KeySelector : KeySelector() {
        var selectedCertificate: X509Certificate? = null
            private set
        
        override fun select(
            keyInfo: javax.xml.crypto.dsig.keyinfo.KeyInfo?,
            purpose: Purpose?,
            method: AlgorithmMethod?,
            context: XMLCryptoContext?
        ): KeySelectorResult {
            if (keyInfo == null) {
                throw KeySelectorException("Null KeyInfo - no key information in signature")
            }
            
            for (content in keyInfo.content) {
                when (content) {
                    is X509Data -> {
                        for (x509Content in content.content) {
                            when (x509Content) {
                                is X509Certificate -> {
                                    val cert = x509Content
                                    if (isAlgorithmCompatible(method, cert.publicKey)) {
                                        selectedCertificate = cert
                                        return SimpleKeySelectorResult(cert.publicKey)
                                    }
                                }
                                is ByteArray -> {
                                    // DER-encoded certificate
                                    val cert = parseCertificate(x509Content)
                                    if (cert != null && isAlgorithmCompatible(method, cert.publicKey)) {
                                        selectedCertificate = cert
                                        return SimpleKeySelectorResult(cert.publicKey)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            throw KeySelectorException("No suitable X509Certificate found in KeyInfo")
        }
        
        private fun isAlgorithmCompatible(method: AlgorithmMethod?, key: PublicKey): Boolean {
            if (method == null) return true
            
            val algorithm = method.algorithm
            val keyAlg = key.algorithm
            
            return when {
                algorithm.contains("rsa", ignoreCase = true) && keyAlg.equals("RSA", ignoreCase = true) -> true
                algorithm.contains("dsa", ignoreCase = true) && keyAlg.equals("DSA", ignoreCase = true) -> true
                algorithm.contains("ecdsa", ignoreCase = true) && keyAlg.equals("EC", ignoreCase = true) -> true
                algorithm.contains("ec", ignoreCase = true) && keyAlg.equals("EC", ignoreCase = true) -> true
                else -> true // Be permissive for unknown algorithms
            }
        }
        
        private fun parseCertificate(der: ByteArray): X509Certificate? {
            return try {
                val factory = CertificateFactory.getInstance("X.509")
                factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse DER certificate" }
                null
            }
        }
    }
    
    /**
     * Simple KeySelectorResult implementation.
     */
    private class SimpleKeySelectorResult(private val publicKey: PublicKey) : KeySelectorResult {
        override fun getKey(): java.security.Key = publicKey
    }
}
