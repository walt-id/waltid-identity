package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.verify
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborNull
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class MdocPortraitCaptureCompatibilityTest {
    private val namespace = "org.iso.18013.5.1"
    // Covers no mapping, the stored legacy mapping, the new mapping, and a conflicting conversion.
    private val conversions = listOf(null, "stringToFullDate", "stringToTDate", "base64StringToByteString")

    @Test
    fun `portrait normalization takes precedence without rewriting inputs or mappings`() = runTest {
        for (conversion in conversions) {
            val mapping = savedMapping(conversion)
            val savedJson = Json.encodeToString(mapping)
            for ((input, expected) in listOf(
                "2024-02-29" to "2024-02-29T00:00:00Z",
                "2024-02-29T13:34:56.987+01:00" to "2024-02-29T12:34:56Z",
            )) {
                val data = buildJsonObject { putJsonObject(namespace) {
                    put("portrait_capture_date", input)
                    put("birth_date", "2000-01-01")
                } }
                val items = issue(data, mapping).items(namespace)
                assertContentEquals(
                    byteArrayOf(0xc0.toByte(), 0x74) + expected.encodeToByteArray(),
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("portrait_capture_date")),
                )
                assertContentEquals(
                    byteArrayOf(0xd9.toByte(), 0x03, 0xec.toByte(), 0x6a) + "2000-01-01".encodeToByteArray(),
                    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), items.getValue("birth_date")),
                )
                assertEquals(input, data.getValue(namespace).jsonObject.getValue("portrait_capture_date").jsonPrimitive.content)
                assertEquals(savedJson, Json.encodeToString(mapping))
            }
        }
    }

    @Test
    fun `absent and explicit null portraits are omitted before any profile conversion`() = runTest {
        for (conversion in conversions) {
            for (includeNull in listOf(false, true)) {
                val data = buildJsonObject {
                    putJsonObject(namespace) {
                        put("given_name", "Example")
                        put("name_at_birth", JsonNull)
                        if (includeNull) put("portrait_capture_date", JsonNull)
                    }
                    putJsonObject("custom.example") { put("portrait_capture_date", JsonNull) }
                }
                val issued = issue(data, savedMapping(conversion))
                assertFalse("portrait_capture_date" in issued.items(namespace))
                assertIs<CborNull>(issued.items(namespace).getValue("name_at_birth"))
                assertIs<CborNull>(issued.items("custom.example").getValue("portrait_capture_date"))
                assertEquals(includeNull, "portrait_capture_date" in data.getValue(namespace).jsonObject)
            }
        }
    }

    @Test
    fun `standard field semantics follow the namespace across document types`() = runTest {
        for (docType in listOf("org.iso.18013.5.1.mDL", "com.google.wallet.idcard.1")) {
            val issued = issue(buildJsonObject {
                putJsonObject(namespace) { put("portrait_capture_date", "2024-02-29") }
                putJsonObject("custom.example") { put("portrait_capture_date", "custom-value") }
            }, docType = docType)
            assertContentEquals(
                byteArrayOf(0xc0.toByte(), 0x74) + "2024-02-29T00:00:00Z".encodeToByteArray(),
                coseCompliantCbor.encodeToByteArray(CborElement.serializer(), issued.items(namespace).getValue("portrait_capture_date")),
            )
            assertEquals("custom-value", assertIs<CborString>(issued.items("custom.example").getValue("portrait_capture_date")).value)
        }
    }

    @Test
    fun `malformed non-null portrait values still fail issuance`() = runTest {
        for (value in listOf(JsonPrimitive("2023-02-29"), JsonPrimitive(17))) {
            assertFailsWith<IllegalArgumentException> {
                issue(buildJsonObject { putJsonObject(namespace) { put("portrait_capture_date", value) } }, savedMapping("stringToFullDate"))
            }
        }
    }

    private fun savedMapping(conversion: String?): Map<String, JsonObjectToCborMappingConfig>? = conversion?.let {
        mapOf(namespace to Json.decodeFromString<JsonObjectToCborMappingConfig>(
            """{"entriesConfigMap":{"portrait_capture_date":{"type":"string","conversionType":"$it"},"birth_date":{"type":"string","conversionType":"stringToFullDate"}}}""",
        ))
    }

    private fun IssuerSigned.items(namespace: String): Map<String, CborElement> =
        namespaces!!.getValue(namespace).entries.associate { it.value.elementIdentifier to it.value.elementValue }

    private suspend fun issue(
        data: JsonObject,
        mapping: Map<String, JsonObjectToCborMappingConfig>? = null,
        docType: String = "org.iso.18013.5.1.mDL",
    ): IssuerSigned {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            id = KeyId(id), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val issuer = key("portrait-issuer")
        val holder = key("portrait-holder")
        val credential = MdocCredentialSigner.generateMdocCredential(
            credentialRequest = DefaultCredentialRequest(
                client = DefaultClient("test", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = null, credentialConfigurationId = "mdl", proofs = null, credentialResponseEncryption = null,
            ),
            credentialData = data, issuerKey = issuer, signatureAlgorithm = -7,
            issuerCertificate = listOf(CoseCertificate(byteArrayOf(1, 2, 3))),
            docType = docType,
            mDocNameSpacesDataMappingConfig = mapping,
            valueMappingFunction = { type, fieldNamespace, element, value ->
                assertFalse(fieldNamespace == namespace && element == "portrait_capture_date",
                    "Standard portrait values must not reach a custom fallback callback")
                MdocIssuer.defaultSchemalessMappingFunction(type, fieldNamespace, element, value)
            },
            verifiedProof = VerifiedCredentialProof(
                proofType = "jwt", jwt = "", algorithm = "ES256", header = buildJsonObject {}, payload = buildJsonObject {},
                holderKey = holder, holderKid = null, holderDid = null, nonce = null,
            ),
        )
        return coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode()).also {
            assertTrue(it.issuerAuth.verify(issuer, -7))
        }
    }
}
