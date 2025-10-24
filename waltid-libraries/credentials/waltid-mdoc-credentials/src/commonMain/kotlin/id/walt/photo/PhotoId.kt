package id.walt.photo

import cbor.Cbor
import id.walt.mdoc.dataelement.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

//TODO: Add handling for age_over_NN where multiple such thingies can be supported, not just static ones...
//TODO: Add handling for biometric_template_XX
//TODO: Add handling of other optional fields, e.g., height, weight, hair and eye color etc.

/**
 * Data model for the Photo ID profile as per ISO/IEC 23220-4, Annex C.
 * This follows the same pattern as MobileDrivingLicence in the existing library.
 */
@Serializable(with = PhotoIdSerializer::class)
data class PhotoId(
    // Required fields as per ISO/IEC 23220-2 Table C.1
    val familyName: String,
    val givenName: String? = null,
    val familyNameViz: String? = null,
    val givenNameViz: String? = null,
    val familyNameLatin1: String? = null,
    val givenNameLatin1: String? = null,
    val birthDate: BirthDate,
    val portrait: ByteArray,
    val enrolmentPortraitImage: ByteArray? = null,
    val issueDate: LocalDate,
    val expiryDate: LocalDate,
    val issuingAuthority: String,
    val issuingCountry: String,
    val issuingSubdivision: String? = null,
    val ageOver18: Boolean,
    
    // Optional fields
    val ageInYears: UInt? = null,
    val ageOverNN: Boolean? = null,
    val ageBirthYear: UInt? = null,
    val portraitCaptureDate: LocalDate? = null,
    val birthplace: String? = null,
    val nameAtBirth: String? = null,
    val residentAddress: String? = null,
    val residentCity: String? = null,
    val residentCityLatin1: String? = null,
    val residentPostalCode: String? = null,
    val residentCountry: String? = null,
    val sex: IsoSexEnum? = null,
    val nationality: String? = null,
    val documentNumber: String? = null,
    
    // Additional Photo ID fields from org.iso.23220.photoID.1 namespace
    val personId: String? = null,
    val birthCountry: String? = null,
    val birthState: String? = null,
    val birthCity: String? = null,
    val administrativeNumber: String? = null,
    val residentStreet: String? = null,
    val residentHouseNumber: String? = null,
    val residentState: String? = null,
    val travelDocumentType: String? = null,
    val travelDocumentNumber: String? = null,
    val travelDocumentMrz: String? = null
) {
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
        birthCountry?.let { 
            require(it.matches(Regex("[A-Z]{2}"))) { 
                "birth_country must be 2 uppercase letters (ISO 3166-1 alpha-2)" 
            } 
        }
        
        // Validate age constraints
        ageInYears?.let { require(it >= 0u) { "age_in_years must be >= 0" } }
        ageBirthYear?.let { require(it >= 0u) { "age_birth_year must be >= 0" } }
        
        // Validate date constraints
        require(issueDate <= expiryDate) { "issue_date must be <= expiry_date" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PhotoId

        if (familyName != other.familyName) return false
        if (givenName != other.givenName) return false
        if (familyNameViz != other.familyNameViz) return false
        if (givenNameViz != other.givenNameViz) return false
        if (familyNameLatin1 != other.familyNameLatin1) return false
        if (givenNameLatin1 != other.givenNameLatin1) return false
        if (birthDate != other.birthDate) return false
        if (!portrait.contentEquals(other.portrait)) return false
        if (enrolmentPortraitImage != null) {
            if (other.enrolmentPortraitImage == null || !enrolmentPortraitImage.contentEquals(other.enrolmentPortraitImage)) return false
        } else if (other.enrolmentPortraitImage != null) return false
        if (issueDate != other.issueDate) return false
        if (expiryDate != other.expiryDate) return false
        if (issuingAuthority != other.issuingAuthority) return false
        if (issuingCountry != other.issuingCountry) return false
        if (issuingSubdivision != other.issuingSubdivision) return false
        if (ageOver18 != other.ageOver18) return false
        if (ageInYears != other.ageInYears) return false
        if (ageOverNN != other.ageOverNN) return false
        if (ageBirthYear != other.ageBirthYear) return false
        if (portraitCaptureDate != other.portraitCaptureDate) return false
        if (birthplace != other.birthplace) return false
        if (nameAtBirth != other.nameAtBirth) return false
        if (residentAddress != other.residentAddress) return false
        if (residentCity != other.residentCity) return false
        if (residentCityLatin1 != other.residentCityLatin1) return false
        if (residentPostalCode != other.residentPostalCode) return false
        if (residentCountry != other.residentCountry) return false
        if (sex != other.sex) return false
        if (nationality != other.nationality) return false
        if (documentNumber != other.documentNumber) return false
        if (personId != other.personId) return false
        if (birthCountry != other.birthCountry) return false
        if (birthState != other.birthState) return false
        if (birthCity != other.birthCity) return false
        if (administrativeNumber != other.administrativeNumber) return false
        if (residentStreet != other.residentStreet) return false
        if (residentHouseNumber != other.residentHouseNumber) return false
        if (residentState != other.residentState) return false
        if (travelDocumentType != other.travelDocumentType) return false
        if (travelDocumentNumber != other.travelDocumentNumber) return false
        if (travelDocumentMrz != other.travelDocumentMrz) return false

        return true
    }

    override fun hashCode(): Int {
        var result = familyName.hashCode()
        result = 31 * result + (givenName?.hashCode() ?: 0)
        result = 31 * result + (familyNameViz?.hashCode() ?: 0)
        result = 31 * result + (givenNameViz?.hashCode() ?: 0)
        result = 31 * result + (familyNameLatin1?.hashCode() ?: 0)
        result = 31 * result + (givenNameLatin1?.hashCode() ?: 0)
        result = 31 * result + birthDate.hashCode()
        result = 31 * result + portrait.contentHashCode()
        result = 31 * result + (enrolmentPortraitImage?.contentHashCode() ?: 0)
        result = 31 * result + issueDate.hashCode()
        result = 31 * result + expiryDate.hashCode()
        result = 31 * result + issuingAuthority.hashCode()
        result = 31 * result + issuingCountry.hashCode()
        result = 31 * result + (issuingSubdivision?.hashCode() ?: 0)
        result = 31 * result + ageOver18.hashCode()
        result = 31 * result + (ageInYears?.hashCode() ?: 0)
        result = 31 * result + (ageOverNN?.hashCode() ?: 0)
        result = 31 * result + (ageBirthYear?.hashCode() ?: 0)
        result = 31 * result + (portraitCaptureDate?.hashCode() ?: 0)
        result = 31 * result + (birthplace?.hashCode() ?: 0)
        result = 31 * result + (nameAtBirth?.hashCode() ?: 0)
        result = 31 * result + (residentAddress?.hashCode() ?: 0)
        result = 31 * result + (residentCity?.hashCode() ?: 0)
        result = 31 * result + (residentCityLatin1?.hashCode() ?: 0)
        result = 31 * result + (residentPostalCode?.hashCode() ?: 0)
        result = 31 * result + (residentCountry?.hashCode() ?: 0)
        result = 31 * result + (sex?.hashCode() ?: 0)
        result = 31 * result + (nationality?.hashCode() ?: 0)
        result = 31 * result + (documentNumber?.hashCode() ?: 0)
        result = 31 * result + (personId?.hashCode() ?: 0)
        result = 31 * result + (birthCountry?.hashCode() ?: 0)
        result = 31 * result + (birthState?.hashCode() ?: 0)
        result = 31 * result + (birthCity?.hashCode() ?: 0)
        result = 31 * result + (administrativeNumber?.hashCode() ?: 0)
        result = 31 * result + (residentStreet?.hashCode() ?: 0)
        result = 31 * result + (residentHouseNumber?.hashCode() ?: 0)
        result = 31 * result + (residentState?.hashCode() ?: 0)
        result = 31 * result + (travelDocumentType?.hashCode() ?: 0)
        result = 31 * result + (travelDocumentNumber?.hashCode() ?: 0)
        result = 31 * result + (travelDocumentMrz?.hashCode() ?: 0)
        return result
    }

    /**
     * Convert to CBOR map element
     */
    fun toMapElement() = MapElement(
        buildMap {
            put(MapKey("family_name"), familyName.toDataElement())
            givenName?.let { put(MapKey("given_name"), it.toDataElement()) }
            familyNameViz?.let { put(MapKey("family_name_viz"), it.toDataElement()) }
            givenNameViz?.let { put(MapKey("given_name_viz"), it.toDataElement()) }
            familyNameLatin1?.let { put(MapKey("family_name_latin1"), it.toDataElement()) }
            givenNameLatin1?.let { put(MapKey("given_name_latin1"), it.toDataElement()) }
            put(MapKey("birth_date"), birthDate.toMapElement())
            put(MapKey("portrait"), portrait.toDataElement())
            enrolmentPortraitImage?.let { put(MapKey("enrolment_portrait_image"), it.toDataElement()) }
            put(MapKey("issue_date"), issueDate.toDataElement())
            put(MapKey("expiry_date"), expiryDate.toDataElement())
            put(MapKey("issuing_authority"), issuingAuthority.toDataElement())
            put(MapKey("issuing_country"), issuingCountry.toDataElement())
            issuingSubdivision?.let { put(MapKey("issuing_subdivision"), it.toDataElement()) }
            put(MapKey("age_over_18"), ageOver18.toDataElement())
            ageInYears?.let { put(MapKey("age_in_years"), it.toDataElement()) }
            ageOverNN?.let { put(MapKey("age_over_NN"), it.toDataElement()) }
            ageBirthYear?.let { put(MapKey("age_birth_year"), it.toDataElement()) }
            portraitCaptureDate?.let { put(MapKey("portrait_capture_date"), it.toDataElement()) }
            birthplace?.let { put(MapKey("birthplace"), it.toDataElement()) }
            nameAtBirth?.let { put(MapKey("name_at_birth"), it.toDataElement()) }
            residentAddress?.let { put(MapKey("resident_address"), it.toDataElement()) }
            residentCity?.let { put(MapKey("resident_city"), it.toDataElement()) }
            residentCityLatin1?.let { put(MapKey("resident_city_latin1"), it.toDataElement()) }
            residentPostalCode?.let { put(MapKey("resident_postal_code"), it.toDataElement()) }
            residentCountry?.let { put(MapKey("resident_country"), it.toDataElement()) }
            sex?.let { put(MapKey("sex"), NumberElement(it.code)) }
            nationality?.let { put(MapKey("nationality"), it.toDataElement()) }
            documentNumber?.let { put(MapKey("document_number"), it.toDataElement()) }
            personId?.let { put(MapKey("person_id"), it.toDataElement()) }
            birthCountry?.let { put(MapKey("birth_country"), it.toDataElement()) }
            birthState?.let { put(MapKey("birth_state"), it.toDataElement()) }
            birthCity?.let { put(MapKey("birth_city"), it.toDataElement()) }
            administrativeNumber?.let { put(MapKey("administrative_number"), it.toDataElement()) }
            residentStreet?.let { put(MapKey("resident_street"), it.toDataElement()) }
            residentHouseNumber?.let { put(MapKey("resident_house_number"), it.toDataElement()) }
            residentState?.let { put(MapKey("resident_state"), it.toDataElement()) }
            travelDocumentType?.let { put(MapKey("travel_document_type"), it.toDataElement()) }
            travelDocumentNumber?.let { put(MapKey("travel_document_number"), it.toDataElement()) }
            travelDocumentMrz?.let { put(MapKey("travel_document_mrz"), it.toDataElement()) }
        }
    )

    /**
     * Serialize to CBOR data
     */
    fun toCBOR() = toMapElement().toCBOR()

    /**
     * Serialize to CBOR hex string
     */
    fun toCBORHex() = toMapElement().toCBORHex()

    /**
     * Serialize to JSON object
     */
    fun toJSON() = buildJsonObject {
        put("family_name", Json.encodeToJsonElement(familyName))
        givenName?.let { put("given_name", Json.encodeToJsonElement(it)) }
        familyNameViz?.let { put("family_name_viz", Json.encodeToJsonElement(it)) }
        givenNameViz?.let { put("given_name_viz", Json.encodeToJsonElement(it)) }
        familyNameLatin1?.let { put("family_name_latin1", Json.encodeToJsonElement(it)) }
        givenNameLatin1?.let { put("given_name_latin1", Json.encodeToJsonElement(it)) }
        put("birth_date", birthDate.toJSON())
        put("portrait", Json.encodeToJsonElement(portrait))
        enrolmentPortraitImage?.let { put("enrolment_portrait_image", Json.encodeToJsonElement(it)) }
        put("issue_date", Json.encodeToJsonElement(issueDate))
        put("expiry_date", Json.encodeToJsonElement(expiryDate))
        put("issuing_authority", Json.encodeToJsonElement(issuingAuthority))
        put("issuing_country", Json.encodeToJsonElement(issuingCountry))
        issuingSubdivision?.let { put("issuing_subdivision", Json.encodeToJsonElement(it)) }
        put("age_over_18", Json.encodeToJsonElement(ageOver18))
        ageInYears?.let { put("age_in_years", Json.encodeToJsonElement(it)) }
        ageOverNN?.let { put("age_over_NN", Json.encodeToJsonElement(it)) }
        ageBirthYear?.let { put("age_birth_year", Json.encodeToJsonElement(it)) }
        portraitCaptureDate?.let { put("portrait_capture_date", Json.encodeToJsonElement(it)) }
        birthplace?.let { put("birthplace", Json.encodeToJsonElement(it)) }
        nameAtBirth?.let { put("name_at_birth", Json.encodeToJsonElement(it)) }
        residentAddress?.let { put("resident_address", Json.encodeToJsonElement(it)) }
        residentCity?.let { put("resident_city", Json.encodeToJsonElement(it)) }
        residentCityLatin1?.let { put("resident_city_latin1", Json.encodeToJsonElement(it)) }
        residentPostalCode?.let { put("resident_postal_code", Json.encodeToJsonElement(it)) }
        residentCountry?.let { put("resident_country", Json.encodeToJsonElement(it)) }
        sex?.let { put("sex", Json.encodeToJsonElement(it.code)) }
        nationality?.let { put("nationality", Json.encodeToJsonElement(it)) }
        documentNumber?.let { put("document_number", Json.encodeToJsonElement(it)) }
        personId?.let { put("person_id", Json.encodeToJsonElement(it)) }
        birthCountry?.let { put("birth_country", Json.encodeToJsonElement(it)) }
        birthState?.let { put("birth_state", Json.encodeToJsonElement(it)) }
        birthCity?.let { put("birth_city", Json.encodeToJsonElement(it)) }
        administrativeNumber?.let { put("administrative_number", Json.encodeToJsonElement(it)) }
        residentStreet?.let { put("resident_street", Json.encodeToJsonElement(it)) }
        residentHouseNumber?.let { put("resident_house_number", Json.encodeToJsonElement(it)) }
        residentState?.let { put("resident_state", Json.encodeToJsonElement(it)) }
        travelDocumentType?.let { put("travel_document_type", Json.encodeToJsonElement(it)) }
        travelDocumentNumber?.let { put("travel_document_number", Json.encodeToJsonElement(it)) }
        travelDocumentMrz?.let { put("travel_document_mrz", Json.encodeToJsonElement(it)) }
    }

    companion object {
        /**
         * Deserialize from CBOR data
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBOR(cbor: ByteArray) = Cbor.decodeFromByteArray<PhotoId>(cbor)

        /**
         * Deserialize from CBOR hex string
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromCBORHex(cbor: String) = Cbor.decodeFromHexString<PhotoId>(cbor)

        fun fromMapElement(element: MapElement): PhotoId {
            require(element.value.containsKey(MapKey("family_name"))) {
                "PhotoId CBOR map must contain string key family_name"
            }
            require(element.value.containsKey(MapKey("birth_date"))) {
                "PhotoId CBOR map must contain key birth_date"
            }
            require(element.value.containsKey(MapKey("portrait"))) {
                "PhotoId CBOR map must contain key portrait"
            }
            require(element.value.containsKey(MapKey("issue_date"))) {
                "PhotoId CBOR map must contain key issue_date"
            }
            require(element.value.containsKey(MapKey("expiry_date"))) {
                "PhotoId CBOR map must contain key expiry_date"
            }
            require(element.value.containsKey(MapKey("issuing_authority"))) {
                "PhotoId CBOR map must contain key issuing_authority"
            }
            require(element.value.containsKey(MapKey("issuing_country"))) {
                "PhotoId CBOR map must contain key issuing_country"
            }
            require(element.value.containsKey(MapKey("age_over_18"))) {
                "PhotoId CBOR map must contain key age_over_18"
            }

            return PhotoId(
                familyName = (element.value[MapKey("family_name")]!! as StringElement).value,
                givenName = element.value[MapKey("given_name")]?.let { (it as StringElement).value },
                familyNameViz = element.value[MapKey("family_name_viz")]?.let { (it as StringElement).value },
                givenNameViz = element.value[MapKey("given_name_viz")]?.let { (it as StringElement).value },
                familyNameLatin1 = element.value[MapKey("family_name_latin1")]?.let { (it as StringElement).value },
                givenNameLatin1 = element.value[MapKey("given_name_latin1")]?.let { (it as StringElement).value },
                birthDate = BirthDate.fromMapElement(element.value[MapKey("birth_date")]!! as MapElement),
                portrait = (element.value[MapKey("portrait")]!! as ByteStringElement).value,
                enrolmentPortraitImage = element.value[MapKey("enrolment_portrait_image")]?.let { (it as ByteStringElement).value },
                issueDate = (element.value[MapKey("issue_date")]!! as FullDateElement).value,
                expiryDate = (element.value[MapKey("expiry_date")]!! as FullDateElement).value,
                issuingAuthority = (element.value[MapKey("issuing_authority")]!! as StringElement).value,
                issuingCountry = (element.value[MapKey("issuing_country")]!! as StringElement).value,
                issuingSubdivision = element.value[MapKey("issuing_subdivision")]?.let { (it as StringElement).value },
                ageOver18 = (element.value[MapKey("age_over_18")]!! as BooleanElement).value,
                ageInYears = element.value[MapKey("age_in_years")]?.let { (it as NumberElement).value.toInt().toUInt() },
                ageOverNN = element.value[MapKey("age_over_NN")]?.let { (it as BooleanElement).value },
                ageBirthYear = element.value[MapKey("age_birth_year")]?.let { (it as NumberElement).value.toInt().toUInt() },
                portraitCaptureDate = element.value[MapKey("portrait_capture_date")]?.let { (it as FullDateElement).value },
                birthplace = element.value[MapKey("birthplace")]?.let { (it as StringElement).value },
                nameAtBirth = element.value[MapKey("name_at_birth")]?.let { (it as StringElement).value },
                residentAddress = element.value[MapKey("resident_address")]?.let { (it as StringElement).value },
                residentCity = element.value[MapKey("resident_city")]?.let { (it as StringElement).value },
                residentCityLatin1 = element.value[MapKey("resident_city_latin1")]?.let { (it as StringElement).value },
                residentPostalCode = element.value[MapKey("resident_postal_code")]?.let { (it as StringElement).value },
                residentCountry = element.value[MapKey("resident_country")]?.let { (it as StringElement).value },
                sex = element.value[MapKey("sex")]?.let { IsoSexEnum.parseCode((it as NumberElement).value.toInt()) },
                nationality = element.value[MapKey("nationality")]?.let { (it as StringElement).value },
                documentNumber = element.value[MapKey("document_number")]?.let { (it as StringElement).value },
                personId = element.value[MapKey("person_id")]?.let { (it as StringElement).value },
                birthCountry = element.value[MapKey("birth_country")]?.let { (it as StringElement).value },
                birthState = element.value[MapKey("birth_state")]?.let { (it as StringElement).value },
                birthCity = element.value[MapKey("birth_city")]?.let { (it as StringElement).value },
                administrativeNumber = element.value[MapKey("administrative_number")]?.let { (it as StringElement).value },
                residentStreet = element.value[MapKey("resident_street")]?.let { (it as StringElement).value },
                residentHouseNumber = element.value[MapKey("resident_house_number")]?.let { (it as StringElement).value },
                residentState = element.value[MapKey("resident_state")]?.let { (it as StringElement).value },
                travelDocumentType = element.value[MapKey("travel_document_type")]?.let { (it as StringElement).value },
                travelDocumentNumber = element.value[MapKey("travel_document_number")]?.let { (it as StringElement).value },
                travelDocumentMrz = element.value[MapKey("travel_document_mrz")]?.let { (it as StringElement).value }
            )
        }

        fun fromJSON(jsonObject: JsonObject) = PhotoId(
            familyName = jsonObject.getValue("family_name").jsonPrimitive.content,
            givenName = jsonObject["given_name"]?.jsonPrimitive?.content,
            familyNameViz = jsonObject["family_name_viz"]?.jsonPrimitive?.content,
            givenNameViz = jsonObject["given_name_viz"]?.jsonPrimitive?.content,
            familyNameLatin1 = jsonObject["family_name_latin1"]?.jsonPrimitive?.content,
            givenNameLatin1 = jsonObject["given_name_latin1"]?.jsonPrimitive?.content,
            birthDate = BirthDate.fromJSON(jsonObject.getValue("birth_date").jsonObject),
            portrait = Json.decodeFromJsonElement<ByteArray>(jsonObject.getValue("portrait")),
            enrolmentPortraitImage = jsonObject["enrolment_portrait_image"]?.let { Json.decodeFromJsonElement<ByteArray>(it) },
            issueDate = Json.decodeFromJsonElement<LocalDate>(jsonObject.getValue("issue_date")),
            expiryDate = Json.decodeFromJsonElement<LocalDate>(jsonObject.getValue("expiry_date")),
            issuingAuthority = jsonObject.getValue("issuing_authority").jsonPrimitive.content,
            issuingCountry = jsonObject.getValue("issuing_country").jsonPrimitive.content,
            issuingSubdivision = jsonObject["issuing_subdivision"]?.jsonPrimitive?.content,
            ageOver18 = jsonObject.getValue("age_over_18").jsonPrimitive.boolean,
            ageInYears = jsonObject["age_in_years"]?.jsonPrimitive?.int?.toUInt(),
            ageOverNN = jsonObject["age_over_NN"]?.jsonPrimitive?.boolean,
            ageBirthYear = jsonObject["age_birth_year"]?.jsonPrimitive?.int?.toUInt(),
            portraitCaptureDate = jsonObject["portrait_capture_date"]?.let { Json.decodeFromJsonElement<LocalDate>(it) },
            birthplace = jsonObject["birthplace"]?.jsonPrimitive?.content,
            nameAtBirth = jsonObject["name_at_birth"]?.jsonPrimitive?.content,
            residentAddress = jsonObject["resident_address"]?.jsonPrimitive?.content,
            residentCity = jsonObject["resident_city"]?.jsonPrimitive?.content,
            residentCityLatin1 = jsonObject["resident_city_latin1"]?.jsonPrimitive?.content,
            residentPostalCode = jsonObject["resident_postal_code"]?.jsonPrimitive?.content,
            residentCountry = jsonObject["resident_country"]?.jsonPrimitive?.content,
            sex = jsonObject["sex"]?.jsonPrimitive?.int?.let { IsoSexEnum.parseCode(it) },
            nationality = jsonObject["nationality"]?.jsonPrimitive?.content,
            documentNumber = jsonObject["document_number"]?.jsonPrimitive?.content,
            personId = jsonObject["person_id"]?.jsonPrimitive?.content,
            birthCountry = jsonObject["birth_country"]?.jsonPrimitive?.content,
            birthState = jsonObject["birth_state"]?.jsonPrimitive?.content,
            birthCity = jsonObject["birth_city"]?.jsonPrimitive?.content,
            administrativeNumber = jsonObject["administrative_number"]?.jsonPrimitive?.content,
            residentStreet = jsonObject["resident_street"]?.jsonPrimitive?.content,
            residentHouseNumber = jsonObject["resident_house_number"]?.jsonPrimitive?.content,
            residentState = jsonObject["resident_state"]?.jsonPrimitive?.content,
            travelDocumentType = jsonObject["travel_document_type"]?.jsonPrimitive?.content,
            travelDocumentNumber = jsonObject["travel_document_number"]?.jsonPrimitive?.content,
            travelDocumentMrz = jsonObject["travel_document_mrz"]?.jsonPrimitive?.content
        )
    }
}

internal object PhotoIdSerializer : KSerializer<PhotoId> {
    override val descriptor = buildClassSerialDescriptor("PhotoId")

    override fun serialize(encoder: Encoder, value: PhotoId) {
        when (encoder) {
            is JsonEncoder -> {
                encoder.encodeSerializableValue(JsonObject.serializer(), value.toJSON())
            }
            else -> {
                encoder.encodeSerializableValue(DataElementSerializer, value.toMapElement())
            }
        }
    }

    override fun deserialize(decoder: Decoder): PhotoId {
        return when (decoder) {
            is JsonDecoder -> {
                PhotoId.fromJSON(decoder.decodeJsonElement().jsonObject)
            }
            else -> {
                PhotoId.fromMapElement(decoder.decodeSerializableValue(DataElementSerializer) as MapElement)
            }
        }
    }
}
