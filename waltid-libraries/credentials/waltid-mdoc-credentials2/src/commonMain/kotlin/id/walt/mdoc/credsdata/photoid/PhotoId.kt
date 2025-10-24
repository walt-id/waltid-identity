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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.ByteString

/**
 * Birth date structure as per ISO/IEC 23220-2
 */
@Serializable
data class BirthDate(
    @SerialName("birth_date") val birthDate: LocalDate, // YYYY-MM-DD format
    @SerialName("approximate_mask") val approximateMask: String? = null // 8-digit 0/1 mask
) {
    init {
        // Validate approximate_mask format if provided
        approximateMask?.let { mask ->
            require(mask.matches(Regex("[01]{8}"))) { 
                "approximate_mask must be an 8-digit binary string (0s and 1s only)" 
            }
        }
    }
}

/**
 * Core data elements from org.iso.23220.1 namespace as per ISO/IEC 23220-2 Table C.1
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PhotoIdCore(
    // Required fields
    @SerialName("family_name") val familyName: String,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name_viz") val familyNameViz: String? = null,
    @SerialName("given_name_viz") val givenNameViz: String? = null,
    @SerialName("family_name_latin1") val familyNameLatin1: String? = null,
    @SerialName("given_name_latin1") val givenNameLatin1: String? = null,
    @SerialName("birth_date") val birthDate: BirthDate,
    
    @ByteString
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    @SerialName("portrait") val portrait: ByteArray,
    
    @ByteString
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    @SerialName("enrolment_portrait_image") val enrolmentPortraitImage: ByteArray? = null,
    
    @SerialName("issue_date") val issueDate: LocalDate, // YYYY-MM-DD
    @SerialName("expiry_date") val expiryDate: LocalDate, // YYYY-MM-DD
    @SerialName("issuing_authority") val issuingAuthority: String,
    @SerialName("issuing_country") val issuingCountry: String,
    @SerialName("issuing_subdivision") val issuingSubdivision: String? = null,
    @SerialName("age_over_18") val ageOver18: Boolean,
    
    // Optional fields
    @SerialName("age_in_years") val ageInYears: UInt? = null,
    @SerialName("age_over_NN") val ageOverNN: Boolean? = null,
    @SerialName("age_birth_year") val ageBirthYear: UInt? = null,
    @SerialName("portrait_capture_date") val portraitCaptureDate: LocalDate? = null,
    @SerialName("birthplace") val birthplace: String? = null,
    @SerialName("name_at_birth") val nameAtBirth: String? = null,
    @SerialName("resident_address") val residentAddress: String? = null,
    @SerialName("resident_city") val residentCity: String? = null,
    @SerialName("resident_city_latin1") val residentCityLatin1: String? = null,
    @SerialName("resident_postal_code") val residentPostalCode: String? = null,
    @SerialName("resident_country") val residentCountry: String? = null,
    @Serializable(with = IsoSexEnumSerializer::class)
    @SerialName("sex") val sex: IsoSexEnum? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("document_number") val documentNumber: String? = null
) : MdocData {
    init {
        // Validate string length constraints as per schema
        require(familyName.length <= 150) { "family_name must be <= 150 characters" }
        givenName?.let { require(it.length <= 150) { "given_name must be <= 150 characters" } }
        familyNameViz?.let { require(it.length <= 150) { "family_name_viz must be <= 150 characters" } }
        givenNameViz?.let { require(it.length <= 150) { "given_name_viz must be <= 150 characters" } }
        familyNameLatin1?.let { require(it.length <= 150) { "family_name_latin1 must be <= 150 characters" } }
        givenNameLatin1?.let { require(it.length <= 150) { "given_name_latin1 must be <= 150 characters" } }
        issuingAuthority.let { require(it.length <= 150) { "issuing_authority must be <= 150 characters" } }
        issuingSubdivision?.let { require(it.length <= 150) { "issuing_subdivision must be <= 150 characters" } }
        birthplace?.let { require(it.length <= 150) { "birthplace must be <= 150 characters" } }
        nameAtBirth?.let { require(it.length <= 150) { "name_at_birth must be <= 150 characters" } }
        residentAddress?.let { require(it.length <= 150) { "resident_address must be <= 150 characters" } }
        residentCity?.let { require(it.length <= 150) { "resident_city must be <= 150 characters" } }
        residentCityLatin1?.let { require(it.length <= 150) { "resident_city_latin1 must be <= 150 characters" } }
        residentPostalCode?.let { require(it.length <= 150) { "resident_postal_code must be <= 150 characters" } }
        documentNumber?.let { require(it.length <= 150) { "document_number must be <= 150 characters" } }
        
        // Validate country code formats
        require(issuingCountry.matches(Regex("[A-Z]{2,3}"))) { 
            "issuing_country must be 2-3 uppercase letters (ISO 3166-1)" 
        }
        residentCountry?.let { 
            require(it.matches(Regex("[A-Z]{2}"))) { 
                "resident_country must be 2 uppercase letters (ISO 3166-1 alpha-2)" 
            } 
        }
        nationality?.let { 
            require(it.matches(Regex("[A-Z]{2,3}"))) { 
                "nationality must be 2-3 uppercase letters (ISO 3166-1)" 
            } 
        }
        
        // Validate age constraints
        ageInYears?.let { require(it >= 0u) { "age_in_years must be >= 0" } }
        ageBirthYear?.let { require(it >= 0u) { "age_birth_year must be >= 0" } }
        
        // Validate date constraints
        require(issueDate <= expiryDate) { "issue_date must be <= expiry_date" }
    }
}

/**
 * Additional Photo ID data from org.iso.23220.photoID.1 namespace as per ISO/IEC 23220-4 Table C.2
 */
@Serializable
data class PhotoIdAdditional(
    @SerialName("person_id") val personId: String? = null,
    @SerialName("birth_country") val birthCountry: String? = null,
    @SerialName("birth_state") val birthState: String? = null,
    @SerialName("birth_city") val birthCity: String? = null,
    @SerialName("administrative_number") val administrativeNumber: String? = null,
    @SerialName("resident_street") val residentStreet: String? = null,
    @SerialName("resident_house_number") val residentHouseNumber: String? = null,
    @SerialName("resident_state") val residentState: String? = null,
    @SerialName("travel_document_type") val travelDocumentType: String? = null,
    @SerialName("travel_document_number") val travelDocumentNumber: String? = null,
    @SerialName("travel_document_mrz") val travelDocumentMrz: String? = null
) : MdocData {
    init {
        // Validate string length constraints as per schema
        personId?.let { require(it.length <= 150) { "person_id must be <= 150 characters" } }
        birthState?.let { require(it.length <= 150) { "birth_state must be <= 150 characters" } }
        birthCity?.let { require(it.length <= 150) { "birth_city must be <= 150 characters" } }
        administrativeNumber?.let { require(it.length <= 150) { "administrative_number must be <= 150 characters" } }
        residentStreet?.let { require(it.length <= 150) { "resident_street must be <= 150 characters" } }
        residentHouseNumber?.let { require(it.length <= 150) { "resident_house_number must be <= 150 characters" } }
        residentState?.let { require(it.length <= 150) { "resident_state must be <= 150 characters" } }
        travelDocumentType?.let { require(it.length <= 150) { "travel_document_type must be <= 150 characters" } }
        travelDocumentNumber?.let { require(it.length <= 150) { "travel_document_number must be <= 150 characters" } }
        travelDocumentMrz?.let { require(it.length <= 150) { "travel_document_mrz must be <= 150 characters" } }
        
        // Validate country code format
        birthCountry?.let { 
            require(it.matches(Regex("[A-Z]{2}"))) { 
                "birth_country must be 2 uppercase letters (ISO 3166-1 alpha-2)" 
            } 
        }
    }
}

/**
 * ICAO 9303 data groups from org.iso.23220.datagroups.1 namespace as per ISO/IEC 23220-4 Table C.3
 */
@Serializable
data class PhotoIdDataGroups(
    @SerialName("version") val version: String? = null,
    @SerialName("dg1") val dg1: String? = null, // base64
    @SerialName("dg2") val dg2: String? = null, // base64
    @SerialName("dg3") val dg3: String? = null, // base64
    @SerialName("dg4") val dg4: String? = null, // base64
    @SerialName("dg5") val dg5: String? = null, // base64
    @SerialName("dg6") val dg6: String? = null, // base64
    @SerialName("dg7") val dg7: String? = null, // base64
    @SerialName("dg8") val dg8: String? = null, // base64
    @SerialName("dg9") val dg9: String? = null, // base64
    @SerialName("dg10") val dg10: String? = null, // base64
    @SerialName("dg11") val dg11: String? = null, // base64
    @SerialName("dg12") val dg12: String? = null, // base64
    @SerialName("dg13") val dg13: String? = null, // base64
    @SerialName("dg14") val dg14: String? = null, // base64
    @SerialName("dg15") val dg15: String? = null, // base64
    @SerialName("dg16") val dg16: String? = null, // base64
    @SerialName("sod") val sod: String? = null // base64
) : MdocData

/**
 * Complete Photo ID mdoc structure as per ISO/IEC 23220-4
 * This represents the full document structure with all three namespaces
 */
@Serializable
data class PhotoId(
    @SerialName("docType") val docType: String = "org.iso.23220.photoID.1",
    @SerialName("issuerSigned") val issuerSigned: PhotoIdIssuerSigned,
    @SerialName("deviceSigned") val deviceSigned: Map<String, Any>? = null,
    @SerialName("readerAuth") val readerAuth: String? = null // base64 COSE_Sign1
) : MdocData {
    init {
        // Validate document type
        require(docType == "org.iso.23220.photoID.1") { 
            "docType must be 'org.iso.23220.photoID.1' for Photo ID mdoc" 
        }
    }
}

/**
 * Issuer signed portion of Photo ID mdoc
 */
@Serializable
data class PhotoIdIssuerSigned(
    @SerialName("nameSpaces") val nameSpaces: PhotoIdNameSpaces,
    @SerialName("issuerAuth") val issuerAuth: String, // base64 COSE_Sign1
    @SerialName("digestAlgorithm") val digestAlgorithm: String? = null
)

/**
 * Namespaces container for Photo ID mdoc
 */
@Serializable
data class PhotoIdNameSpaces(
    @SerialName("org.iso.23220.1") val core: PhotoIdCore,
    @SerialName("org.iso.23220.photoID.1") val additional: PhotoIdAdditional,
    @SerialName("org.iso.23220.datagroups.1") val dataGroups: PhotoIdDataGroups? = null
)

    companion object : MdocCompanion {
        override fun registerSerializationTypes() {
            val localDate = LocalDate.serializer()
            val byteArray = ByteArraySerializer()
            val uint = UInt.serializer()
            val boolean = Boolean.serializer()
            val string = String.serializer()

            // Register serializers for org.iso.23220.1 namespace
            MdocsCborSerializer.register(
                mapOf(
                    "birth_date" to BirthDate.serializer(),
                    "issue_date" to localDate,
                    "expiry_date" to localDate,
                    "portrait" to byteArray,
                    "enrolment_portrait_image" to byteArray,
                    "sex" to IsoSexEnumSerializer,
                    "portrait_capture_date" to localDate,
                    "age_in_years" to uint,
                    "age_birth_year" to uint,
                    "age_over_18" to boolean,
                    "age_over_NN" to boolean
                ),
                "org.iso.23220.1"
            )
            
            // Register serializers for org.iso.23220.photoID.1 namespace
            MdocsCborSerializer.register(
                mapOf(
                    "person_id" to string,
                    "birth_country" to string,
                    "birth_state" to string,
                    "birth_city" to string,
                    "administrative_number" to string,
                    "resident_street" to string,
                    "resident_house_number" to string,
                    "resident_state" to string,
                    "travel_document_type" to string,
                    "travel_document_number" to string,
                    "travel_document_mrz" to string
                ),
                "org.iso.23220.photoID.1"
            )
            
            // Register serializers for org.iso.23220.datagroups.1 namespace
            MdocsCborSerializer.register(
                mapOf(
                    "version" to string,
                    "dg1" to string,
                    "dg2" to string,
                    "dg3" to string,
                    "dg4" to string,
                    "dg5" to string,
                    "dg6" to string,
                    "dg7" to string,
                    "dg8" to string,
                    "dg9" to string,
                    "dg10" to string,
                    "dg11" to string,
                    "dg12" to string,
                    "dg13" to string,
                    "dg14" to string,
                    "dg15" to string,
                    "dg16" to string,
                    "sod" to string
                ),
                "org.iso.23220.datagroups.1"
            )
        }
    }

}
