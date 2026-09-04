@file:OptIn(
    ExperimentalUnsignedTypes::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity

import id.walt.mdoc.encoding.toMdocTDateString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

object RicalSerializer : KSerializer<Rical> {
    override val descriptor: SerialDescriptor = CborElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Rical) {
        val fields = linkedMapOf<String, CborElement>(
            "version" to CborString(value.version),
            "provider" to CborString(value.provider),
            "date" to value.date.toTDate(),
            "certificateInfos" to CborArray(value.certificateInfos.map(RicalCertificateInfo::toElement)),
            "type" to CborString(value.type),
        )
        value.nextUpdate?.let { fields["nextUpdate"] = it.toTDate() }
        value.notAfter?.let { fields["notAfter"] = it.toTDate() }
        value.id?.let { fields["id"] = CborInteger(it) }
        value.latestRicalUrl?.let { fields["latestRicalUrl"] = CborString(it) }
        if (value.extensions.isNotEmpty()) fields["extensions"] = value.extensions.toTextMap()
        fields.putAll(value.reserved)
        encoder.encodeSerializableValue(CborElement.serializer(), fields.toTextMap())
    }

    override fun deserialize(decoder: Decoder): Rical {
        val fields = decoder.decodeSerializableValue(CborElement.serializer()).asTextFields("RICAL")
        return Rical(
            version = fields.requiredText("version", "RICAL"),
            provider = fields.requiredText("provider", "RICAL"),
            date = fields.requiredTDate("date", "RICAL"),
            nextUpdate = fields["nextUpdate"]?.asTDate("RICAL nextUpdate"),
            notAfter = fields["notAfter"]?.asTDate("RICAL notAfter"),
            certificateInfos = fields["certificateInfos"]?.let { element ->
                (element as? CborArray)?.map(CborElement::toCertificateInfo)
                    ?: throw SerializationException("RICAL certificateInfos must be an array")
            } ?: throw SerializationException("RICAL certificateInfos is required"),
            id = fields["id"]?.asUInt64("RICAL id"),
            latestRicalUrl = fields.optionalText("latestRicalUrl", "RICAL"),
            type = fields.requiredText("type", "RICAL"),
            extensions = fields["extensions"]?.asTextFields("RICAL extensions").orEmpty(),
            reserved = fields.filterKeys { it !in RICAL_FIELDS },
        )
    }
}

private fun RicalCertificateInfo.toElement(): CborElement {
    val fields = linkedMapOf<String, CborElement>(
        "certificate" to CborByteString(certificateDer.copy()),
        "serialNumber" to CborByteString(serialNumber.copy(), 2u),
        "isTrustAnchor" to CborBoolean(isTrustAnchor),
        "ski" to CborByteString(subjectKeyIdentifier.copy()),
    )
    authorityKeyIdentifier?.let { fields["aki"] = CborByteString(it.copy()) }
    type?.let { fields["type"] = CborString(it) }
    if (trustConstraints.isNotEmpty()) {
        // The current DIS CDDL spells this field "trustContraints". Preserve that exact wire spelling.
        fields["trustContraints"] = CborArray(trustConstraints.map { it.values.toTextMap() })
    }
    name?.let { fields["name"] = CborString(it) }
    issuingCountry?.let { fields["issuingCountry"] = CborString(it) }
    stateOrProvinceName?.let { fields["stateOrProvinceName"] = CborString(it) }
    issuerDer?.let { fields["issuer"] = CborByteString(it.copy()) }
    subjectDer?.let { fields["subject"] = CborByteString(it.copy()) }
    notBefore?.let { fields["notBefore"] = it.toTDate() }
    notAfter?.let { fields["notAfter"] = it.toTDate() }
    if (extensions.isNotEmpty()) fields["extensions"] = extensions.toTextMap()
    fields.putAll(reserved)
    return fields.toTextMap()
}

private fun CborElement.toCertificateInfo(): RicalCertificateInfo {
    val fields = asTextFields("RICALCertificateInfo")
    return RicalCertificateInfo(
        certificateDer = ImmutableBytes.of(fields.requiredBytes("certificate", "RICALCertificateInfo")),
        serialNumber = ImmutableBytes.of(
            fields["serialNumber"]?.asPositiveBignum("RICALCertificateInfo serialNumber")
                ?: throw SerializationException("RICALCertificateInfo serialNumber is required")
        ),
        subjectKeyIdentifier = ImmutableBytes.of(fields.requiredBytes("ski", "RICALCertificateInfo")),
        isTrustAnchor = fields.requiredBoolean("isTrustAnchor", "RICALCertificateInfo"),
        authorityKeyIdentifier = fields["aki"]?.asBytes("RICALCertificateInfo aki")?.let(ImmutableBytes::of),
        type = fields.optionalText("type", "RICALCertificateInfo"),
        trustConstraints = fields["trustContraints"]?.let { element ->
            val constraints = (element as? CborArray)?.map {
                RicalTrustConstraint(it.asTextFields("RICAL TrustConstraint"))
            } ?: throw SerializationException("RICAL trustContraints must be an array")
            constraints.also {
                if (it.isEmpty()) throw SerializationException("RICAL trustContraints must not be empty when present")
            }
        }.orEmpty(),
        name = fields.optionalText("name", "RICALCertificateInfo"),
        issuingCountry = fields.optionalText("issuingCountry", "RICALCertificateInfo"),
        stateOrProvinceName = fields.optionalText("stateOrProvinceName", "RICALCertificateInfo"),
        issuerDer = fields["issuer"]?.asBytes("RICALCertificateInfo issuer")?.let(ImmutableBytes::of),
        subjectDer = fields["subject"]?.asBytes("RICALCertificateInfo subject")?.let(ImmutableBytes::of),
        notBefore = fields["notBefore"]?.asTDate("RICALCertificateInfo notBefore"),
        notAfter = fields["notAfter"]?.asTDate("RICALCertificateInfo notAfter"),
        extensions = fields["extensions"]?.asTextFields("RICALCertificateInfo extensions").orEmpty(),
        reserved = fields.filterKeys { it !in CERTIFICATE_INFO_FIELDS },
    )
}

private fun Instant.toTDate(): CborElement = CborString(toMdocTDateString(), 0u)

private fun CborElement.asTDate(field: String): Instant {
    val value = this as? CborString ?: throw SerializationException("$field must be a tag-0 date-time string")
    if (0uL !in value.tags) throw SerializationException("$field must use CBOR tag 0")
    return try {
        Instant.parse(value.value)
    } catch (cause: IllegalArgumentException) {
        throw SerializationException("$field is not a valid RFC 3339 date-time", cause)
    }
}

private fun CborElement.asPositiveBignum(field: String): ByteArray {
    val value = this as? CborByteString ?: throw SerializationException("$field must be a positive bignum")
    if (2uL !in value.tags) throw SerializationException("$field must use CBOR positive-bignum tag 2")
    return value.toByteArray().also {
        if (it.isEmpty() || it.size > 1 && it.first() == 0.toByte()) {
            throw SerializationException("$field must use a non-empty minimal unsigned magnitude")
        }
    }
}

private fun CborElement.asBytes(field: String): ByteArray =
    (this as? CborByteString)?.toByteArray() ?: throw SerializationException("$field must be a byte string")

private fun CborElement.asUInt64(field: String): ULong = (this as? CborInteger)?.let {
    if (!it.isPositive) null else it.absoluteValue
} ?: throw SerializationException("$field must be an unsigned integer")

private fun CborElement.asTextFields(owner: String): Map<String, CborElement> {
    val map = this as? CborMap ?: throw SerializationException("$owner must be a CBOR map")
    return map.entries.associate { (key, value) ->
        ((key as? CborString)?.value ?: throw SerializationException("$owner keys must be text")) to value
    }
}

private fun Map<String, CborElement>.toTextMap(): CborMap = CborMap(
    entries.associate { (key, value) -> CborString(key) to value }
)

private fun Map<String, CborElement>.requiredText(name: String, owner: String): String =
    (this[name] as? CborString)?.value ?: throw SerializationException("$owner $name is required and must be text")

private fun Map<String, CborElement>.optionalText(name: String, owner: String): String? = this[name]?.let {
    (it as? CborString)?.value ?: throw SerializationException("$owner $name must be text")
}

private fun Map<String, CborElement>.requiredTDate(name: String, owner: String): Instant =
    this[name]?.asTDate("$owner $name") ?: throw SerializationException("$owner $name is required")

private fun Map<String, CborElement>.requiredBytes(name: String, owner: String): ByteArray =
    this[name]?.asBytes("$owner $name") ?: throw SerializationException("$owner $name is required")

private fun Map<String, CborElement>.requiredBoolean(name: String, owner: String): Boolean =
    (this[name] as? CborBoolean)?.value
        ?: throw SerializationException("$owner $name is required and must be a boolean")

private val RICAL_FIELDS = setOf(
    "version", "provider", "date", "nextUpdate", "notAfter", "certificateInfos", "id",
    "latestRicalUrl", "extensions", "type",
)

private val CERTIFICATE_INFO_FIELDS = setOf(
    "certificate", "serialNumber", "isTrustAnchor", "ski", "aki", "type", "trustContraints",
    "name", "issuingCountry", "stateOrProvinceName", "issuer", "subject", "notBefore", "notAfter",
    "extensions",
)
