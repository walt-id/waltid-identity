package id.walt.mdoc.credsdata

import id.walt.mdoc.credsdata.isoshared.IsoSexEnum
import id.walt.mdoc.credsdata.isoshared.IsoSexEnumSerializer
import id.walt.mdoc.encoding.ByteArrayBase64UrlSerializer
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString

/**
 * Data model for the Photo ID profile as per ISO/IEC TS 23220-4, Annex C.
 * Note: `ByteArray` properties like `portrait` will be automatically encoded as `bstr` by the central MdocCbor instance.
 */
// --- Data elements from ISO/IEC 23220-2 (Table 1) ---
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PhotoId(
    @SerialName("family_name_unicode") val familyNameUnicode: String,
    @SerialName("given_name_unicode") val givenNameUnicode: String,
    @SerialName("birth_date") val birthDate: LocalDate, // full-date

    @ByteString
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    @SerialName("portrait") val portrait: ByteArray,

    @SerialName("issue_date") val issueDate: LocalDate, // tdate or full-date
    @SerialName("expiry_date") val expiryDate: LocalDate, // tdate or full-date
    @SerialName("issuing_authority_unicode") val issuingAuthorityUnicode: String,
    @SerialName("issuing_country") val issuingCountry: String,
    @SerialName("age_in_years") val ageInYears: UInt? = null,
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

    @SerialName("age_birth_year") val ageBirthYear: UInt? = null,
    @SerialName("portrait_capture_date") val portraitCaptureDate: LocalDate? = null, // tdate
    @SerialName("birthplace") val birthPlace: String? = null,
    @SerialName("name_at_birth") val nameAtBirth: String? = null,
    @SerialName("resident_address_unicode") val residentAddressUnicode: String? = null,
    @SerialName("resident_city_unicode") val residentCityUnicode: String? = null,
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
    @SerialName("resident_country") val residentCountry: String? = null,
    @SerialName("resident_city_latin1") val residentCityLatin1: String? = null,
    @Serializable(with = IsoSexEnumSerializer::class)
    @SerialName("sex") val sex: IsoSexEnum? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("issuing_subdivision") val issuingSubdivision: String? = null,
    @SerialName("family_name_latin1") val familyNameLatin1: String,
    @SerialName("given_name_latin1") val givenNameLatin1: String,

    // --- Data elements from org.iso.23220.photoid.1 namespace (Table 2) ---
    @SerialName("person_id") val personId: String? = null,
    @SerialName("birth_country") val birthCountry: String? = null,
    @SerialName("birth_state") val birthState: String? = null,
    @SerialName("birth_city") val birthCity: String? = null,
    @SerialName("administrative_number") val administrativeNumber: String? = null,
    @SerialName("resident_street") val residentStreet: String? = null,
    @SerialName("resident_house_number") val residentHouseNumber: String? = null,
    @SerialName("travel_document_number") val travelDocumentNumber: String? = null,
    @SerialName("resident_state") val residentState: String? = null,
) : MdocData {

    companion object : MdocCompanion {
        override fun registerSerializationTypes() {
            val localDate = LocalDate.serializer()
            val byteArray = ByteArraySerializer()
            val uint = UInt.serializer()
            val boolean = Boolean.serializer()

            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to localDate,
                    "issue_date" to localDate,
                    "expiry_date" to localDate,
                    "portrait" to byteArray,
                    "sex" to IsoSexEnumSerializer,
                    "portrait_capture_date" to localDate,
                    "age_in_year" to uint,
                    "age_birth_year" to uint,
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

                ),
                "org.iso.23220.photoid.1"
            )

            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to localDate,
                    "issue_date" to localDate,
                    "expiry_date" to localDate,
                    "portrait" to byteArray,
                    "sex" to IsoSexEnumSerializer,
                    "portrait_capture_date" to localDate,
                    "age_in_year" to uint,
                    "age_birth_year" to uint,
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

                    ),
                "org.iso.23220.1"
            )
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoId) return false

        if (ageInYears != other.ageInYears) return false
        if (ageOver12 != other.ageOver12) return false
        if (ageOver13 != other.ageOver13) return false
        if (ageOver14 != other.ageOver14) return false
        if (ageOver16 != other.ageOver16) return false
        if (ageOver18 != other.ageOver18) return false
        if (ageOver21 != other.ageOver21) return false
        if (ageOver25 != other.ageOver25) return false
        if (ageOver60 != other.ageOver60) return false
        if (ageOver62 != other.ageOver62) return false
        if (ageOver65 != other.ageOver65) return false
        if (ageOver68 != other.ageOver68) return false
        if (ageBirthYear != other.ageBirthYear) return false
        if (sex != other.sex) return false
        if (familyNameUnicode != other.familyNameUnicode) return false
        if (givenNameUnicode != other.givenNameUnicode) return false
        if (birthDate != other.birthDate) return false
        if (!portrait.contentEquals(other.portrait)) return false
        if (issueDate != other.issueDate) return false
        if (expiryDate != other.expiryDate) return false
        if (issuingAuthorityUnicode != other.issuingAuthorityUnicode) return false
        if (issuingCountry != other.issuingCountry) return false
        if (portraitCaptureDate != other.portraitCaptureDate) return false
        if (birthPlace != other.birthPlace) return false
        if (nameAtBirth != other.nameAtBirth) return false
        if (residentAddressUnicode != other.residentAddressUnicode) return false
        if (residentCityUnicode != other.residentCityUnicode) return false
        if (residentPostalCode != other.residentPostalCode) return false
        if (residentCountry != other.residentCountry) return false
        if (residentCityLatin1 != other.residentCityLatin1) return false
        if (nationality != other.nationality) return false
        if (documentNumber != other.documentNumber) return false
        if (issuingSubdivision != other.issuingSubdivision) return false
        if (familyNameLatin1 != other.familyNameLatin1) return false
        if (givenNameLatin1 != other.givenNameLatin1) return false
        if (personId != other.personId) return false
        if (birthCountry != other.birthCountry) return false
        if (birthState != other.birthState) return false
        if (birthCity != other.birthCity) return false
        if (administrativeNumber != other.administrativeNumber) return false
        if (residentStreet != other.residentStreet) return false
        if (residentHouseNumber != other.residentHouseNumber) return false
        if (travelDocumentNumber != other.travelDocumentNumber) return false
        if (residentState != other.residentState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ageInYears?.hashCode() ?: 0
        result = 31 * result + (ageOver12?.hashCode() ?: 0)
        result = 31 * result + (ageOver13?.hashCode() ?: 0)
        result = 31 * result + (ageOver14?.hashCode() ?: 0)
        result = 31 * result + (ageOver16?.hashCode() ?: 0)
        result = 31 * result + (ageOver18?.hashCode() ?: 0)
        result = 31 * result + (ageOver21?.hashCode() ?: 0)
        result = 31 * result + (ageOver25?.hashCode() ?: 0)
        result = 31 * result + (ageOver60?.hashCode() ?: 0)
        result = 31 * result + (ageOver62?.hashCode() ?: 0)
        result = 31 * result + (ageOver65?.hashCode() ?: 0)
        result = 31 * result + (ageOver68?.hashCode() ?: 0)
        result = 31 * result + (ageBirthYear?.hashCode() ?: 0)
        result = 31 * result + (sex?.hashCode() ?: 0)
        result = 31 * result + familyNameUnicode.hashCode()
        result = 31 * result + givenNameUnicode.hashCode()
        result = 31 * result + birthDate.hashCode()
        result = 31 * result + portrait.contentHashCode()
        result = 31 * result + issueDate.hashCode()
        result = 31 * result + expiryDate.hashCode()
        result = 31 * result + issuingAuthorityUnicode.hashCode()
        result = 31 * result + issuingCountry.hashCode()
        result = 31 * result + (portraitCaptureDate?.hashCode() ?: 0)
        result = 31 * result + (birthPlace?.hashCode() ?: 0)
        result = 31 * result + (nameAtBirth?.hashCode() ?: 0)
        result = 31 * result + (residentAddressUnicode?.hashCode() ?: 0)
        result = 31 * result + (residentCityUnicode?.hashCode() ?: 0)
        result = 31 * result + (residentPostalCode?.hashCode() ?: 0)
        result = 31 * result + (residentCountry?.hashCode() ?: 0)
        result = 31 * result + (residentCityLatin1?.hashCode() ?: 0)
        result = 31 * result + (nationality?.hashCode() ?: 0)
        result = 31 * result + (documentNumber?.hashCode() ?: 0)
        result = 31 * result + (issuingSubdivision?.hashCode() ?: 0)
        result = 31 * result + familyNameLatin1.hashCode()
        result = 31 * result + givenNameLatin1.hashCode()
        result = 31 * result + (personId?.hashCode() ?: 0)
        result = 31 * result + (birthCountry?.hashCode() ?: 0)
        result = 31 * result + (birthState?.hashCode() ?: 0)
        result = 31 * result + (birthCity?.hashCode() ?: 0)
        result = 31 * result + (administrativeNumber?.hashCode() ?: 0)
        result = 31 * result + (residentStreet?.hashCode() ?: 0)
        result = 31 * result + (residentHouseNumber?.hashCode() ?: 0)
        result = 31 * result + (travelDocumentNumber?.hashCode() ?: 0)
        result = 31 * result + (residentState?.hashCode() ?: 0)
        return result
    }
}
