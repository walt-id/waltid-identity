package id.walt.trust.parser.lote

import id.walt.trust.model.*
import id.walt.trust.utils.HashUtils.computeCertificateSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * Parses TS 119 602-style LoTE JSON sources into normalized trust model objects.
 *
 * Input format matches the synthetic sample at:
 * waltid-architecture/enterprise/trust-lists/samples/sample-lote-wallet-providers.synthetic.json
 *
 * Note: this parser handles the MVP JSON shape. It is intentionally lenient —
 * unknown fields are ignored. The format is provisional pending TS 119 605 stabilisation.
 */
@OptIn(ExperimentalEncodingApi::class)
object LoteJsonParser {

    // ---------------------------------------------------------------------------
    // Raw JSON shape (internal deserialization models)
    // ---------------------------------------------------------------------------

    @Serializable
    private data class RawLoteDocument(
        val listMetadata: RawListMetadata,
        val trustedEntities: List<RawTrustedEntity>
    )

    @Serializable
    private data class RawListMetadata(
        val listId: String,
        val listType: String? = null,
        val territory: String? = null,
        val issueDate: String? = null,
        val nextUpdate: String? = null,
        val sequenceNumber: String? = null
    )

    @Serializable
    private data class RawTrustedEntity(
        val entityId: String,
        val entityType: String,
        val legalName: String,
        val tradeName: String? = null,
        val registrationNumber: String? = null,
        val country: String? = null,
        val services: List<RawService> = emptyList()
    )

    @Serializable
    private data class RawService(
        val serviceId: String,
        val serviceType: String,
        val status: String,
        val statusStart: String? = null,
        val identities: List<RawIdentity> = emptyList()
    )

    @Serializable
    private data class RawIdentity(
        val matchType: String,
        val value: String
    )

    // ---------------------------------------------------------------------------
    // Parse entry point
    // ---------------------------------------------------------------------------

    fun parse(json: String, sourceId: String, sourceUrl: String? = null): ParsedLoteSource {
        val doc = Json { ignoreUnknownKeys = true }.decodeFromString<RawLoteDocument>(json)
        val meta = doc.listMetadata

        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.LOTE,
            displayName = meta.listId,
            sourceUrl = sourceUrl,
            territory = meta.territory,
            issueDate = meta.issueDate?.let { runCatching { Instant.parse(it) }.getOrNull() },
            nextUpdate = meta.nextUpdate?.let { runCatching { Instant.parse(it) }.getOrNull() },
            sequenceNumber = meta.sequenceNumber,
            authenticityState = AuthenticityState.SKIPPED_DEMO,
            freshnessState = FreshnessState.UNKNOWN,
            metadata = buildMap {
                meta.listType?.let { put("listType", it) }
            }
        )

        val entities = mutableListOf<TrustedEntity>()
        val services = mutableListOf<TrustedService>()
        val identities = mutableListOf<ServiceIdentity>()

        doc.trustedEntities.forEach { rawEntity ->
            val entityType = mapEntityType(rawEntity.entityType)

            entities += TrustedEntity(
                entityId = rawEntity.entityId,
                sourceId = sourceId,
                entityType = entityType,
                legalName = rawEntity.legalName,
                tradeName = rawEntity.tradeName,
                registrationNumber = rawEntity.registrationNumber,
                country = rawEntity.country
            )

            rawEntity.services.forEach { rawService ->
                val serviceId = "${rawEntity.entityId}::${rawService.serviceId}"

                services += TrustedService(
                    serviceId = serviceId,
                    sourceId = sourceId,
                    entityId = rawEntity.entityId,
                    serviceType = rawService.serviceType,
                    status = mapStatus(rawService.status),
                    statusStart = rawService.statusStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
                )

                rawService.identities.forEachIndexed { idx, rawId ->
                    val identityId = "$serviceId::id-$idx"
                    identities += buildServiceIdentity(
                        identityId = identityId,
                        sourceId = sourceId,
                        entityId = rawEntity.entityId,
                        serviceId = serviceId,
                        matchType = rawId.matchType,
                        value = rawId.value
                    )
                }
            }
        }

        return ParsedLoteSource(source, entities, services, identities)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
            "CERTIFICATE_PEM", "CERTIFICATE_DER" -> {
                // Compute SHA-256 from PEM or DER certificate
                val sha256 = computeCertificateSha256(value)
                ServiceIdentity(
                    identityId = identityId,
                    sourceId = sourceId,
                    entityId = entityId,
                    serviceId = serviceId,
                    certificateSha256Hex = sha256,
                    metadata = if (sha256 == null) mapOf("rawMatchType" to matchType, "rawValue" to value) else emptyMap()
                )
            }
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

data class ParsedLoteSource(
    val source: TrustSource,
    val entities: List<TrustedEntity>,
    val services: List<TrustedService>,
    val identities: List<ServiceIdentity>
)
