package id.walt.trust.parser.tsl

import id.walt.trust.model.*
import id.walt.trust.parser.SecureXmlParser
import id.walt.trust.parser.getChildTextContent
import id.walt.trust.parser.getChildrenByLocalName
import id.walt.trust.parser.getFirstChildByLocalName
import id.walt.trust.signature.SignatureValidationConfig
import id.walt.trust.signature.SignatureValidationResult
import id.walt.trust.signature.XmlDsigValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.kotlincrypto.hash.sha2.SHA256
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Configuration for TSL parsing.
 */
data class TslParseConfig(
    /**
     * Validate XMLDSig signature on the TSL.
     * If false, signature is not checked and the source remains unverified.
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
     * Note: "invalid" means signature present but fails validation.
     * For unsigned documents, see [requireSignature].
     */
    val strictSignatureValidation: Boolean = true,

    /**
     * If true, require that the TSL has a signature.
     * If false, unsigned TSLs are parsed as unverified.
     * Only applies when validateSignature = true.
     */
    val requireSignature: Boolean = true
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

    private const val TSL_NAMESPACE = "http://uri.etsi.org/02231/v2#"
    private const val EU_LOTL_TYPE = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists"

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
        val doc = SecureXmlParser.parseXml(xml)
        val root = doc.documentElement
        require(root.localName == "TrustServiceStatusList" && root.namespaceURI == TSL_NAMESPACE) {
            "Expected ETSI TS 119 612 TrustServiceStatusList in namespace $TSL_NAMESPACE"
        }

        // Extract scheme information
        val schemeInfo = root.getFirstChildByLocalName("SchemeInformation")
        val territory = schemeInfo?.getChildTextContent("SchemeTerritory")
        val issueDate = schemeInfo?.getChildTextContent("ListIssueDateTime")?.parseInstant()
        val nextUpdate = schemeInfo?.getFirstChildByLocalName("NextUpdate")?.getChildTextContent("dateTime")?.parseInstant()
        val sequenceNumber = schemeInfo?.getChildTextContent("TSLSequenceNumber")
        val versionId = schemeInfo?.getChildTextContent("TSLVersionIdentifier")
        val tslType = schemeInfo?.getChildTextContent("TSLType")
        val pointerCount = schemeInfo?.getFirstChildByLocalName("PointersToOtherTSL")
            ?.getChildrenByLocalName("OtherTSLPointer")?.size ?: 0
        val format = if (tslType == EU_LOTL_TYPE || tslType?.endsWith("listofthelists") == true) {
            TrustListFormat.ETSI_TS_119_612_LIST_OF_TRUST_LISTS_XML
        } else {
            TrustListFormat.ETSI_TS_119_612_TRUST_LIST_XML
        }

        // Validate signature if configured
        val signatureResult: SignatureValidationResult? = if (config.validateSignature) {
            logger.debug { "Validating XMLDSig signature for TSL $sourceId" }
            XmlDsigValidator.validate(xml, config.signatureConfig)
        } else {
            logger.debug { "Signature validation skipped for TSL $sourceId" }
            null
        }

        val assurance = when {
            signatureResult == null -> SourceAssurance(
                signatureStatus = SignatureStatus.NOT_CHECKED,
                signerTrust = SignerTrust.NOT_EVALUATED,
                authenticityState = AuthenticityState.UNVERIFIED,
                details = "XML signature verification was disabled"
            )
            signatureResult.signatureStatus == SignatureStatus.NOT_PRESENT && !config.requireSignature -> SourceAssurance(
                signatureStatus = SignatureStatus.NOT_PRESENT,
                signerTrust = SignerTrust.NOT_APPLICABLE,
                authenticityState = AuthenticityState.UNVERIFIED,
                details = signatureResult.details
            )
            signatureResult.signatureStatus == SignatureStatus.NOT_PRESENT -> SourceAssurance(
                signatureStatus = SignatureStatus.NOT_PRESENT,
                signerTrust = SignerTrust.NOT_APPLICABLE,
                authenticityState = AuthenticityState.FAILED,
                details = "A signature is required but the TSL is unsigned"
            )
            else -> SourceAssurance(
                signatureStatus = signatureResult.signatureStatus,
                signerTrust = signatureResult.signerTrust,
                authenticityState = signatureResult.state,
                details = signatureResult.details
            )
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
        if (config.strictSignatureValidation && assurance.authenticityState == AuthenticityState.FAILED) {
            throw TslSignatureValidationException(
                "TSL signature validation failed: ${signatureResult?.details}",
                signatureResult
            )
        }

        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.TSL,
            format = format,
            displayName = if (format == TrustListFormat.ETSI_TS_119_612_LIST_OF_TRUST_LISTS_XML) {
                "$territory LoTL (seq $sequenceNumber)"
            } else {
                "TSL $territory (seq $sequenceNumber)"
            },
            sourceUrl = sourceUrl,
            territory = territory,
            issueDate = issueDate,
            nextUpdate = nextUpdate,
            sequenceNumber = sequenceNumber,
            assurance = assurance,
            freshnessState = evaluateFreshness(nextUpdate),
            metadata = buildMap {
                versionId?.let { put("tslVersion", it) }
                tslType?.let { put("tslType", it) }
                put("pointerCount", pointerCount.toString())
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
        val tspList = root.getFirstChildByLocalName("TrustServiceProviderList")
        tspList?.getChildrenByLocalName("TrustServiceProvider")?.forEach { tspElement ->
            val tspInfo = tspElement.getFirstChildByLocalName("TSPInformation")
            val tspName = tspInfo?.getChildTextContent("TSPName") ?: "Unknown TSP"
            val entityId = generateEntityId(sourceId, tspName)

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = TrustedEntityType.TRUST_SERVICE_PROVIDER,
                legalName = tspName,
                country = territory
            )

            // Parse TSPServices
            val tspServices = tspElement.getFirstChildByLocalName("TSPServices")
            tspServices?.getChildrenByLocalName("TSPService")?.forEachIndexed { svcIdx, svcElement ->
                val svcInfo = svcElement.getFirstChildByLocalName("ServiceInformation")
                val serviceType = svcInfo?.getChildTextContent("ServiceTypeIdentifier") ?: "unknown"
                val statusUri = svcInfo?.getChildTextContent("ServiceStatus") ?: ""
                val statusStart = svcInfo?.getChildTextContent("StatusStartingTime")?.parseInstant()

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
                val digitalIdentity = svcInfo?.getFirstChildByLocalName("ServiceDigitalIdentity")
                digitalIdentity?.getChildrenByLocalName("DigitalId")?.forEachIndexed { idIdx, digitalIdElement ->
                    val x509Cert = digitalIdElement.getChildTextContent("X509Certificate")
                    val x509Ski = digitalIdElement.getChildTextContent("X509SKI")
                    val x509SubjectName = digitalIdElement.getChildTextContent("X509SubjectName")
                    val otherId = digitalIdElement.getChildTextContent("Other")

                    if (x509Cert != null || x509Ski != null || x509SubjectName != null || otherId != null) {
                        val identityId = "$serviceId::id-$idIdx"
                        identities += ServiceIdentity(
                            identityId = identityId,
                            sourceId = sourceId,
                            entityId = entityId,
                            serviceId = serviceId,
                            certificateDerBase64 = x509Cert?.replace("\\s".toRegex(), ""),
                            certificateSha256Hex = x509Cert?.let { computeCertSha256(it) },
                            subjectKeyIdentifierHex = x509Ski?.let(::decodeBase64Hex),
                            subjectDn = x509SubjectName,
                            metadata = buildMap {
                                if (x509Cert != null) put("hasX509Certificate", "true")
                                otherId?.let { put("otherId", it) }
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

    private fun String.parseInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    private fun generateEntityId(sourceId: String, name: String): String {
        val hash = SHA256().digest("$sourceId:$name".toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "tsp-$hash"
    }
    private fun computeCertSha256(base64Cert: String): String? {
        return runCatching {
            val decoded = Base64.decode(base64Cert.replace("\\s".toRegex(), ""))
            SHA256().digest(decoded)
                .joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun decodeBase64Hex(value: String): String = runCatching {
        Base64.decode(value.filterNot(Char::isWhitespace))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }.getOrElse { value }

    private fun evaluateFreshness(nextUpdate: Instant?): FreshnessState {
        nextUpdate ?: return FreshnessState.UNKNOWN
        val now = Clock.System.now()
        return when {
            now > nextUpdate -> FreshnessState.EXPIRED
            now > nextUpdate.minus(Duration.parse("24h")) -> FreshnessState.STALE
            else -> FreshnessState.FRESH
        }
    }

    private fun mapTslStatus(uri: String): TrustStatus {
        val lower = uri.lowercase()
        return when {
            "granted" in lower -> TrustStatus.GRANTED
            "recognized" in lower || "recognised" in lower -> TrustStatus.RECOGNIZED
            "accredited" in lower -> TrustStatus.ACCREDITED
            "undersupervision" in lower || "supervised" in lower -> TrustStatus.SUPERVISED
            "accreditationrevoked" in lower -> TrustStatus.REVOKED
            "accreditationceased" in lower || "supervisionceased" in lower -> TrustStatus.WITHDRAWN
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
