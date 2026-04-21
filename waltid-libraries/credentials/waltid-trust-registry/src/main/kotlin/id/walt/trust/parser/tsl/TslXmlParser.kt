package id.walt.trust.parser.tsl

import id.walt.trust.model.*
import kotlin.time.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import java.security.MessageDigest
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses TS 119 612-style Trusted Lists (TSL/TL) XML into normalized trust model objects.
 *
 * This is an MVP parser — it handles the core structure but does not implement:
 * - full signature validation (set to SKIPPED_DEMO)
 * - all optional extensions
 * - complete historical status replay
 *
 * Input format matches EU LOTL / national TL structure.
 */
object TslXmlParser {

    fun parse(xml: String, sourceId: String, sourceUrl: String? = null): ParsedTslSource {
        val doc = parseXml(xml)
        val root = doc.documentElement

        // Extract scheme information
        val schemeInfo = root.getFirstChild("SchemeInformation")
        val territory = schemeInfo?.getTextContent("SchemeTerritory")
        val issueDate = schemeInfo?.getTextContent("ListIssueDateTime")?.parseInstant()
        val nextUpdate = schemeInfo?.getFirstChild("NextUpdate")?.getTextContent("dateTime")?.parseInstant()
        val sequenceNumber = schemeInfo?.getTextContent("TSLSequenceNumber")
        val versionId = schemeInfo?.getTextContent("TSLVersionIdentifier")

        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.TSL,
            displayName = "TSL $territory (seq $sequenceNumber)",
            sourceUrl = sourceUrl,
            territory = territory,
            issueDate = issueDate,
            nextUpdate = nextUpdate,
            sequenceNumber = sequenceNumber,
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.UNKNOWN,
            metadata = buildMap {
                versionId?.let { put("tslVersion", it) }
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

        return ParsedTslSource(source, entities, services, identities)
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
        val nodes = getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.parentNode == this) return node
        }
        // fallback: search recursively (handles namespace variations)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) return node
        }
        return null
    }

    private fun Element.getChildren(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = getElementsByTagName(tagName)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) result += node
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

data class ParsedTslSource(
    val source: TrustSource,
    val entities: List<TrustedEntity>,
    val services: List<TrustedService>,
    val identities: List<ServiceIdentity>
)
