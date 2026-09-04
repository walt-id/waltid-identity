@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.DrivingPrivilegeCode
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.credsdata.isoshared.IsoSexEnum
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MdlAppendixDataMatrixTest {
    @Test
    fun `configured mDL profile retains every element identifier and wire representation`() {
        val encoded = issuerSignedElements(
            testMdl().copy(
                drivingPrivileges = listOf(
                    DrivingPrivilege(
                        vehicleCategoryCode = "B",
                        issueDate = LocalDate(2020, 2, 3),
                        expiryDate = LocalDate(2030, 2, 3),
                        codes = listOf(DrivingPrivilegeCode(code = "01", sign = "+", value = "10")),
                    )
                ),
                administrativeNumber = "ADMIN-1",
                sex = IsoSexEnum.FEMALE,
                height = 170u,
                weight = 65u,
                eyeColour = "blue",
                hairColour = "brown",
                birthPlace = "Vienna",
                residentAddress = "Example street 1",
                ageInYears = 42u,
                ageBirthYear = 1984u,
                ageOver12 = true,
                ageOver13 = true,
                ageOver14 = true,
                ageOver16 = true,
                ageOver18 = true,
                ageOver21 = true,
                ageOver25 = true,
                ageOver60 = false,
                ageOver62 = false,
                ageOver65 = false,
                ageOver68 = false,
                issuingJurisdiction = "AT-9",
                nationality = "AT",
                residentCity = "Vienna",
                residentState = "Vienna",
                residentPostalCode = "1010",
                residentCountry = "AT",
                biometricTemplateFace = byteArrayOf(0x01),
                biometricTemplateFinger = byteArrayOf(0x02),
                biometricTemplateSignatureSign = byteArrayOf(0x03),
                biometricTemplateIris = byteArrayOf(0x04),
                familyNameNationalCharacter = "Dö",
                givenNameNationalCharacter = "Jäne",
                signatureOrUsualMark = byteArrayOf(0x05),
            )
        )

        val textValues = linkedMapOf(
            "family_name" to "Doe",
            "given_name" to "Jane",
            "issuing_country" to "AT",
            "issuing_authority" to "Example authority",
            "document_number" to "TEST-1",
            "un_distinguishing_sign" to "AT",
            "administrative_number" to "ADMIN-1",
            "eye_colour" to "blue",
            "hair_colour" to "brown",
            "birth_place" to "Vienna",
            "resident_address" to "Example street 1",
            "issuing_jurisdiction" to "AT-9",
            "nationality" to "AT",
            "resident_city" to "Vienna",
            "resident_state" to "Vienna",
            "resident_postal_code" to "1010",
            "resident_country" to "AT",
            "family_name_national_character" to "Dö",
            "given_name_national_character" to "Jäne",
        )
        val fullDates = linkedMapOf(
            "birth_date" to "1984-01-02",
            "issue_date" to "2026-01-01",
            "expiry_date" to "2031-01-01",
        )
        val unsignedValues = linkedMapOf(
            "sex" to 2uL,
            "height" to 170uL,
            "weight" to 65uL,
            "age_in_years" to 42uL,
            "age_birth_year" to 1984uL,
        )
        val booleanValues = linkedMapOf(
            "age_over_12" to true,
            "age_over_13" to true,
            "age_over_14" to true,
            "age_over_16" to true,
            "age_over_18" to true,
            "age_over_21" to true,
            "age_over_25" to true,
            "age_over_60" to false,
            "age_over_62" to false,
            "age_over_65" to false,
            "age_over_68" to false,
        )
        val byteStrings = linkedMapOf(
            "portrait" to byteArrayOf(0x7f),
            "biometric_template_face" to byteArrayOf(0x01),
            "biometric_template_finger" to byteArrayOf(0x02),
            "biometric_template_signature_sign" to byteArrayOf(0x03),
            "biometric_template_iris" to byteArrayOf(0x04),
            "signature_usual_mark" to byteArrayOf(0x05),
        )
        val expectedIdentifiers =
            textValues.keys + fullDates.keys + unsignedValues.keys + booleanValues.keys +
                    byteStrings.keys + "driving_privileges"

        assertEquals(
            expectedIdentifiers,
            encoded.keys,
            "DM_PROFILE:exact configured element identifiers",
        )
        textValues.forEach { (identifier, expected) ->
            assertEquals(CborString(expected), encoded[identifier], "DM_TEXT:$identifier")
        }
        fullDates.forEach { (identifier, expected) ->
            assertEquals(CborString(expected, 1004uL), encoded[identifier], "DM_FULL_DATE:$identifier")
        }
        unsignedValues.forEach { (identifier, expected) ->
            assertEquals(
                expected,
                assertIs<CborInteger>(encoded[identifier], "DM_UINT:$identifier").absoluteValue,
                "DM_UINT:$identifier",
            )
        }
        booleanValues.forEach { (identifier, expected) ->
            assertEquals(
                expected,
                assertIs<CborBoolean>(encoded[identifier], "DM_BOOL:$identifier").value,
                "DM_BOOL:$identifier",
            )
        }
        byteStrings.forEach { (identifier, expected) ->
            assertContentEquals(
                expected,
                assertIs<CborByteString>(encoded[identifier], "DM_BSTR:$identifier").toByteArray(),
                "DM_BSTR:$identifier",
            )
        }

        val privileges = assertIs<CborArray>(encoded["driving_privileges"])
        val privilege = assertIs<CborMap>(privileges.single())
        assertEquals(
            setOf("vehicle_category_code", "issue_date", "expiry_date", "codes"),
            privilege.keys.mapTo(linkedSetOf()) { assertIs<CborString>(it).value },
            "DM_DRIVING_PRIVILEGES:exact nested identifiers",
        )
        assertEquals(CborString("B"), privilege[CborString("vehicle_category_code")])
        assertEquals(CborString("2020-02-03", 1004uL), privilege[CborString("issue_date")])
        assertEquals(CborString("2030-02-03", 1004uL), privilege[CborString("expiry_date")])
        val code = assertIs<CborMap>(assertIs<CborArray>(privilege[CborString("codes")]).single())
        assertEquals(
            setOf("code", "sign", "value"),
            code.keys.mapTo(linkedSetOf()) { assertIs<CborString>(it).value },
            "DM_DRIVING_PRIVILEGES:exact restriction-code identifiers",
        )
        assertEquals(CborString("01"), code[CborString("code")])
        assertEquals(CborString("+"), code[CborString("sign")])
        assertEquals(CborString("10"), code[CborString("value")])
    }

    @Test
    fun `optional age elements retain their identifiers CBOR types and values`() {
        val ageValues = linkedMapOf(
            "age_in_years" to 42uL,
            "age_birth_year" to 1984uL,
        )
        val ageAttestations = linkedMapOf(
            "age_over_12" to true,
            "age_over_13" to true,
            "age_over_14" to true,
            "age_over_16" to true,
            "age_over_18" to true,
            "age_over_21" to true,
            "age_over_25" to true,
            "age_over_60" to false,
            "age_over_62" to false,
            "age_over_65" to false,
            "age_over_68" to false,
        )
        val encoded = encode(
            testMdl().copy(
                ageInYears = 42u,
                ageBirthYear = 1984u,
                ageOver12 = true,
                ageOver13 = true,
                ageOver14 = true,
                ageOver16 = true,
                ageOver18 = true,
                ageOver21 = true,
                ageOver25 = true,
                ageOver60 = false,
                ageOver62 = false,
                ageOver65 = false,
                ageOver68 = false,
            )
        )

        ageValues.forEach { (identifier, expected) ->
            val actual = assertIs<CborInteger>(encoded[CborString(identifier)], "DM_AGE_POLICY:$identifier")
            assertEquals(expected, actual.absoluteValue, "DM_AGE_POLICY:$identifier")
        }
        ageAttestations.forEach { (identifier, expected) ->
            val actual = assertIs<CborBoolean>(encoded[CborString(identifier)], "DM_AGE_POLICY:$identifier")
            assertEquals(expected, actual.value, "DM_AGE_POLICY:$identifier")
        }

        Mdl.registerSerializationTypes()
        (ageValues.keys + ageAttestations.keys).forEach { identifier ->
            assertNotNull(
                MdocsCborSerializer.lookupSerializer(MDL_NAMESPACE, identifier),
                "DM_AGE_POLICY:$identifier must be registered for dynamic issuer-signed decoding",
            )
        }
    }

    @Test
    fun `portrait signature and biometric elements use CBOR byte strings and JSON base64url`() {
        val templates = linkedMapOf(
            "portrait" to byteArrayOf(0x7f),
            "biometric_template_face" to byteArrayOf(0x01, 0x02, 0x03),
            "biometric_template_finger" to byteArrayOf(0x10, 0x11),
            "biometric_template_signature_sign" to byteArrayOf(0x20, 0x21, 0x22, 0x23),
            "biometric_template_iris" to byteArrayOf(0x30),
            "signature_usual_mark" to byteArrayOf(0x55),
        )
        val mdl = testMdl().copy(
            biometricTemplateFace = templates.getValue("biometric_template_face"),
            biometricTemplateFinger = templates.getValue("biometric_template_finger"),
            biometricTemplateSignatureSign = templates.getValue("biometric_template_signature_sign"),
            biometricTemplateIris = templates.getValue("biometric_template_iris"),
            signatureOrUsualMark = templates.getValue("signature_usual_mark"),
        )
        val encoded = encode(mdl)

        Mdl.registerSerializationTypes()
        templates.forEach { (identifier, expected) ->
            val actual = assertIs<CborByteString>(
                encoded[CborString(identifier)],
                "DM_BIOMETRIC_STRUCTURE:$identifier",
            )
            assertContentEquals(expected, actual.toByteArray(), "DM_BIOMETRIC_STRUCTURE:$identifier")
            assertNotNull(
                MdocsCborSerializer.lookupSerializer(MDL_NAMESPACE, identifier),
                "DM_BIOMETRIC_STRUCTURE:$identifier must be registered for dynamic issuer-signed decoding",
            )
        }

        val json = Json.encodeToJsonElement(Mdl.serializer(), mdl).jsonObject
        assertEquals("fw", json.getValue("portrait").jsonPrimitive.content)
        assertEquals("AQID", json.getValue("biometric_template_face").jsonPrimitive.content)
        assertEquals("VQ", json.getValue("signature_usual_mark").jsonPrimitive.content)

        val decoded = Json.decodeFromJsonElement(Mdl.serializer(), json)
        assertContentEquals(templates.getValue("portrait"), decoded.portrait)
        assertContentEquals(templates.getValue("biometric_template_face"), decoded.biometricTemplateFace)
        assertContentEquals(templates.getValue("signature_usual_mark"), decoded.signatureOrUsualMark)
    }

    private fun encode(mdl: Mdl): CborMap {
        val bytes = MdocCbor.encodeToByteArray(Mdl.serializer(), mdl)
        return assertIs<CborMap>(MdocCbor.decodeFromByteArray<CborElement>(bytes))
    }

    private fun issuerSignedElements(mdl: Mdl): Map<String, CborElement> =
        mdl.toNamespaceIssuerSignedItems().getValue(MDL_NAMESPACE).associate {
            it.elementIdentifier to it.elementValue
        }

    private fun testMdl() = Mdl(
        familyName = "Doe",
        givenName = "Jane",
        birthDate = LocalDate(1984, 1, 2),
        issueDate = LocalDate(2026, 1, 1),
        expiryDate = LocalDate(2031, 1, 1),
        issuingCountry = "AT",
        issuingAuthority = "Example authority",
        documentNumber = "TEST-1",
        portrait = byteArrayOf(0x7f),
        drivingPrivileges = emptyList(),
        unDistinguishingSign = "AT",
    )

    private companion object {
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }
}
