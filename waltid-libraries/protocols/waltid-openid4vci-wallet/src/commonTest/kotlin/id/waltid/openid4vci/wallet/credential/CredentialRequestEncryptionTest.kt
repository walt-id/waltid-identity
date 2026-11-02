package id.waltid.openid4vci.wallet.credential

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialRequestEncryption
import id.walt.openid4vci.metadata.issuer.CredentialResponseEncryption
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import id.walt.openid4vci.responses.credential.encryption.Crypto2JweCredentialResponseEncryptor
import io.ktor.http.ContentType
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialRequestEncryptionTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `missing encryption metadata keeps the credential request in JSON`() = runTest {
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }

        val withoutMetadata = CredentialEncryption.prepare(payload, metadata = null)
        val withoutEncryption = CredentialEncryption.prepare(payload, issuerMetadata(encryption = null))

        for (prepared in listOf(withoutMetadata, withoutEncryption)) {
            assertEquals(ContentType.Application.Json, prepared.contentType)
            assertEquals(payload.toString(), prepared.body)
            assertNull(prepared.responseDecryptionKey)
        }
    }

    @Test
    fun `advertised wallet profile encrypts the request and decrypts the issuer response`() = runTest {
        val issuerKey = agreementKey("issuer-encryption")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val prepared = CredentialEncryption.prepare(payload, issuerMetadata(encryption = requestEncryption(issuerKey)))
        val responseKey = requireNotNull(prepared.responseDecryptionKey)

        assertEquals("application", prepared.contentType.contentType)
        assertEquals("jwt", prepared.contentType.contentSubtype)
        val header = jweHeader(prepared.body)
        assertEquals(CredentialEncryptionProfile.ALG_ECDH_ES, header.getValue("alg").jsonPrimitive.content)
        assertEquals(CredentialEncryptionProfile.ENC_A128GCM, header.getValue("enc").jsonPrimitive.content)
        assertEquals(issuerKey.id.value, header.getValue("kid").jsonPrimitive.content)

        val request = Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(prepared.body)
        assertEquals("identity", request.getValue("credential_configuration_id").jsonPrimitive.content)
        val responsePayload = buildJsonObject {
            put("credentials", buildJsonArray {
                add(buildJsonObject { put("credential", "issued-credential") })
            })
        }
        val responseJwe = Crypto2JweCredentialResponseEncryptor.encrypt(
            payload = responsePayload,
            encryption = CredentialResponseEncryptionParameters.fromJsonObject(
                request.getValue("credential_response_encryption").jsonObject,
            ),
        )

        assertEquals(responsePayload, json.parseToJsonElement(CredentialEncryption.decryptResponse(responseJwe, responseKey)).jsonObject)
        val tampered = responseJwe.split('.').toMutableList()
        tampered[4] = (if (tampered[4].first() == 'A') "B" else "A") + tampered[4].drop(1)
        assertFails { CredentialEncryption.decryptResponse(tampered.joinToString("."), responseKey) }
    }

    @Test
    fun `unsupported optional encryption stays plaintext`() = runTest {
        val issuerKey = agreementKey("issuer-encryption")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val metadata = issuerMetadata(encryption = requestEncryption(issuerKey)).copy(
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf(CredentialEncryptionProfile.ALG_ECDH_ES),
                encValuesSupported = setOf("A256GCM"),
                encryptionRequired = false,
            ),
        )

        val prepared = CredentialEncryption.prepare(payload, metadata)

        assertEquals(ContentType.Application.Json, prepared.contentType)
        assertNull(prepared.responseDecryptionKey)
    }

    @Test
    fun `required encryption outside the wallet profile is rejected before a request is sent`() = runTest {
        val issuerKey = agreementKey("issuer-encryption")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val unsupportedResponse = issuerMetadata(encryption = requestEncryption(issuerKey)).copy(
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf("RSA-OAEP"),
                encValuesSupported = setOf("A256GCM"),
                encryptionRequired = true,
            ),
        )
        val missingKey = issuerMetadata(encryption = null).copy(
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = buildJsonObject { put("keys", buildJsonArray {}) },
                encValuesSupported = setOf(CredentialEncryptionProfile.ENC_A128GCM),
                encryptionRequired = true,
            ),
        )

        val unsupported = assertFailsWith<IllegalArgumentException> { CredentialEncryption.prepare(payload, unsupportedResponse) }
        assertTrue(unsupported.message.orEmpty().contains("advertised algorithms"))
        val absent = assertFailsWith<IllegalArgumentException> { CredentialEncryption.prepare(payload, missingKey) }
        assertTrue(absent.message.orEmpty().contains("no supported EC P-256 key"))
    }

    @Test
    fun `optional unsupported request encryption stays plaintext when the response profile is supported`() = runTest {
        val issuerKey = agreementKey("issuer-request-enc")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val metadata = splitEncryptionMetadata(issuerKey, requestEnc = "A256GCM", requestRequired = false)

        val prepared = CredentialEncryption.prepare(payload, metadata)

        assertEquals(ContentType.Application.Json, prepared.contentType)
        assertEquals(payload.toString(), prepared.body)
        assertNull(prepared.responseDecryptionKey)
    }

    @Test
    fun `required unsupported request encryption fails before a request is sent`() = runTest {
        val issuerKey = agreementKey("issuer-request-enc-required")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val metadata = splitEncryptionMetadata(issuerKey, requestEnc = "A256GCM", requestRequired = true)

        val failure = assertFailsWith<IllegalArgumentException> { CredentialEncryption.prepare(payload, metadata) }

        assertTrue(failure.message.orEmpty().contains("credential_request_encryption.enc_values_supported"))
    }

    @Test
    fun `required request encryption omits an unsupported response encryption parameter`() = runTest {
        val issuerKey = agreementKey("issuer-request-required")
        val payload = buildJsonObject { put("credential_configuration_id", "identity") }
        val metadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example",
            credentialConfigurationsSupported = mapOf(
                "identity" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"),
            ),
            credentialRequestEncryption = requestEncryption(issuerKey).copy(encryptionRequired = true),
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf(CredentialEncryptionProfile.ALG_ECDH_ES),
                encValuesSupported = setOf("A256GCM"),
                encryptionRequired = false,
            ),
        )

        val prepared = CredentialEncryption.prepare(payload, metadata)

        assertEquals("jwt", prepared.contentType.contentSubtype)
        assertNull(prepared.responseDecryptionKey)
        val request = Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(prepared.body)
        assertEquals(null, request["credential_response_encryption"])
    }

    @Test
    fun `application jwt comparison ignores capitalization`() {
        assertTrue(ContentType.parse("Application/JWT").isApplicationJwt())
        assertTrue(ContentType.parse("application/jwt; charset=UTF-8").isApplicationJwt())
        assertEquals(false, ContentType.Application.Json.isApplicationJwt())
    }

    private suspend fun splitEncryptionMetadata(issuerKey: Key, requestEnc: String, requestRequired: Boolean) =
        CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example",
            credentialConfigurationsSupported = mapOf(
                "identity" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"),
            ),
            credentialRequestEncryption = requestEncryption(issuerKey).copy(
                encValuesSupported = setOf(requestEnc),
                encryptionRequired = requestRequired,
            ),
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf(CredentialEncryptionProfile.ALG_ECDH_ES),
                encValuesSupported = setOf(CredentialEncryptionProfile.ENC_A128GCM),
                encryptionRequired = false,
            ),
        )

    private suspend fun issuerMetadata(encryption: CredentialRequestEncryption?): CredentialIssuerMetadata =
        CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example",
            credentialConfigurationsSupported = mapOf(
                "identity" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC, vct = "identity"),
            ),
            credentialRequestEncryption = encryption,
        )

    private suspend fun requestEncryption(issuerKey: Key) = CredentialRequestEncryption(
        jwks = buildJsonObject { put("keys", buildJsonArray { add(issuerKey.metadataJwk()) }) },
        encValuesSupported = setOf(CredentialEncryptionProfile.ENC_A128GCM),
        encryptionRequired = false,
    )

    private suspend fun agreementKey(id: String): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        ),
    )

    private suspend fun Key.metadataJwk(): JsonObject = buildJsonObject {
        Jwk.parse(exportPublicJwk()).forEach { (name, value) -> put(name, value) }
        put("kid", JsonPrimitive(id.value))
        put("alg", JsonPrimitive(CredentialEncryptionProfile.ALG_ECDH_ES))
        put("use", JsonPrimitive(CredentialEncryptionProfile.KEY_USE_ENC))
    }

    private fun jweHeader(compactJwe: String): JsonObject =
        Json.parseToJsonElement(Base64.UrlSafe.decode(compactJwe.substringBefore('.')).decodeToString()).jsonObject
}
