package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ResponseEncryptionHandlerJvmTest {

    @Test
    fun encryptResponse_roundTripsPayload() = runTest {
        val privateKey = JWKKey.importJWK(
            """{"kty":"EC","crv":"P-256","kid":"round-trip-key","d":"QN9Y3k_3Hy2OV0C5Pmez_ObEXJKcXonnMg3xTpcLOAg","x":"eTT2WdzlmOWBItdgSmsqB1_BP69wfuwOe1IYvaY1WdI","y":"wbOu3GP02JiOVIRQ_ufWLRNOmDB6seYAabCmsGBfr_4"}"""
        ).getOrThrow()
        val encryptionJwk = Json.parseToJsonElement(
            """{"kty":"EC","crv":"P-256","kid":"round-trip-key","use":"enc","alg":"ECDH-ES","x":"eTT2WdzlmOWBItdgSmsqB1_BP69wfuwOe1IYvaY1WdI","y":"wbOu3GP02JiOVIRQ_ufWLRNOmDB6seYAabCmsGBfr_4"}"""
        ).jsonObject
        val request = AuthorizationRequest(
            responseUri = "https://verifier.example/response",
            responseMode = OpenID4VPResponseMode.DIRECT_POST_JWT,
            clientMetadata = ClientMetadata(
                jwks = ClientMetadata.Jwks(listOf(encryptionJwk)),
                encryptedResponseEncValuesSupported = listOf("A256GCM"),
            ),
        )
        val config = ResponseEncryptionHandler.extractEncryptionConfig(request).getOrThrow()
        assertNotNull(config)
        val payload = buildJsonObject {
            put("vp_token", JsonPrimitive("credential-presentation"))
            put("state", JsonPrimitive("state-123"))
        }

        val jwe = ResponseEncryptionHandler.encryptResponse(payload, config)
        val decrypted = privateKey.decryptJwe(jwe).decodeToString()

        assertEquals(payload, Json.parseToJsonElement(decrypted))
    }
}
