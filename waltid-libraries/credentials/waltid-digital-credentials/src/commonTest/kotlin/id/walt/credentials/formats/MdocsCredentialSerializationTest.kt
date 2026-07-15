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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import kotlin.time.Clock


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

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `holder key is read from the mobile security object`() = runTest {
        val x = "2Z3gxK7IatHaxPWLYkBYn1XS0wKdL7fMQQuF_nGw2Kw"
        val y = "41CM3oYupV2TNid0xDbESe0bzKWVNu0LU8kKQS47jUI"
        every { mockExtractor.invoke(any()) } returns createDummyMso(
            CoseKey(
                kty = Cose.KeyTypes.EC2,
                crv = Cose.EllipticCurves.P_256,
                x = Base64.UrlSafe.decode("$x="),
                y = Base64.UrlSafe.decode("$y="),
            )
        )
        MdocsCredential.msoExtractionTestHook = mockExtractor

        val holderKey = credential.getHolderKey().getPublicKey().exportJWKObject()

        assertEquals(x, holderKey["x"]?.jsonPrimitive?.content)
        assertEquals(y, holderKey["y"]?.jsonPrimitive?.content)
    }

    private fun createDummyMso(deviceKey: CoseKey = dummyCoseKey): MobileSecurityObject = MobileSecurityObject(
        version = "1.0",
        digestAlgorithm = "SHA-256",
        valueDigests = mapOf("namespace" to dummyDigestList),
        deviceKeyInfo = DeviceKeyInfo(deviceKey),
        docType = "org.iso.18013.5.1.mDL",
        validityInfo = dummyValidityInfo,
    )

    private fun assertField(result: JsonObject, name: String) =
        assertTrue(result.containsKey(name), "JSON must contain '$name' field")

    private fun assertContent(result: JsonObject, name: String) =
        assertNotNull(result[name], "'$name' field should not be null")
}
