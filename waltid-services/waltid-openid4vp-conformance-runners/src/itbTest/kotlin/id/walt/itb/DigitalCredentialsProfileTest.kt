package id.walt.itb

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.URLBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DigitalCredentialsProfileTest {
    @Test
    fun `CS07 shared wallet resolves the signed Digital Credentials API protocol`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", key).did
        val encryptionKey = JsonObject(
            Json.parseToJsonElement(ECKeyGenerator(Curve.P_256).keyID("response-key").generate()
                .toPublicJWK().toJSONString()).jsonObject + mapOf(
                "alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc"),
            )
        )
        val request = buildJsonObject {
            put("client_id", "decentralized_identifier:$did")
            put("nonce", "dc-api-nonce")
            put("aud", "https://self-issued.me/v2")
            put("response_type", "vp_token")
            put("response_mode", "dc_api.jwt")
            put("expected_origins", JsonArray(listOf(JsonPrimitive(WalletFixtures.VERIFIER))))
            put("dcql_query", WalletFixtures.query())
            put("client_metadata", buildJsonObject {
                put("jwks", buildJsonObject { put("keys", JsonArray(listOf(encryptionKey))) })
                put("encrypted_response_enc_values_supported", JsonArray(listOf(JsonPrimitive("A256GCM"))))
                put("vp_formats_supported", buildJsonObject {
                    put("dc+sd-jwt", buildJsonObject { put("sd-jwt_alg_values", JsonArray(listOf(JsonPrimitive("ES256")))) })
                })
            })
        }
        val jwt = key.signJws(request.toString().encodeToByteArray(), buildJsonObject {
            put("typ", "oauth-authz-req+jwt")
            put("kid", "$did#0")
        })
        // Establish that this is an authentic Request Object before exercising DC API dispatch.
        val authenticated = AuthorizationRequestResolver.resolve(
            requestUrl = URLBuilder("openid4vp://authorize").apply {
                parameters.append("client_id", "decentralized_identifier:$did")
                parameters.append("request", jwt)
            }.build(),
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            fetchRequestUri = { _, _ -> error("Inline request must not use HTTP") },
        )
        assertEquals("dc-api-nonce", authenticated.authorizationRequest.nonce)
        // This checks shared signed-protocol resolution only. Native registration, X.509 profile
        // trust and encrypted response delivery require their own qualification.
        val resolved = DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-signed",
            data = buildJsonObject { put("request", jwt) },
            origin = WalletFixtures.VERIFIER,
        )
        assertEquals("dc-api-nonce", resolved.authorizationRequest.nonce, "WAL-896 / Identity #2141")
        assertEquals("origin:${WalletFixtures.VERIFIER}", resolved.holderBindingAudience)
    }
}
