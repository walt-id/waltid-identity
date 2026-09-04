@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlin.ExperimentalUnsignedTypes::class,
)

import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
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
