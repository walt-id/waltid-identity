package id.walt.mdoc.credsdata


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for the Photo ID profile as per ISO/IEC TS 23220-4, Annex C.
 * Note: `ByteArray` properties like `portrait` will be automatically encoded as `bstr` by the central MdocCbor instance.
 */
@Serializable
data class PhotoId(
    // --- Data elements from ISO/IEC 23220-2 (Table 1) ---
    @SerialName("family_name") val familyName: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("birth_date") val birthDate: String, // full-date
    @SerialName("issue_date") val issueDate: String, // tdate or full-date
    @SerialName("expiry_date") val expiryDate: String, // tdate or full-date
    @SerialName("issuing_authority") val issuingAuthority: String,
    @SerialName("issuing_country") val issuingCountry: String,
    @SerialName("portrait") val portrait: ByteArray? = null,
    @SerialName("age_over_18") val ageOver18: Boolean? = null,
    @SerialName("age_in_years") val ageInYears: Long? = null,
    @SerialName("age_over_NN") val ageOverNN: Boolean? = null, // Placeholder
    @SerialName("age_birth_year") val ageBirthYear: Long? = null,
    @SerialName("portrait_capture_date") val portraitCaptureDate: String? = null, // tdate
    @SerialName("birth_place") val birthPlace: String? = null,
    @SerialName("name_at_birth") val nameAtBirth: String? = null,
    @SerialName("resident_address") val residentAddress: String? = null,
    @SerialName("resident_city") val residentCity: String? = null,
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
    @SerialName("resident_country") val residentCountry: String? = null,
    @SerialName("sex") val sex: Long? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("issuing_subdivision") val issuingSubdivision: String? = null,

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
) : MdocData
