package id.walt.openid4vci.handlers.credential

import id.walt.cose.verify
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class MdocPortraitCaptureCompatibilityTest {
    @Test
    fun `saved full-date mappings issue timestamps without changing stored inputs`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val issuer = key("portrait-issuer")
        val holder = key("portrait-holder")
        val savedMapping = """{"entriesConfigMap":{"portrait_capture_date":{"type":"string","conversionType":"stringToFullDate"},"birth_date":{"type":"string","conversionType":"stringToFullDate"}}}"""
        val mapping = Json.decodeFromString<JsonObjectToCborMappingConfig>(savedMapping)
        for ((input, expected) in listOf(
            "2024-02-29" to "2024-02-29T00:00:00Z",
            "2024-02-29T13:34:56.987+01:00" to "2024-02-29T12:34:56Z",
        )) {
            val data = buildJsonObject { putJsonObject("org.iso.18013.5.1") {
                put("portrait_capture_date", input); put("birth_date", "2000-01-01")
            } }
            val credential = MdocCredentialSigner.generateMdocCredential(
                credentialRequest = DefaultCredentialRequest(
                    client = DefaultClient("test", emptyList(), emptySet(), emptySet()),
                    credentialIdentifier = null, credentialConfigurationId = "mdl", proofs = null, credentialResponseEncryption = null,
                ),
                credentialData = data, issuerKey = issuer, signatureAlgorithm = -7,
                issuerCertificate = listOf(CoseCertificate(byteArrayOf(1, 2, 3))),
                docType = "org.iso.18013.5.1.mDL",
                mDocNameSpacesDataMappingConfig = mapOf("org.iso.18013.5.1" to mapping),
                verifiedProof = VerifiedCredentialProof(
                    proofType = "jwt", jwt = "", algorithm = "ES256", header = buildJsonObject {}, payload = buildJsonObject {},
                    holderKey = holder, holderKid = null, holderDid = null, nonce = null,
                ),
            )
            val signed = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode())
            assertTrue(signed.issuerAuth.verify(issuer, -7))
            val items = signed.namespaces!!["org.iso.18013.5.1"]!!.entries.associate { it.value.elementIdentifier to it.value.elementValue }
            assertContentEquals(byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("portrait_capture_date")))
            assertContentEquals(byteArrayOf(0xd9.toByte(), 0x03, 0xec.toByte(), 0x6a) + "2000-01-01".encodeToByteArray(),
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("birth_date")))
            assertEquals(input, data["org.iso.18013.5.1"]!!.jsonObject["portrait_capture_date"]!!.jsonPrimitive.content)
            assertEquals(mapping, Json.decodeFromString<JsonObjectToCborMappingConfig>(savedMapping))
        }
    }
}
