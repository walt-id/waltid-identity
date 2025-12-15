package id.walt.credentials.formats

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MdocsCredentialSerializationTest {

    private val dummyValueDigest = ValueDigest(key = 1u, value = byteArrayOf())
    private val dummyDigestList = ValueDigestList(entries = listOf(dummyValueDigest))
    private val dummyCoseKey = CoseKey(
        kty = Cose.KeyTypes.EC2,
        crv = Cose.EllipticCurves.P_256,
        kid = byteArrayOf(),
        x = byteArrayOf(),
        y = byteArrayOf()
    )
    private val dummyDeviceKeyInfo = DeviceKeyInfo(
        deviceKey = dummyCoseKey
    )
    private val now = Clock.System.now()
    private val dummyValidityInfo = ValidityInfo(
        signed = now,
        validFrom = now,
        validUntil = now,
        expectedUpdate = null
    )
    private val dummyMso = createDummyMso()
    private val credential = MdocsCredential(
        credentialData = JsonObject(mapOf()),
        signed = "invalid_hex_string_mock_test",
        docType = "org.iso.18013.5.1.mDL"
    )
    private val mockExtractor = mock<Function1<MdocsCredential, MobileSecurityObject?>>()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @AfterTest
    fun tearDown() {
        MdocsCredential.msoExtractionTestHook = null
    }

    @Test
    fun `json serialization should include mso field`() {
        every { mockExtractor.invoke(any()) } returns dummyMso
        MdocsCredential.msoExtractionTestHook = mockExtractor
        val encodedJson = json.encodeToString(credential)
        println(encodedJson)

        val result = json.parseToJsonElement(encodedJson).jsonObject

        assertField(result, "credentialData")
        assertField(result, "docType")
        assertField(result, "format")
        assertField(result, "mso")
        assertContent(result, "credentialData")
        assertContent(result, "docType")
        assertContent(result, "format")
        assertContent(result, "mso")
        assertTrue(result["format"] is JsonPrimitive)
        assertEquals(expected = MSO_MDOC_FORMAT, actual = result["format"]!!.jsonPrimitive.content)

    }

    private fun createDummyMso(): MobileSecurityObject = MobileSecurityObject(
        version = "1.0",
        digestAlgorithm = "SHA-256",
        valueDigests = mapOf("namespace" to dummyDigestList),
        deviceKeyInfo = dummyDeviceKeyInfo,
        docType = "org.iso.18013.5.1.mDL",
        validityInfo = dummyValidityInfo,
    )

    private fun assertField(result: JsonObject, name: String) =
        assertTrue(result.containsKey(name), "JSON must contain '$name' field")

    private fun assertContent(result: JsonObject, name: String) =
        assertNotNull(result[name], "'$name' field should not be null")
}
