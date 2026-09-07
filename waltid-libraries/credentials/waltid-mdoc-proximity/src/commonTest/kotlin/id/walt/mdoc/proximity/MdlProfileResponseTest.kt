@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.DrivingPrivilegeCode
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.document.Document
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixed Austrian test profile, issued and selectively disclosed through production code.
 * These assertions qualify the configured profile's returned values, not arbitrary issuer input
 * or an authority's enrolment process. The small JPEG tests the wire format, not portrait quality.
 */
class MdlProfileResponseTest {
    @Test
    fun profileTextRetainsLatin1CharactersAndLengthBoundariesInDeviceResponse() = runTest {
        val texts = linkedMapOf(
            "family_name" to "Dö",
            "given_name" to "Éva",
            "issuing_authority" to "Testbehörde Wien",
            "document_number" to "TEST-001",
            "administrative_number" to "A",
            "birth_place" to "Köln",
            "resident_address" to "Teststraße 1",
            "resident_city" to "Wien",
            "resident_state" to "Wien",
            "resident_postal_code" to "1010",
        )
        val response = responseElements(profile())
        for ((identifier, expected) in texts) {
            val actual = assertIs<CborString>(response[identifier], identifier).value
            assertEquals(expected, actual, identifier)
            assertTrue(actual.length in 1..150, identifier)
            assertTrue(actual.all { it.code in 0x20..0x7e || it.code in 0xa0..0xff }, identifier)
        }
        // Latin-1 characters still require UTF-8 on the CBOR wire; 150 characters are not 150 bytes.
        val upperBoundary = "É".repeat(150)
        val boundaryResponse = responseElements(profile().copy(familyName = upperBoundary))
        assertEquals(upperBoundary, assertIs<CborString>(boundaryResponse["family_name"]).value)
    }

    @Test
    fun profileCountrySubdivisionAndAppearanceCodesRetainTheirAssignedValues() = runTest {
        val response = responseElements(profile())
        // Fixed registry entries: ISO 3166 AT (Austria), AT-9 (Wien), UN distinguishing sign A.
        for (identifier in listOf("issuing_country", "nationality", "resident_country")) {
            assertEquals(CborString("AT"), response[identifier], identifier)
        }
        assertEquals(CborString("AT-9"), response["issuing_jurisdiction"])
        assertEquals(CborString("A"), response["un_distinguishing_sign"])
        assertEquals(CborString("blue"), response["eye_colour"])
        assertEquals(CborString("brown"), response["hair_colour"])
    }

    @Test
    fun profileDrivingCategoriesAndRestrictionsRetainTheirValueDomains() = runTest {
        val response = responseElements(profile())
        val privileges = assertIs<CborArray>(response["driving_privileges"])
            .map { assertIs<CborMap>(it) }
        val categories = privileges.map { assertIs<CborString>(it[CborString("vehicle_category_code")]).value }
        assertEquals(listOf("A1", "A2", "B"), categories)
        assertTrue("A2" !in categories || "A1" in categories)
        val codes = privileges.flatMap { privilege ->
            val codes = assertIs<CborArray>(privilege[CborString("codes")])
            assertTrue(codes.isNotEmpty(), "A present codes array must not be empty")
            codes.map { assertIs<CborMap>(it) }
        }
        assertEquals(listOf("01", "78", "S05"), codes.map { it[CborString("code")] }.map { assertIs<CborString>(it).value })
        val restriction = codes.last()
        assertEquals(CborString("<="), restriction[CborString("sign")])
        val value = assertIs<CborString>(restriction[CborString("value")]).value
        assertEquals("3500", value)
        assertTrue(value.all { it in '0'..'9' })
    }

    @Test
    fun profilePortraitAndSignatureReturnTheExactIndependentlyDecodableJpeg() = runTest {
        val response = responseElements(profile())
        val jpeg = profileJpeg()
        for (identifier in listOf("portrait", "signature_usual_mark")) {
            val actual = assertIs<CborByteString>(response[identifier], identifier).toByteArray()
            assertContentEquals(jpeg, actual, identifier)
            assertContentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte()), actual.copyOf(2), identifier)
            assertEquals(PROFILE_JPEG_SHA256, SHA256().digest(actual).toHexString(), identifier)
        }
    }

    internal suspend fun responseElements(mdl: Mdl): Map<String, CborElement> {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val holderKey = runtime.generateMdocTestKey("profile-holder", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val issuerKey = runtime.generateMdocTestKey("profile-issuer", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Profile test issuer" }
        val issuerSigned = MdocIssuer.signMsoForIssuerSignedObjects(
            namespaceIssuerSignedItems = mdl.toNamespaceIssuerSignedItems(),
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = mdl.docType,
        )
        val source = Document(mdl.docType, issuerSigned)
        val sourceItems = issuerSigned.namespaces!!.getValue(NAMESPACE).entries
        val selected = sourceItems.mapTo(linkedSetOf()) { ElementReference(NAMESPACE, it.value.elementIdentifier) }
        val response = MdocResponseBuilder().buildResponse(
            listOf(MdocDocumentPresentation(source, holderKey, selected, authentication = MdocAuthenticationMethod.Signature())),
            SessionTranscript.forQr(byteArrayOf(1, 2), byteArrayOf(3, 4)),
        )
        assertEquals(0u, response.status)
        // Inspect the raw response, including each tagged IssuerSignedItemBytes, without Mdl decoding.
        val wire = assertIs<CborMap>(coseCompliantCbor.decodeFromByteArray<CborElement>(coseCompliantCbor.encodeToByteArray(response)))
        assertNull(wire[CborString("documentErrors")])
        val document = assertIs<CborMap>(assertIs<CborArray>(wire[CborString("documents")]).single())
        assertNull(document[CborString("errors")])
        assertEquals(CborString("org.iso.18013.5.1.mDL"), document[CborString("docType")])
        val returnedIssuer = assertIs<CborMap>(document[CborString("issuerSigned")])
        val namespaces = assertIs<CborMap>(returnedIssuer[CborString("nameSpaces")])
        val items = assertIs<CborArray>(namespaces[CborString(NAMESPACE)])
        val elements = items.associate { wrapped ->
            val bytes = assertIs<CborByteString>(wrapped).toByteArray()
            val item = assertIs<CborMap>(coseCompliantCbor.decodeFromByteArray<CborElement>(bytes))
            val identifier = assertIs<CborString>(item[CborString("elementIdentifier")]).value
            assertContentEquals(sourceItems.single { it.value.elementIdentifier == identifier }.serialized, bytes, identifier)
            identifier to requireNotNull(item[CborString("elementValue")])
        }
        assertEquals(selected.mapTo(linkedSetOf()) { it.elementIdentifier }, elements.keys)
        return elements
    }

    internal fun profile() = Mdl(
        familyName = "Dö", givenName = "Éva", birthDate = LocalDate(1990, 1, 2),
        issueDate = LocalDate(2026, 1, 1), expiryDate = LocalDate(2031, 1, 1),
        issuingCountry = "AT", issuingAuthority = "Testbehörde Wien", documentNumber = "TEST-001",
        portrait = profileJpeg(), unDistinguishingSign = "A", administrativeNumber = "A",
        drivingPrivileges = listOf(
            DrivingPrivilege("A1", codes = listOf(DrivingPrivilegeCode("01"))),
            DrivingPrivilege("A2", codes = listOf(DrivingPrivilegeCode("78"))),
            DrivingPrivilege("B", codes = listOf(DrivingPrivilegeCode("S05", "<=", "3500"))),
        ),
        eyeColour = "blue", hairColour = "brown", birthPlace = "Köln", residentAddress = "Teststraße 1",
        issuingJurisdiction = "AT-9", nationality = "AT", residentCity = "Wien", residentState = "Wien",
        residentPostalCode = "1010", residentCountry = "AT", signatureOrUsualMark = profileJpeg(),
    )

    private companion object { const val NAMESPACE = "org.iso.18013.5.1" }
}

// Generated 2x2 RGB JPEG, decoded independently with Pillow and ImageIO in the JVM test below.
// Its pixels are irrelevant: the profile cases assert encoding, not biometric image quality.
internal const val PROFILE_JPEG_SHA256 = "04f51a545ac77eeee6ada28f477c1fe6c4d36c39fbc2ff48311460f31fb81aef"
internal fun profileJpeg(): ByteArray = Base64.decode(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDxeiiiv2s/Ij//2Q=="
)
