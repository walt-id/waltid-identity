package id.walt.trust.parser.lote

import id.walt.trust.model.*
import id.walt.trust.parser.SecureXmlParser
import id.walt.trust.signature.SignatureValidationConfig
import id.walt.trust.signature.XmlDsigValidator
import id.walt.trust.utils.HashUtils.computeCertificateSha256
import id.walt.trust.utils.HashUtils.normalizeCertificateDerBase64
import org.w3c.dom.Element
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Instant

data class LoteXmlParseConfig(
    val validateSignature: Boolean = true,
    val signatureConfig: SignatureValidationConfig = SignatureValidationConfig(),
    val requireSignature: Boolean = true
)

/** Parses the normative ETSI TS 119 602 V1.1.1 Annex A.2.1 XML binding. */
object LoteXmlParser {
    const val NAMESPACE = "http://uri.etsi.org/019602/v1#"

    fun isNormative(xml: String): Boolean = runCatching {
        val root = SecureXmlParser.parseXml(xml).documentElement
        root.localName == "ListOfTrustedEntities" && root.namespaceURI == NAMESPACE
    }.getOrDefault(false)

    fun parse(
        xml: String,
        sourceId: String,
        sourceUrl: String? = null,
        config: LoteXmlParseConfig = LoteXmlParseConfig()
    ): ParsedLoteSource {
        EtsiLoteXmlSchemaValidator.validate(xml)
        val root = SecureXmlParser.parseXml(xml).documentElement
        require(root.localName == "ListOfTrustedEntities" && root.namespaceURI == NAMESPACE) {
            "Expected ETSI TS 119 602 ListOfTrustedEntities in namespace $NAMESPACE"
        }

        val signature = if (config.validateSignature) {
            XmlDsigValidator.validate(xml, config.signatureConfig)
        } else null
        val assurance = when {
            signature == null -> SourceAssurance(
                SignatureStatus.NOT_CHECKED, SignerTrust.NOT_EVALUATED,
                AuthenticityState.UNVERIFIED, details = "XML signature verification was disabled"
            )
            signature.signatureStatus == SignatureStatus.NOT_PRESENT && config.requireSignature -> SourceAssurance(
                SignatureStatus.NOT_PRESENT, SignerTrust.NOT_APPLICABLE,
                AuthenticityState.FAILED, details = "ETSI TS 119 602 requires a Baseline-B signature"
            )
            signature.signatureStatus == SignatureStatus.NOT_PRESENT -> SourceAssurance(
                SignatureStatus.NOT_PRESENT, SignerTrust.NOT_APPLICABLE,
                AuthenticityState.UNVERIFIED, details = signature.details
            )
            else -> SourceAssurance(
                signature.signatureStatus, signature.signerTrust, signature.state,
                details = signature.details
            )
        }

        val scheme = root.direct("ListAndSchemeInformation")
            ?: error("Missing ListAndSchemeInformation")
        val loteType = scheme.text("LoTEType")
        val source = TrustSource(
            sourceId = sourceId,
            sourceFamily = SourceFamily.LOTE,
            format = TrustListFormat.ETSI_TS_119_602_XML,
            displayName = scheme.multiLang("SchemeName")
                ?: scheme.multiLang("SchemeOperatorName")
                ?: sourceId,
            sourceUrl = sourceUrl,
            territory = scheme.text("SchemeTerritory"),
            issueDate = scheme.text("ListIssueDateTime")?.parseInstant(),
            nextUpdate = scheme.direct("NextUpdate")?.text("dateTime")?.parseInstant(),
            sequenceNumber = scheme.text("LoTESequenceNumber"),
            assurance = assurance,
            metadata = buildMap {
                put("syntax", "ETSI_TS_119_602_V1_1_1_XML")
                put("schemaCommit", EtsiLoteXmlSchemaValidator.SCHEMA_COMMIT)
                put("loteTag", root.getAttribute("LOTETag"))
                scheme.text("LoTEVersionIdentifier")?.let { put("loteVersionIdentifier", it) }
                loteType?.let { put("loteType", it) }
            }
        )

        val entities = mutableListOf<TrustedEntity>()
        val services = mutableListOf<TrustedService>()
        val identities = mutableListOf<ServiceIdentity>()

        root.direct("TrustedEntitiesList")?.directChildren("TrustedEntity")?.forEachIndexed { entityIndex, rawEntity ->
            val info = rawEntity.direct("TrustedEntityInformation") ?: error("Missing TrustedEntityInformation")
            val informationUris = info.direct("TEInformationURI")
                ?.directChildren("URI")?.map { it.textContent.trim() }.orEmpty()
            val entityId = informationUris.firstOrNull { "/ListOfTrustedEntities/" in it }
                ?: informationUris.firstOrNull()
                ?: "$sourceId::entity-$entityIndex"
            val address = info.direct("TEAddress")
            val country = address?.direct("PostalAddresses")?.direct("PostalAddress")?.text("CountryName")

            entities += TrustedEntity(
                entityId = entityId,
                sourceId = sourceId,
                entityType = mapEntityType(loteType),
                legalName = info.multiLang("TEName") ?: entityId,
                tradeName = info.multiLang("TETradeName"),
                country = country,
                metadata = if (informationUris.isEmpty()) emptyMap()
                else mapOf("informationUris" to informationUris.joinToString(" "))
            )

            rawEntity.direct("TrustedEntityServices")
                ?.directChildren("TrustedEntityService")
                ?.forEachIndexed { serviceIndex, rawService ->
                    val serviceInfo = rawService.direct("ServiceInformation") ?: error("Missing ServiceInformation")
                    val serviceId = "$entityId::service-$serviceIndex"
                    val rawStatus = serviceInfo.text("ServiceStatus")
                    services += TrustedService(
                        serviceId = serviceId,
                        sourceId = sourceId,
                        entityId = entityId,
                        serviceType = serviceInfo.text("ServiceTypeIdentifier")
                            ?: "urn:etsi:ts:119602:service:unspecified",
                        status = mapStatus(rawStatus),
                        statusStart = serviceInfo.text("StatusStartingTime")?.parseInstant(),
                        metadata = buildMap {
                            serviceInfo.multiLang("ServiceName")?.let { put("serviceName", it) }
                            rawStatus?.let { put("rawStatusUri", it) }
                        }
                    )
                    identities += parseIdentities(
                        serviceInfo.direct("ServiceDigitalIdentity") ?: error("Missing ServiceDigitalIdentity"),
                        sourceId, entityId, serviceId
                    )
                }
        }

        return ParsedLoteSource(source, entities, services, identities)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseIdentities(
        container: Element, sourceId: String, entityId: String, serviceId: String
    ): List<ServiceIdentity> = container.directChildren("DigitalId").mapIndexedNotNull { index, digitalId ->
        val certificate = digitalId.direct("X509Certificate")?.textContent?.filterNot(Char::isWhitespace)
        val subject = digitalId.text("X509SubjectName")
        val ski = digitalId.text("X509SKI")
        val keyValue = digitalId.direct("KeyValue")
        val otherId = digitalId.direct("OtherId")
        when {
            certificate != null -> ServiceIdentity(
                "$serviceId::id-$index", sourceId, entityId, serviceId,
                certificateDerBase64 = normalizeCertificateDerBase64(certificate),
                certificateSha256Hex = computeCertificateSha256(certificate)
            )
            subject != null -> ServiceIdentity(
                "$serviceId::id-$index", sourceId, entityId, serviceId, subjectDn = subject
            )
            ski != null -> ServiceIdentity(
                "$serviceId::id-$index", sourceId, entityId, serviceId,
                subjectKeyIdentifierHex = runCatching {
                    Base64.decode(ski.filterNot(Char::isWhitespace))
                        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                }.getOrElse { ski }
            )
            keyValue != null -> ServiceIdentity(
                "$serviceId::id-$index", sourceId, entityId, serviceId,
                metadata = mapOf("xmlDsigKeyValue" to keyValue.textContent.trim())
            )
            otherId != null -> ServiceIdentity(
                "$serviceId::id-$index", sourceId, entityId, serviceId,
                metadata = mapOf("otherId" to otherId.textContent.trim())
            )
            else -> null
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
    private fun Element.direct(name: String): Element? = directChildren(name).firstOrNull()
    private fun Element.directChildren(name: String): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val child = nodes.item(index)
            if (child is Element && child.localName == name) add(child)
        }
    }
    private fun Element.text(name: String): String? = direct(name)?.textContent?.trim()?.takeIf(String::isNotEmpty)
    private fun Element.multiLang(name: String): String? {
        val names = direct(name)?.directChildren("Name").orEmpty()
        return names.firstOrNull { it.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang") == "en" }
            ?.textContent?.trim()
            ?: names.firstOrNull()?.textContent?.trim()
    }
}
