@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

/**
 * https://github.com/eu-digital-identity-wallet/eudi-doc-attestation-rulebooks-catalog/blob/main/rulebooks/pid/pid-rulebook.md
 */
@Serializable
@SerialName("EuPid2023")
data class Pid(
    // Mandatory attributes specified in CIR 2024/2977:

    /** Current last name(s) or surname(s) of the user to whom the person identification data relates. */
    @SerialName("family_name")
    val familyName: String,

    /** Current first name(s), including middle name(s) where applicable, of the user to whom the person identification data relates. */
    @SerialName("given_name")
    val givenName: String,

    /** Day, month, and year on which the user to whom the person identification data relates was born. */
    @SerialName("birth_date")
    val birthDate: LocalDate,

    /** The country as an alpha-2 country code as specified in ISO 3166-1, or the state, province, district, or local area or the municipality, city, town, or village where the user to whom the person identification data relates was born. */
    @SerialName("birth_place")
    val birthPlace: String? = null, // Could also be a data class?

    /** One or more alpha-2 country codes as specified in ISO 3166-1, representing the nationality of the user to whom the person identification data relates. */
    @SerialName("nationality")
    val nationality: List<String>? = null,

    // Optional attributes specified in CIR 2024/2977:

    /** The full address of the place where the user to whom the person identification data relates currently resides or can be contacted (street name, house number, city etc.). */
    @SerialName("resident_address")
    val residentAddress: String? = null,

    /** The country where the user to whom the person identification data relates currently resides, as an alpha-2 country code as specified in ISO 3166-1. */
    @SerialName("resident_country")
    val residentCountry: String? = null,

    /** The state, province, district, or local area where the user to whom the person identification data relates currently resides. */
    @SerialName("resident_state")
    val residentState: String? = null,

    /** The municipality, city, town, or village where the user to whom the person identification data relates currently resides. */
    @SerialName("resident_city")
    val residentCity: String? = null,

    /** The postal code of the place where the user to whom the person identification data relates currently resides. */
    @SerialName("resident_postal_code")
    val residentPostalCode: String? = null,

    /** The name of the street where the user to whom the person identification data relates currently resides. */
    @SerialName("resident_street")
    val residentStreet: String? = null,

    /** The house number where the user to whom the person identification data relates currently resides, including any affix or suffix. */
    @SerialName("resident_house_number")
    val residentHouseNumber: String? = null,

    /**
     * A value assigned to the natural person that is unique among all personal administrative numbers issued by the provider of person identification data. Where Member States opt to include this attribute, they shall describe in their electronic identification schemes under which the person identification data is issued, the policy that they apply to the values of this attribute, including, where applicable, specific conditions for the processing of this value.
     */
    @SerialName("personal_administrative_number")
    val personalAdministrativeNumber: String? = null,

    /** Facial image of the wallet user compliant with ISO 19794-5 or ISO 39794 specifications. Further clarification added in this PID Rulebook: The detailed format of the portrait is specified in requirement PID_03 in Annex 2, Topic 3. */
    @SerialName("portrait")
    @ByteString
    val portrait: ByteArray? = null,

    /** Last name(s) or surname(s) of the User to whom the person identification data relates at the time of birth. */
    @SerialName("family_name_birth")
    val familyNameBirth: String? = null,

    /** First name(s), including middle name(s), of the User to whom the person identification data relates at the time of birth. */
    @SerialName("given_name_birth")
    val givenNameBirth: String? = null,

    /** Values shall be one of the following: 0 = not known; 1 = male; 2 = female; 3 = other; 4 = inter; 5 = diverse; 6 = open; 9 = not applicable. For values 0, 1, 2 and 9, ISO/IEC 5218 applies. */
    @SerialName("sex")
    val sex: UInt? = null,

    /** Electronic mail address of the user to whom the person identification data relates, in conformance with [RFC 5322]. */
    @SerialName("email_address")
    val emailAddress: String? = null,

    /** Mobile telephone number of the User to whom the person identification data relates, starting with the '+' symbol as the international code prefix and the country code, followed by numbers only. */
    @SerialName("mobile_phone_number")
    val mobilePhoneNumber: String? = null,

    // Mandatory metadata specified in CIR 2024/2977:


    /** Date (and if possible time) when the person identification data will expire. Further clarification added in this PID Rulebook: This attribute, as well as the optional issuance_date attribute specified in Section 2.6, pertains to the administrative validity period of the PID. It is up to the PID Provider to decide whether a PID has an administrative validity period. However, if present, it in general is different from the technical validity period of a PID. The technical validity period is a mandatory element of all PIDs (and also attestations) in the EUDI Wallet ecosystem. It typically is short, a few days or weeks at most, if not shorter, to mitigate challenges regarding tracking of Users by malicious Relying Parties based on the repeated presentation of the same PID. On the other hand, the administrative validity period is typically at least a few years long. During the administrative validity period of a PID, the PID Provider will therefore provide multiple successive PIDs to a User, typically without any actions being expected from the User. However, when the administrative validity period of a PID ends, typically the User has to apply for an entirely new PID. */
    @SerialName("expiry_date")
    val expiryDate: LocalDate,

    /**
     * Name of the administrative authority that issued the person identification data, or the ISO 3166 alpha-2 country code of the respective Member State if there is no separate authority entitled to issue person identification data.
     */
    @SerialName("issuing_authority")
    val issuingAuthority: String,

    /** Alpha-2 country code, as specified in ISO 3166-1, of the country or territory of the provider of the person identification data. */
    @SerialName("issuing_country")
    val issuingCountry: String,

    // Optional metadata specified in CIR 2024/2977:

    /** A number for the PID, assigned by the PID Provider. */
    @SerialName("document_number")
    val documentNumber: String? = null,

    /**
     * Country subdivision code of the jurisdiction that issued the person identification data, as specified in ISO 3166-2:2020, Clause 8. The first part of the code shall be the same as the value for the issuing country.
     */
    @SerialName("issuing_jurisdiction")
    val issuingJurisdiction: String? = null,

    /** The location of validity status information on the person identification data where the providers of person identification data revoke person identification data. */
    @SerialName("location_status")
    val locationStatus: String? = null,
    /* For ISO/IEC 18013-5-compliant PIDs, the attribute location_status is absent, since the PID issuer will add revocation information, if needed, to the MSO as specified in [ISO/IEC 18013-5]. */

    // Additional optional attributes specified in this Rulebook:

    /** Date (and if possible time) when the person identification data was issued and/or the administrative validity period of the person identification data began. */
    @SerialName("issuance_date")
    val issuanceDate: LocalDate,

    /** This attribute indicates at least the URL at which a machine-readable version of the trust anchor to be used for verifying the PID can be found or looked up */
    @SerialName("trust_anchor")
    val trustAnchor: String? = null,

    ) : MdocData {
    enum class PidSex(val code: UInt) {
        NOT_KNOWN(0u),
        MALE(1u),
        FEMALE(2u),
        OTHER(3u),
        INTER(4u),
        DIVERSE(5u),
        OPEN(6u),
        NOT_APPLICABLE(9u)
    }

    companion object : MdocCompanion {
        override fun registerSerializationTypes() {
            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to LocalDate.serializer(),
                    "issuance_date" to LocalDate.serializer(),
                    "issuance_date" to LocalDate.serializer(),
                ), "eu.europa.ec.eudi.pid.1"
            )
        }

    }
}
