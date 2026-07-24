package id.walt.trust.parser.lote

import id.walt.trust.model.*
import id.walt.trust.utils.HashUtils.computeCertificateSha256
import id.walt.trust.utils.HashUtils.normalizeCertificateDerBase64
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

/**
 * Parses the normative scheme-explicit JSON binding from ETSI TS 119 602
 * V1.1.1, Annex A.1.
 *
 * Input is validated against the ETSI-published JSON Schema before any data is normalized.
 */
object LoteJsonParser {

    private val json = Json
    private val schema by lazy { JsonSchema.fromJsonElement(EtsiLoteJsonSchema.schema) }

    fun isNormative(json: String): Boolean = runCatching {
        Json.parseToJsonElement(json).jsonObject.containsKey("LoTE")
    }.getOrDefault(false)

    fun parse(
        json: String,
        sourceId: String,
        sourceUrl: String? = null,
        assurance: SourceAssurance = SourceAssurance(
            signatureStatus = SignatureStatus.NOT_PRESENT,
            signerTrust = SignerTrust.NOT_APPLICABLE,
            authenticityState = AuthenticityState.UNVERIFIED,
            details = "Unsigned ETSI TS 119 602 JSON"
        ),
        validationMetadata: Map<String, String> = emptyMap()
    ): ParsedLoteSource {
        val document = Json.parseToJsonElement(json)
        val errors = mutableListOf<ValidationError>()
        require(schema.validate(document, errors::add)) {
            errors.joinToString(
                prefix = "ETSI TS 119 602 JSON Schema validation failed: ",
                separator = "; ",
                limit = 10
            ) { it.toString() }
        }

        val lote = document.jsonObject.requiredObject("LoTE")
        val scheme = lote.requiredObject("ListAndSchemeInformation")
        val loteType = scheme.string("LoTEType")
        val territory = scheme.string("SchemeTerritory")
        val displayName = scheme.multiLangValue("SchemeName")
            ?: scheme.multiLangValue("SchemeOperatorName")
            ?: sourceId

        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.LOTE,
            format = TrustListFormat.ETSI_TS_119_602_JSON,
            displayName = displayName,
            sourceUrl = sourceUrl,
            territory = territory,
            issueDate = scheme.string("ListIssueDateTime")?.parseInstant(),
            nextUpdate = scheme.string("NextUpdate")?.parseInstant(),
            sequenceNumber = scheme.primitive("LoTESequenceNumber")?.content,
            assurance = assurance,
            freshnessState = FreshnessState.UNKNOWN,
            metadata = buildMap {
                put("syntax", "ETSI_TS_119_602_V1_1_1_JSON")
                put("schemaCommit", EtsiLoteJsonSchema.SOURCE_COMMIT)
                scheme.primitive("LoTEVersionIdentifier")?.content?.let { put("loteVersionIdentifier", it) }
                loteType?.let { put("loteType", it) }
                putAll(validationMetadata)
            }
        )

        val entities = mutableListOf<TrustedEntity>()
        val services = mutableListOf<TrustedService>()
        val identities = mutableListOf<ServiceIdentity>()
        val rawEntities = lote.array("TrustedEntitiesList") ?: JsonArray(emptyList())

        rawEntities.forEachIndexed { entityIndex, entityElement ->
            val entityObject = entityElement.jsonObject
            val info = entityObject.requiredObject("TrustedEntityInformation")
            val informationUris = info.multiLangUris("TEInformationURI")
            val entityId = informationUris.firstOrNull { "/ListOfTrustedEntities/" in it }
                ?: informationUris.firstOrNull()
                ?: "$sourceId::entity-$entityIndex"
            val legalName = info.multiLangValue("TEName") ?: entityId
            val country = info.objectOrNull("TEAddress")
                ?.array("TEPostalAddress")
                ?.firstOrNull()
                ?.jsonObject
                ?.string("Country")

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = mapEntityType(loteType),
                legalName = legalName,
                tradeName = info.multiLangValue("TETradeName"),
                country = country,
                metadata = buildMap {
                    put("entityIndex", entityIndex.toString())
                    if (informationUris.isNotEmpty()) put("informationUris", informationUris.joinToString(" "))
                }
            )

            val rawServices = entityObject.array("TrustedEntityServices") ?: JsonArray(emptyList())
            rawServices.forEachIndexed { serviceIndex, serviceElement ->
                val serviceInfo = serviceElement.jsonObject.requiredObject("ServiceInformation")
                val serviceId = "$entityId::service-$serviceIndex"
                val serviceType = serviceInfo.string("ServiceTypeIdentifier") ?: "urn:etsi:ts:119602:service:unspecified"
                val rawStatus = serviceInfo.string("ServiceStatus")

                services += TrustedService(
                    serviceId = serviceId,
                    sourceId = sourceId,
                    entityId = entityId,
                    serviceType = serviceType,
                    status = mapStatus(rawStatus),
                    statusStart = serviceInfo.string("StatusStartingTime")?.parseInstant(),
                    metadata = buildMap {
                        serviceInfo.multiLangValue("ServiceName")?.let { put("serviceName", it) }
                        rawStatus?.let { put("rawStatusUri", it) }
                    }
                )

                val digitalIdentity = serviceInfo.requiredObject("ServiceDigitalIdentity")
                identities += parseIdentities(digitalIdentity, sourceId, entityId, serviceId)
            }
        }

        return ParsedLoteSource(source, entities, services, identities)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseIdentities(
        digitalIdentity: JsonObject,
        sourceId: String,
        entityId: String,
        serviceId: String
    ): List<ServiceIdentity> = buildList {
        var index = 0
        digitalIdentity.array("X509Certificates")?.forEach { value ->
            val encoded = value.jsonObject.requiredString("val")
            add(ServiceIdentity(
                identityId = "$serviceId::id-${index++}",
                sourceId = sourceId,
                entityId = entityId,
                serviceId = serviceId,
                certificateDerBase64 = normalizeCertificateDerBase64(encoded),
                certificateSha256Hex = computeCertificateSha256(encoded),
                metadata = value.jsonObject.string("encoding")?.let { mapOf("encoding" to it) } ?: emptyMap()
            ))
        }
        digitalIdentity.array("X509SubjectNames")?.forEach { value ->
            add(ServiceIdentity(
                identityId = "$serviceId::id-${index++}", sourceId = sourceId,
                entityId = entityId, serviceId = serviceId,
                subjectDn = value.jsonPrimitive.content
            ))
        }
        digitalIdentity.array("X509SKIs")?.forEach { value ->
            val ski = value.jsonPrimitive.content
            val skiHex = runCatching {
                Base64.decode(ski).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
            }.getOrElse { ski }
            add(ServiceIdentity(
                identityId = "$serviceId::id-${index++}", sourceId = sourceId,
                entityId = entityId, serviceId = serviceId,
                subjectKeyIdentifierHex = skiHex
            ))
        }
        digitalIdentity.array("PublicKeyValues")?.forEach { value ->
            add(ServiceIdentity(
                identityId = "$serviceId::id-${index++}", sourceId = sourceId,
                entityId = entityId, serviceId = serviceId,
                metadata = mapOf("publicKeyJwk" to json.encodeToString(JsonElement.serializer(), value))
            ))
        }
        digitalIdentity.array("OtherIds")?.forEach { value ->
            add(ServiceIdentity(
                identityId = "$serviceId::id-${index++}", sourceId = sourceId,
                entityId = entityId, serviceId = serviceId,
                metadata = mapOf("otherId" to value.jsonPrimitive.content)
            ))
        }
    }

    private fun mapEntityType(loteType: String?): TrustedEntityType = when {
        loteType == null -> TrustedEntityType.OTHER
        "EUPIDProvidersList" in loteType -> TrustedEntityType.PID_PROVIDER
        "EUWalletProvidersList" in loteType -> TrustedEntityType.WALLET_PROVIDER
        "EUWRPACProvidersList" in loteType -> TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER
        "EUWRPRCProvidersList" in loteType -> TrustedEntityType.RELYING_PARTY_PROVIDER
        "EUPubEAAProvidersList" in loteType -> TrustedEntityType.ATTESTATION_PROVIDER
        else -> TrustedEntityType.OTHER
    }

    private fun mapStatus(raw: String?): TrustStatus = when (raw?.substringAfterLast('/')?.lowercase()) {
        "granted", "recognized", "accredited", "supervised", "inaccord" -> TrustStatus.GRANTED
        "deprecated" -> TrustStatus.DEPRECATED
        "suspended" -> TrustStatus.SUSPENDED
        "revoked" -> TrustStatus.REVOKED
        "withdrawn", "notinaccord" -> TrustStatus.WITHDRAWN
        "expired" -> TrustStatus.EXPIRED
        else -> TrustStatus.UNKNOWN
    }

    private fun String.parseInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
    private fun JsonObject.primitive(name: String): JsonPrimitive? = get(name) as? JsonPrimitive
    private fun JsonObject.string(name: String): String? = primitive(name)?.contentOrNull
    private fun JsonObject.requiredString(name: String): String = requireNotNull(string(name)) { "Missing $name" }
    private fun JsonObject.objectOrNull(name: String): JsonObject? = get(name) as? JsonObject
    private fun JsonObject.requiredObject(name: String): JsonObject = requireNotNull(objectOrNull(name)) { "Missing $name" }
    private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
    private fun JsonObject.multiLangValue(name: String): String? {
        val values = array(name)?.mapNotNull { it as? JsonObject } ?: return null
        return values.firstOrNull { it.string("lang") == "en" }?.string("value")
            ?: values.firstNotNullOfOrNull { it.string("value") }
    }
    private fun JsonObject.multiLangUris(name: String): List<String> =
        array(name)?.mapNotNull { (it as? JsonObject)?.string("uriValue") } ?: emptyList()
}

data class ParsedLoteSource(
    val source: TrustSource,
    val entities: List<TrustedEntity>,
    val services: List<TrustedService>,
    val identities: List<ServiceIdentity>
)
