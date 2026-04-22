package id.walt.trust.parser.tsl

import id.walt.trust.model.*
import id.walt.trust.signature.SignatureValidationConfig
import id.walt.trust.signature.SignatureValidationResult
import id.walt.trust.signature.XmlDsigValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * Configuration for TSL parsing.
 */
data class TslParseConfig(
    /**
     * Validate XMLDSig signature on the TSL.
     * If false, signature is not checked and authenticityState = SKIPPED_DEMO.
     */
    val validateSignature: Boolean = true,
    
    /**
     * Signature validation configuration.
     * Only used if validateSignature = true.
     */
    val signatureConfig: SignatureValidationConfig = SignatureValidationConfig(),
    
    /**
     * If true, reject TSLs with invalid signatures.
     * If false, parse anyway but set authenticityState = FAILED.
     */
    val strictSignatureValidation: Boolean = false
)

/**
 * Parses TS 119 612-style Trusted Lists (TSL/TL) XML into normalized trust model objects.
 *
 * This parser handles:
 * - Full TSL structure parsing (entities, services, identities)
 * - XMLDSig signature validation (configurable)
 * - Status mapping from ETSI URIs to normalized enum
 *
 * Input format matches EU LOTL / national TL structure.
 */
object TslXmlParser {

    /**
     * Parse a TSL XML document.
     *
     * @param xml The TSL XML content as a string
     * @param sourceId Unique identifier for this trust source
     * @param sourceUrl Optional URL where the TSL was fetched from
     * @param config Parsing configuration (signature validation, etc.)
     * @return ParsedTslSource containing the parsed data and validation result
     */
    fun parse(
        xml: String,
        sourceId: String,
        sourceUrl: String? = null,
        config: TslParseConfig = TslParseConfig()
    ): ParsedTslSource {
        val doc = parseXml(xml)
        val root = doc.documentElement

        // Extract scheme information
        val schemeInfo = root.getFirstChild("SchemeInformation")
        val territory = schemeInfo?.getTextContent("SchemeTerritory")
        val issueDate = schemeInfo?.getTextContent("ListIssueDateTime")?.parseInstant()
        val nextUpdate = schemeInfo?.getFirstChild("NextUpdate")?.getTextContent("dateTime")?.parseInstant()
        val sequenceNumber = schemeInfo?.getTextContent("TSLSequenceNumber")
        val versionId = schemeInfo?.getTextContent("TSLVersionIdentifier")

        // Validate signature if configured
        val signatureResult: SignatureValidationResult? = if (config.validateSignature) {
            logger.debug { "Validating XMLDSig signature for TSL $sourceId" }
            XmlDsigValidator.validate(xml, config.signatureConfig)
        } else {
            logger.debug { "Signature validation skipped for TSL $sourceId" }
            null
        }
        
        // Determine authenticity state
        val authenticityState = when {
            signatureResult == null -> AuthenticityState.SKIPPED_DEMO
            signatureResult.state == AuthenticityState.VALIDATED -> AuthenticityState.VALIDATED
            else -> AuthenticityState.FAILED
        }
        
        // Log validation result
        if (signatureResult != null) {
            logger.info { 
                "TSL $sourceId signature validation: ${signatureResult.state}" +
                (signatureResult.details?.let { " - $it" } ?: "") 
            }
            signatureResult.warnings.forEach { warning ->
                logger.warn { "TSL $sourceId signature warning: $warning" }
            }
        }
        
        // If strict validation is enabled and signature is invalid, throw
        if (config.strictSignatureValidation && authenticityState == AuthenticityState.FAILED) {
            throw TslSignatureValidationException(
                "TSL signature validation failed: ${signatureResult?.details}",
                signatureResult
            )
        }
        
        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.TSL,
            displayName = "TSL $territory (seq $sequenceNumber)",
            sourceUrl = sourceUrl,
            territory = territory,
            issueDate = issueDate,
            nextUpdate = nextUpdate,
            sequenceNumber = sequenceNumber,
            authenticityState = authenticityState,
            freshnessState = FreshnessState.UNKNOWN,
            metadata = buildMap {
                versionId?.let { put("tslVersion", it) }
                signatureResult?.signerCertificate?.let { cert ->
                    put("signerSubjectDN", cert.subjectX500Principal.name)
                    put("signerIssuerDN", cert.issuerX500Principal.name)
                    put("signerSerialNumber", cert.serialNumber.toString())
                }
            }
        )

        val entities = mutableListOf<TrustedEntity>()
        val services = mutableListOf<TrustedService>()
        val identities = mutableListOf<ServiceIdentity>()

        // Parse TrustServiceProviderList
        val tspList = root.getFirstChild("TrustServiceProviderList")
        tspList?.getChildren("TrustServiceProvider")?.forEach { tspElement ->
            val tspInfo = tspElement.getFirstChild("TSPInformation")
            val tspName = tspInfo?.getTextContent("TSPName") ?: "Unknown TSP"
            val entityId = generateEntityId(sourceId, tspName)

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = TrustedEntityType.TRUST_SERVICE_PROVIDER,
                legalName = tspName,
                country = territory
            )

            // Parse TSPServices
            val tspServices = tspElement.getFirstChild("TSPServices")
            tspServices?.getChildren("TSPService")?.forEachIndexed { svcIdx, svcElement ->
                val svcInfo = svcElement.getFirstChild("ServiceInformation")
                val serviceType = svcInfo?.getTextContent("ServiceTypeIdentifier") ?: "unknown"
                val statusUri = svcInfo?.getTextContent("ServiceStatus") ?: ""
                val statusStart = svcInfo?.getTextContent("StatusStartingTime")?.parseInstant()

                val serviceId = "$entityId::svc-$svcIdx"
                val status = mapTslStatus(statusUri)

                services += TrustedService(
                    serviceId = serviceId,
                    sourceId = sourceId,
                    entityId = entityId,
                    serviceType = serviceType,
                    status = status,
                    statusStart = statusStart,
                    metadata = mapOf("rawStatusUri" to statusUri)
                )

                // Parse ServiceDigitalIdentity
                val digitalIdentity = svcInfo?.getFirstChild("ServiceDigitalIdentity")
                digitalIdentity?.getChildren("DigitalId")?.forEachIndexed { idIdx, digitalIdElement ->
                    val x509Cert = digitalIdElement.getTextContent("X509Certificate")
                    val x509Ski = digitalIdElement.getTextContent("X509SKI")
                    val x509SubjectName = digitalIdElement.getTextContent("X509SubjectName")

                    if (x509Cert != null || x509Ski != null || x509SubjectName != null) {
                        val identityId = "$serviceId::id-$idIdx"
                        identities += ServiceIdentity(
                            identityId = identityId,
                            sourceId = sourceId,
                            entityId = entityId,
                            serviceId = serviceId,
                            certificateSha256Hex = x509Cert?.let { computeCertSha256(it) },
                            subjectKeyIdentifierHex = x509Ski,
                            subjectDn = x509SubjectName,
                            metadata = buildMap {
                                if (x509Cert != null) put("hasX509Certificate", "true")
                            }
                        )
                    }
                }
            }
        }

        return ParsedTslSource(
            source = source,
            entities = entities,
            services = services,
            identities = identities,
            signatureValidation = signatureResult
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        return factory.newDocumentBuilder().parse(xml.reader().let { org.xml.sax.InputSource(it) })
    }

    private fun Element.getFirstChild(tagName: String): Element? {
        // Try exact match first
        var nodes = getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.parentNode == this) return node
        }
        // Fallback: search by local name (handles namespace prefixes like tsl:SchemeTerritory)
        nodes = getElementsByTagNameNS("*", tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.parentNode == this) return node
        }
        // Last resort: any match (recursive)
        nodes = getElementsByTagNameNS("*", tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) return node
        }
        return null
    }

    private fun Element.getChildren(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        // Try exact match first
        var nodes = getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) result += node
        }
        // If no results, try namespace-aware search
        if (result.isEmpty()) {
            nodes = getElementsByTagNameNS("*", tagName)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is Element) result += node
            }
        }
        return result
    }

    private fun Element.getTextContent(tagName: String): String? {
        return getFirstChild(tagName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun String.parseInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private fun generateEntityId(sourceId: String, name: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$sourceId:$name".toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "tsp-$hash"
    }

    private fun computeCertSha256(base64Cert: String): String? {
        return runCatching {
            val decoded = Base64.getDecoder().decode(base64Cert.replace("\\s".toRegex(), ""))
            MessageDigest.getInstance("SHA-256")
                .digest(decoded)
                .joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun mapTslStatus(uri: String): TrustStatus {
        val lower = uri.lowercase()
        return when {
            "granted" in lower -> TrustStatus.GRANTED
            "recognized" in lower -> TrustStatus.RECOGNIZED
            "accredited" in lower -> TrustStatus.ACCREDITED
            "undersupervision" in lower || "supervised" in lower -> TrustStatus.SUPERVISED
            "deprecatedbynationallaw" in lower || "deprecated" in lower -> TrustStatus.DEPRECATED
            "suspended" in lower -> TrustStatus.SUSPENDED
            "revoked" in lower -> TrustStatus.REVOKED
            "withdrawn" in lower -> TrustStatus.WITHDRAWN
            "expired" in lower -> TrustStatus.EXPIRED
            else -> TrustStatus.UNKNOWN
        }
    }
}

/**
 * Result of parsing a TSL document.
 */
data class ParsedTslSource(
    val source: TrustSource,
    val entities: List<TrustedEntity>,
    val services: List<TrustedService>,
    val identities: List<ServiceIdentity>,
    val signatureValidation: SignatureValidationResult? = null
) {
    /** The signing certificate, if signature was validated */
    val signerCertificate: X509Certificate?
        get() = signatureValidation?.signerCertificate
}

/**
 * Exception thrown when TSL signature validation fails in strict mode.
 */
class TslSignatureValidationException(
    message: String,
    val validationResult: SignatureValidationResult?
) : Exception(message)
