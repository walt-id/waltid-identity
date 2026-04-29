package id.walt.trust.signature

import id.walt.trust.model.AuthenticityState
import id.walt.trust.parser.SecureXmlParser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.xml.crypto.*
import javax.xml.crypto.dsig.*
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.X509Data

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
            return SignatureValidationResult(
                state = AuthenticityState.FAILED,
                details = "Multiple Signature elements found (${signatureNodes.length}); document must contain exactly one"
            )
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
            val certValidation = validateCertificateChain(
                signerCert, 
                keySelector.certificateChain,
                config
            )
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
    
    private fun validateCertificateChain(
        signerCert: X509Certificate,
        chain: List<X509Certificate>,
        config: SignatureValidationConfig
    ): Pair<Boolean, String?> {
        // Basic validity check on the signer cert
        try {
            if (!config.allowExpiredCertificates) {
                signerCert.checkValidity()
            }
        } catch (e: Exception) {
            return false to "Certificate expired or not yet valid: ${e.message}"
        }
        
        // Check against trusted anchors (required when requireTrustedCertificate is true)
        if (config.requireTrustedCertificate) {
            // Fail closed: if trusted cert is required but no anchors are configured, reject
            if (config.trustedAnchors.isEmpty()) {
                return false to "requireTrustedCertificate is true but no trusted anchors configured"
            }
            
            // Check if the signer cert itself is a trusted anchor
            if (config.trustedAnchors.any { it.encoded.contentEquals(signerCert.encoded) }) {
                return true to null
            }
            
            // Use PKIX path validation for proper chain verification
            try {
                val anchors = config.trustedAnchors.map { java.security.cert.TrustAnchor(it, null) }.toSet()
                
                // Build intermediates from chain (exclude signer cert)
                val intermediates = chain.filter { !it.encoded.contentEquals(signerCert.encoded) }
                
                val selector = java.security.cert.X509CertSelector().apply { 
                    certificate = signerCert 
                }
                val store = java.security.cert.CertStore.getInstance(
                    "Collection",
                    java.security.cert.CollectionCertStoreParameters(intermediates)
                )
                
                val params = java.security.cert.PKIXBuilderParameters(anchors, selector).apply {
                    addCertStore(store)
                    isRevocationEnabled = false  // Revocation checking is separate concern
                }
                
                val builder = java.security.cert.CertPathBuilder.getInstance("PKIX")
                val result = builder.build(params) as java.security.cert.PKIXCertPathBuilderResult
                
                // Validate the path
                val validator = java.security.cert.CertPathValidator.getInstance("PKIX")
                validator.validate(result.certPath, params)
                
                return true to null
            } catch (e: java.security.cert.CertPathBuilderException) {
                // PKIX path building failed - fall back to direct signing check
                logger.debug { "PKIX path building failed: ${e.message}, trying direct signing check" }
            } catch (e: java.security.cert.CertPathValidatorException) {
                return false to "Certificate path validation failed: ${e.message}"
            } catch (e: Exception) {
                logger.debug { "PKIX validation error: ${e.message}, trying direct signing check" }
            }
            
            // Fallback: direct signing check (for cases where chain has no intermediates)
            for (anchor in config.trustedAnchors) {
                try {
                    signerCert.verify(anchor.publicKey)
                    // Also verify issuer DN matches
                    if (signerCert.issuerX500Principal == anchor.subjectX500Principal) {
                        return true to null
                    }
                } catch (_: Exception) {
                    // Not signed by this anchor, try next
                }
            }
            
            return false to "Certificate not signed by any trusted anchor and no valid chain found"
        }
        
        // requireTrustedCertificate is false - accept any valid (non-expired) certificate
        return true to null
    }
    
    private fun parseXml(xml: String): Document {
        // Use SecureXmlParser for XXE protection, then register Id attributes
        val doc = SecureXmlParser.parseXml(xml)
        
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
     * Stores all certificates found for chain validation.
     */
    private class X509KeySelector : KeySelector() {
        var selectedCertificate: X509Certificate? = null
            private set
        
        /** All certificates found in KeyInfo (for chain validation) */
        val certificateChain = mutableListOf<X509Certificate>()
        
        override fun select(
            keyInfo: javax.xml.crypto.dsig.keyinfo.KeyInfo?,
            purpose: Purpose?,
            method: AlgorithmMethod?,
            context: XMLCryptoContext?
        ): KeySelectorResult {
            if (keyInfo == null) {
                throw KeySelectorException("Null KeyInfo - no key information in signature")
            }
            
            // Collect all certificates from KeyInfo
            certificateChain.clear()
            
            for (content in keyInfo.content) {
                when (content) {
                    is X509Data -> {
                        for (x509Content in content.content) {
                            when (x509Content) {
                                is X509Certificate -> {
                                    certificateChain.add(x509Content)
                                }
                                is ByteArray -> {
                                    // DER-encoded certificate
                                    val cert = parseCertificate(x509Content)
                                    if (cert != null) {
                                        certificateChain.add(cert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Select the signing certificate (first one that matches algorithm)
            for (cert in certificateChain) {
                if (isAlgorithmCompatible(method, cert.publicKey)) {
                    selectedCertificate = cert
                    return SimpleKeySelectorResult(cert.publicKey)
                }
            }
            
            throw KeySelectorException("No suitable X509Certificate found in KeyInfo")
        }
        
        private fun isAlgorithmCompatible(method: AlgorithmMethod?, key: PublicKey): Boolean {
            if (method == null) return true
            
            val algorithm = method.algorithm
            val keyAlg = key.algorithm
            
            // Check if the algorithm in the signature method matches the key type
            val isRsa = algorithm.contains("rsa", ignoreCase = true)
            val isDsa = algorithm.contains("dsa", ignoreCase = true)
            val isEc = algorithm.contains("ecdsa", ignoreCase = true) || algorithm.contains("ec", ignoreCase = true)
            
            return when {
                isRsa -> keyAlg.equals("RSA", ignoreCase = true)
                isDsa -> keyAlg.equals("DSA", ignoreCase = true)
                isEc -> keyAlg.equals("EC", ignoreCase = true)
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
