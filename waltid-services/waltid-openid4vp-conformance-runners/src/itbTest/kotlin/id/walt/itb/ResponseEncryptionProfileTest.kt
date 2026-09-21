package id.walt.itb

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.waltid.openid4vp.wallet.DcApiRequestProtocol
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.ResolvedDcApiRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ResponseEncryptionProfileTest {
    private val recipient = ECKeyGenerator(Curve.P_256).keyID("itb-response-key").generate()
    private val publicJwk = JsonObject(
        Json.parseToJsonElement(recipient.toPublicJWK().toJSONString()).jsonObject + mapOf(
            "alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc"),
        )
    )

    @Test
    fun `CS07 encrypted response can be decrypted by the intended independent verifier`() = runTest {
        // Exercise response encryption independently of signed-request resolution, so WAL-896's
        // missing signed protocol cannot hide a regression in already-supported encryption.
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = WalletFixtures.VERIFIER,
                authorizationRequest = request(publicJwk),
            ),
            vpToken = """{"credential":["synthetic-presentation"]}""",
        )
        assertEquals(setOf("response"), response.data.keys)
        val encrypted = JWEObject.parse(response.data.getValue("response").jsonPrimitive.content)
        assertEquals("ECDH-ES", encrypted.header.algorithm.name)
        assertEquals("A256GCM", encrypted.header.encryptionMethod.name)
        assertEquals(recipient.keyID, encrypted.header.keyID)
        encrypted.decrypt(ECDHDecrypter(recipient))
        assertEquals(
            "synthetic-presentation",
            Json.parseToJsonElement(encrypted.payload.toString()).jsonObject
                .getValue("vp_token").jsonObject.getValue("credential").jsonArray.single().jsonPrimitive.content,
        )
    }

    @Test
    fun `CS07 rejects response encryption keys without the required algorithm`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            ResponseEncryption.resolveCrypto2(request(JsonObject(publicJwk - "alg")))
        }
        assertTrue(error.message.orEmpty().contains("alg=ECDH-ES"))
    }

    private fun request(jwk: JsonObject): AuthorizationRequest = Json.decodeFromJsonElement(buildJsonObject {
        put("nonce", "response-encryption-nonce")
        put("response_type", "vp_token")
        put("response_mode", "dc_api.jwt")
        put("dcql_query", WalletFixtures.query())
        put("client_metadata", buildJsonObject {
            put("jwks", buildJsonObject { put("keys", JsonArray(listOf(jwk))) })
            put("encrypted_response_enc_values_supported", JsonArray(listOf(JsonPrimitive("A256GCM"))))
        })
    })
}
