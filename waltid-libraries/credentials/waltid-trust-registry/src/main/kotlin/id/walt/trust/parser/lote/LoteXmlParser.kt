package id.walt.trust.parser.lote

import id.walt.trust.model.*
import kotlinx.datetime.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses TS 119 602-style LoTE XML sources into normalized trust model objects.
 *
 * Input format matches the synthetic sample at:
 * waltid-architecture/enterprise/trust-lists/samples/sample-lote-pid-providers.synthetic.xml
 */
object LoteXmlParser {

    fun parse(xml: String, sourceId: String, sourceUrl: String? = null): ParsedLoteSource {
        val doc = parseXml(xml)
        val root = doc.documentElement

        // Extract list metadata
        val metaElement = root.getFirstChild("ListMetadata")
        val listId = metaElement?.getTextContent("ListId") ?: sourceId
        val listType = metaElement?.getTextContent("ListType")
        val territory = metaElement?.getTextContent("Territory")
        val issueDate = metaElement?.getTextContent("IssueDate")?.parseInstant()
        val nextUpdate = metaElement?.getTextContent("NextUpdate")?.parseInstant()
        val sequenceNumber = metaElement?.getTextContent("SequenceNumber")

        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.LOTE,
            displayName = listId,
            sourceUrl = sourceUrl,
            territory = territory,
            issueDate = issueDate,
            nextUpdate = nextUpdate,
            sequenceNumber = sequenceNumber,
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.UNKNOWN,
            metadata = buildMap {
                listType?.let { put("listType", it) }
            }
        )

        val entities = mutableListOf<TrustedEntity>()
        val services = mutableListOf<TrustedService>()
        val identities = mutableListOf<ServiceIdentity>()

        root.getChildren("TrustedEntity").forEach { entityElement ->
            val entityId = entityElement.getTextContent("EntityId") ?: return@forEach
            val entityTypeRaw = entityElement.getTextContent("EntityType") ?: "OTHER"
            val legalName = entityElement.getTextContent("LegalName") ?: "Unknown"
            val country = entityElement.getTextContent("Country")

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = mapEntityType(entityTypeRaw),
                legalName = legalName,
                country = country
            )

            entityElement.getChildren("TrustedService").forEach { svcElement ->
                val svcId = svcElement.getTextContent("ServiceId") ?: return@forEach
                val serviceId = "$entityId::$svcId"
                val serviceType = svcElement.getTextContent("ServiceType") ?: "unknown"
                val statusRaw = svcElement.getTextContent("Status") ?: "UNKNOWN"
                val statusStart = svcElement.getTextContent("StatusStart")?.parseInstant()

                services += TrustedService(
                    serviceId = serviceId,
                    sourceId = sourceId,
                    entityId = entityId,
                    serviceType = serviceType,
                    status = mapStatus(statusRaw),
                    statusStart = statusStart
                )

                svcElement.getChildren("Identity").forEachIndexed { idx, idElement ->
                    val matchType = idElement.getTextContent("MatchType") ?: return@forEachIndexed
                    val value = idElement.getTextContent("Value") ?: return@forEachIndexed
                    val identityId = "$serviceId::id-$idx"

                    identities += buildServiceIdentity(
                        identityId = identityId,
                        sourceId = sourceId,
                        entityId = entityId,
                        serviceId = serviceId,
                        matchType = matchType,
                        value = value
                    )
                }
            }
        }

        return ParsedLoteSource(source, entities, services, identities)
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

    private fun buildServiceIdentity(
        identityId: String,
        sourceId: String,
        entityId: String,
        serviceId: String,
        matchType: String,
        value: String
    ): ServiceIdentity {
        return when (matchType.uppercase()) {
            "CERTIFICATE_SHA256" -> ServiceIdentity(
                identityId = identityId,
                sourceId = sourceId,
                entityId = entityId,
                serviceId = serviceId,
                certificateSha256Hex = value.removePrefix("sha256:")
            )
            "SUBJECT_DN" -> ServiceIdentity(
                identityId = identityId,
                sourceId = sourceId,
                entityId = entityId,
                serviceId = serviceId,
                subjectDn = value
            )
            "SKI" -> ServiceIdentity(
                identityId = identityId,
                sourceId = sourceId,
                entityId = entityId,
                serviceId = serviceId,
                subjectKeyIdentifierHex = value
            )
            else -> ServiceIdentity(
                identityId = identityId,
                sourceId = sourceId,
                entityId = entityId,
                serviceId = serviceId,
                metadata = mapOf("rawMatchType" to matchType, "rawValue" to value)
            )
        }
    }

    private fun mapEntityType(raw: String): TrustedEntityType = when (raw.uppercase()) {
        "WALLET_PROVIDER" -> TrustedEntityType.WALLET_PROVIDER
        "PID_PROVIDER" -> TrustedEntityType.PID_PROVIDER
        "ATTESTATION_PROVIDER" -> TrustedEntityType.ATTESTATION_PROVIDER
        "ACCESS_CERTIFICATE_PROVIDER" -> TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER
        "TRUST_SERVICE_PROVIDER" -> TrustedEntityType.TRUST_SERVICE_PROVIDER
        "RELYING_PARTY_PROVIDER" -> TrustedEntityType.RELYING_PARTY_PROVIDER
        else -> TrustedEntityType.OTHER
    }

    private fun mapStatus(raw: String): TrustStatus = when (raw.uppercase()) {
        "GRANTED" -> TrustStatus.GRANTED
        "RECOGNIZED" -> TrustStatus.RECOGNIZED
        "ACCREDITED" -> TrustStatus.ACCREDITED
        "SUPERVISED" -> TrustStatus.SUPERVISED
        "DEPRECATED" -> TrustStatus.DEPRECATED
        "SUSPENDED" -> TrustStatus.SUSPENDED
        "REVOKED" -> TrustStatus.REVOKED
        "WITHDRAWN" -> TrustStatus.WITHDRAWN
        "EXPIRED" -> TrustStatus.EXPIRED
        else -> TrustStatus.UNKNOWN
    }
}
