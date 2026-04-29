package id.walt.trust.parser.lote

import id.walt.trust.model.*
import id.walt.trust.parser.SecureXmlParser
import id.walt.trust.parser.getChildTextContent
import id.walt.trust.parser.getChildrenByLocalName
import id.walt.trust.parser.getFirstChildByLocalName
import kotlin.time.Instant

/**
 * Parses TS 119 602-style LoTE XML sources into normalized trust model objects.
 *
 * Input format matches the synthetic sample at:
 * waltid-architecture/enterprise/trust-lists/samples/sample-lote-pid-providers.synthetic.xml
 */
object LoteXmlParser {

    fun parse(xml: String, sourceId: String, sourceUrl: String? = null): ParsedLoteSource {
        val doc = SecureXmlParser.parseXml(xml)
        val root = doc.documentElement

        // Extract list metadata
        val metaElement = root.getFirstChildByLocalName("ListMetadata")
        val listId = metaElement?.getChildTextContent("ListId") ?: sourceId
        val listType = metaElement?.getChildTextContent("ListType")
        val territory = metaElement?.getChildTextContent("Territory")
        val issueDate = metaElement?.getChildTextContent("IssueDate")?.parseInstant()
        val nextUpdate = metaElement?.getChildTextContent("NextUpdate")?.parseInstant()
        val sequenceNumber = metaElement?.getChildTextContent("SequenceNumber")

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

        root.getChildrenByLocalName("TrustedEntity").forEach { entityElement ->
            val entityId = entityElement.getChildTextContent("EntityId") ?: return@forEach
            val entityTypeRaw = entityElement.getChildTextContent("EntityType") ?: "OTHER"
            val legalName = entityElement.getChildTextContent("LegalName") ?: "Unknown"
            val country = entityElement.getChildTextContent("Country")

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = mapEntityType(entityTypeRaw),
                legalName = legalName,
                country = country
            )

            entityElement.getChildrenByLocalName("TrustedService").forEach { svcElement ->
                val svcId = svcElement.getChildTextContent("ServiceId") ?: return@forEach
                val serviceId = "$entityId::$svcId"
                val serviceType = svcElement.getChildTextContent("ServiceType") ?: "unknown"
                val statusRaw = svcElement.getChildTextContent("Status") ?: "UNKNOWN"
                val statusStart = svcElement.getChildTextContent("StatusStart")?.parseInstant()

                services += TrustedService(
                    serviceId = serviceId,
                    sourceId = sourceId,
                    entityId = entityId,
                    serviceType = serviceType,
                    status = mapStatus(statusRaw),
                    statusStart = statusStart
                )

                svcElement.getChildrenByLocalName("Identity").forEachIndexed { idx, idElement ->
                    val matchType = idElement.getChildTextContent("MatchType") ?: return@forEachIndexed
                    val value = idElement.getChildTextContent("Value") ?: return@forEachIndexed
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
