@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ResponseEncryptionInteropTest {
    @Test
    fun `Nimbus decrypts production direct post response`() = runTest {
        val recipient = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("response-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val exportedPublicKey = recipient.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val verifierJwk = JsonObject(
            Jwk.parse(exportedPublicKey) + mapOf(
                "kid" to JsonPrimitive(recipient.id.value),
                "use" to JsonPrimitive("enc"),
                "alg" to JsonPrimitive("ECDH-ES"),
            )
        )
        val request = AuthorizationRequest(
            clientId = "x509_hash:test",
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientMetadata = ClientMetadata(
                jwks = ClientMetadata.Jwks(listOf(verifierJwk)),
                encryptedResponseEncValuesSupported = listOf("A256GCM"),
            ),
        )
        val config = requireNotNull(ResponseEncryption.resolveCrypto2(request))
        val payload = buildJsonObject { put("state", "state") }

        val compactJwe = WalletPresentFunctionality2.encryptDirectPostResponse(
            payload = payload,
            recipientPublicKey = config.recipientPublicKey,
            contentEncryption = config.contentEncryption,
        )
        val nimbusJwe = JWEObject.parse(compactJwe).apply {
            val privateJwk = recipient.storedKey.material as EncodedKey.Jwk
            decrypt(ECDHDecrypter(ECKey.parse(privateJwk.data.toByteArray().decodeToString())))
        }

        assertFalse(config.recipientPublicKey.privateMaterial)
        assertEquals(recipient.id.value, nimbusJwe.header.keyID)
        assertEquals(payload, Json.parseToJsonElement(nimbusJwe.payload.toString()))
    }
}
