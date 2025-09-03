package id.walt.mdoc.credsdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for the Mobile Driving Licence (mDL) as per ISO/IEC 18013-5, Section 7.2.1, Table 5.
 * Note: `ByteArray` properties like `portrait` will be automatically encoded as `bstr` by the central MdocCbor instance.
 */
@Serializable
data class Mdl(
    @SerialName("family_name") val familyName: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("birth_date") val birthDate: String, // full-date
    @SerialName("issue_date") val issueDate: String, // tdate or full-date
    @SerialName("expiry_date") val expiryDate: String, // tdate or full-date
    @SerialName("issuing_country") val issuingCountry: String,
    @SerialName("issuing_authority") val issuingAuthority: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("portrait") val portrait: ByteArray? = null,
    @SerialName("driving_privileges") val drivingPrivileges: List<DrivingPrivilege>? = null,
    @SerialName("un_distinguishing_sign") val unDistinguishingSign: String? = null,
    @SerialName("administrative_number") val administrativeNumber: String? = null,
    @SerialName("sex") val sex: Long? = null, // 0=not known, 1=male, 2=female, 9=not applicable
    @SerialName("height") val height: Long? = null,
    @SerialName("weight") val weight: Long? = null,
    @SerialName("eye_colour") val eyeColour: String? = null,
    @SerialName("hair_colour") val hairColour: String? = null,
    @SerialName("birth_place") val birthPlace: String? = null,
    @SerialName("resident_address") val residentAddress: String? = null,
    @SerialName("portrait_capture_date") val portraitCaptureDate: String? = null, // tdate
    @SerialName("age_in_years") val ageInYears: Long? = null,
    @SerialName("age_birth_year") val ageBirthYear: Long? = null,
    @SerialName("age_over_NN") val ageOverNN: Boolean? = null, // Placeholder for various age_over_xx fields
    @SerialName("issuing_jurisdiction") val issuingJurisdiction: String? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("resident_city") val residentCity: String? = null,
    @SerialName("resident_state") val residentState: String? = null,
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
    @SerialName("resident_country") val residentCountry: String? = null,
    @SerialName("family_name_national_character") val familyNameNationalCharacter: String? = null,
    @SerialName("given_name_national_character") val givenNameNationalCharacter: String? = null,
    @SerialName("signature_usual_mark") val signatureUsualMark: ByteArray? = null,
) : MdocData

/**
 * Data model for driving privileges as per ISO/IEC 18013-5, Section 7.2.4.
 */
@Serializable
data class DrivingPrivilege(
    @SerialName("vehicle_category_code") val vehicleCategoryCode: String,
    @SerialName("issue_date") val issueDate: String? = null, // full-date
    @SerialName("expiry_date") val expiryDate: String? = null, // full-date
    val codes: List<DrivingPrivilegeCode>? = null,
)

@Serializable
data class DrivingPrivilegeCode(
    val code: String,
    val sign: String? = null,
    val value: String? = null,
)
