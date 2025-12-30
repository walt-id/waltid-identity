@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import id.walt.mdoc.credsdata.isoshared.IsoSexEnum
import id.walt.mdoc.credsdata.isoshared.IsoSexEnumSerializer
import id.walt.mdoc.encoding.ByteArrayBase64UrlSerializer
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Data model for the Mobile Driving Licence (mDL) as per ISO/IEC 18013-5, Section 7.2.1, Table 5.
 * Note: `ByteArray` properties like `portrait` will be automatically encoded as `bstr` by the central MdocCbor instance.
 */
@Serializable
data class Mdl(
    @SerialName("family_name") val familyName: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("birth_date") val birthDate: LocalDate? = null, // full-date
    @SerialName("issue_date") val issueDate: LocalDate, // tdate or full-date
    @SerialName("expiry_date") val expiryDate: LocalDate, // tdate or full-date
    @SerialName("issuing_country") val issuingCountry: String? = null,
    @SerialName("issuing_authority") val issuingAuthority: String? = null,
    @SerialName("document_number") val documentNumber: String,
    @ByteString
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    @SerialName("portrait") val portrait: ByteArray,
    @SerialName("driving_privileges") val drivingPrivileges: List<DrivingPrivilege>,
    @SerialName("un_distinguishing_sign") val unDistinguishingSign: String? = null,
    @SerialName("administrative_number") val administrativeNumber: String? = null,
    @Serializable(with = IsoSexEnumSerializer::class)
    @SerialName("sex") val sex: IsoSexEnum? = null, // 0=not known, 1=male, 2=female, 9=not applicable
    @SerialName("height") val height: UInt? = null,
    @SerialName("weight") val weight: UInt? = null,
    @SerialName("eye_colour") val eyeColour: String? = null,
    @SerialName("hair_colour") val hairColour: String? = null,
    @SerialName("birth_place") val birthPlace: String? = null,
    @SerialName("resident_address") val residentAddress: String? = null,
    @SerialName("portrait_capture_date") val portraitCaptureDate: LocalDate? = null, // tdate
    @SerialName("age_in_years") val ageInYears: UInt? = null,
    @SerialName("age_birth_year") val ageBirthYear: UInt? = null,

    /** Age attestation: Over 12 years old? */
    @SerialName("age_over_12")
    val ageOver12: Boolean? = null,

    /** Age attestation: Over 13 years old? */
    @SerialName("age_over_13")
    val ageOver13: Boolean? = null,

    /** Age attestation: Over 14 years old? */
    @SerialName("age_over_14")
    val ageOver14: Boolean? = null,

    /** Age attestation: Over 16 years old? */
    @SerialName("age_over_16")
    val ageOver16: Boolean? = null,

    /** Age attestation: Over 18 years old? */
    @SerialName("age_over_18")
    val ageOver18: Boolean? = null,

    /** Age attestation: Over 21 years old? */
    @SerialName("age_over_21")
    val ageOver21: Boolean? = null,

    /** Age attestation: Over 25 years old? */
    @SerialName("age_over_25")
    val ageOver25: Boolean? = null,

    /** Age attestation: Over 60 years old? */
    @SerialName("age_over_60")
    val ageOver60: Boolean? = null,

    /** Age attestation: Over 62 years old? */
    @SerialName("age_over_62")
    val ageOver62: Boolean? = null,

    /** Age attestation: Over 65 years old? */
    @SerialName("age_over_65")
    val ageOver65: Boolean? = null,

    /** Age attestation: Over 68 years old? */
    @SerialName("age_over_68")
    val ageOver68: Boolean? = null,


    @SerialName("issuing_jurisdiction") val issuingJurisdiction: String? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("resident_city") val residentCity: String? = null,
    @SerialName("resident_state") val residentState: String? = null,
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
    @SerialName("resident_country") val residentCountry: String? = null,

    /** This element contains optional facial information of the mDL holder. */
    @ByteString
    @SerialName("biometric_template_face")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val biometricTemplateFace: ByteArray? = null,

    /** This element contains optional fingerprint information of the mDL holder. */
    @ByteString
    @SerialName("biometric_template_finger")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val biometricTemplateFinger: ByteArray? = null,

    /** This element contains optional signature/sign information of the mDL holder. */
    @ByteString
    @SerialName("biometric_template_signature_sign")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val biometricTemplateSignatureSign: ByteArray? = null,

    /** This element contains optional iris information of the mDL holder. */
    @ByteString
    @SerialName("biometric_template_iris")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val biometricTemplateIris: ByteArray? = null,

    @SerialName("family_name_national_character") val familyNameNationalCharacter: String? = null,
    @SerialName("given_name_national_character") val givenNameNationalCharacter: String? = null,
    /** Image of the signature or usual mark of the mDL holder. */
    @ByteString
    @SerialName("signature_usual_mark")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val signatureOrUsualMark: ByteArray? = null
) : MdocData {
    companion object : MdocCompanion {
        override fun registerSerializationTypes() {
            val localDate = LocalDate.serializer()
            val uint = UInt.serializer()
            val boolean = Boolean.serializer()

            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to localDate,
                    "issue_date" to localDate,
                    "expiry_date" to localDate,
                    "portrait" to ByteArraySerializer(),
                    "driving_privileges" to ListSerializer(DrivingPrivilege.serializer()),
                    "sex" to IsoSexEnumSerializer,
                    "height" to uint,
                    "weight" to uint,
                    "portrait_capture_date" to localDate,
                    "age_in_year" to uint,
                    "age_birth_year" to uint,
                    "signature_usual_mark" to ByteArraySerializer(),
                    "age_over_12" to boolean,
                    "age_over_13" to boolean,
                    "age_over_14" to boolean,
                    "age_over_16" to boolean,
                    "age_over_18" to boolean,
                    "age_over_21" to boolean,
                    "age_over_25" to boolean,
                    "age_over_60" to boolean,
                    "age_over_62" to boolean,
                    "age_over_65" to boolean,
                    "age_over_68" to boolean,
                    "biometric_template_face" to ByteArraySerializer(),
                    "biometric_template_finger" to ByteArraySerializer(),
                    "biometric_template_signature_sign" to ByteArraySerializer(),
                    "biometric_template_iris" to ByteArraySerializer()
                ),
                "org.iso.18013.5.1" // The namespace for mDL
            )

            // Fallback for issues in mdocs1:
            MdocsCborSerializer.registerFallbackDecoder(
                mapOf(
                    // Portrait is encoded as array of 15 items instead of bytestring
                    "portrait" to ListSerializer(Byte.serializer()),
                    // Dates in DrivingPrivilege are CBOR-tagged 1004 ("full-date"), but it's just a text string
                    "driving_privileges" to ListSerializer(DrivingPrivilege.FallbackDrivingPrivilege.serializer()),
                ), "org.iso.18013.5.1"
            )
        }
    }
}

@Serializable
data class MobileDrivingLicenceJwsNamespace(
    @SerialName("org.iso.18013.5.1")
    val mdl: Mdl,
)

@OptIn(ExperimentalTime::class)
@Serializable
data class MobileDrivingLicenceJws(
    @SerialName("doctype")
    val doctype: String,
    @SerialName("namespaces")
    val namespaces: MobileDrivingLicenceJwsNamespace,
    @SerialName("iat")
    @Serializable(with = InstantLongSerializer::class)
    val issuedAt: Instant,
    @SerialName("exp")
    @Serializable(with = NullableInstantLongSerializer::class)
    val expiration: Instant?,
)

@OptIn(ExperimentalTime::class)
class NullableInstantLongSerializer : KSerializer<Instant?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableInstantLongSerializer", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant? =
        runCatching { Instant.fromEpochSeconds(decoder.decodeLong()) }.getOrNull()

    override fun serialize(encoder: Encoder, value: Instant?) {
        value?.let { encoder.encodeLong(it.epochSeconds) }
    }

}

@OptIn(ExperimentalTime::class)
object InstantLongSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantLongSerializer", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.fromEpochSeconds(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.epochSeconds)
    }

}

/**
 * Data model for driving privileges as per ISO/IEC 18013-5, Section 7.2.4.
 */
@Serializable
data class DrivingPrivilege(
    @SerialName("vehicle_category_code") val vehicleCategoryCode: String,
    @ValueTags(1004u)
    @SerialName("issue_date")
    val issueDate: LocalDate? = null, // full-date
    @ValueTags(1004u)
    @SerialName("expiry_date")
    val expiryDate: LocalDate? = null,// full-date
    val codes: List<DrivingPrivilegeCode>? = null,
) {
    @Serializable
    data class FallbackDrivingPrivilege(
        @SerialName("vehicle_category_code") val vehicleCategoryCode: String,
        @SerialName("issue_date")
        val issueDate: LocalDate? = null, // full-date
        @SerialName("expiry_date")
        val expiryDate: LocalDate? = null,// full-date
        val codes: List<DrivingPrivilegeCode>? = null,
    )
}

@Serializable
data class DrivingPrivilegeCode(
    val code: String,
    val sign: String? = null,
    val value: String? = null,
)
