package id.waltid.openid4vci.wallet.dpop

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DPoPProofBuilderTest {
    @Test
    fun createsFreshBoundProofsWithoutQueryOrFragmentInHtu() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val builder = DPoPProofBuilder()
        val first = builder.buildProof(
            key = key,
            httpMethod = "post",
            targetUri = "https://issuer.example:8443/token?secret=value#ignored",
            accessToken = "access-token",
            nonce = "server-nonce",
            supportedAlgorithms = setOf("ES256"),
        )
        val second = builder.buildProof(
            key = key,
            httpMethod = "POST",
            targetUri = "https://issuer.example:8443/token",
            accessToken = "access-token",
            supportedAlgorithms = setOf("ES256"),
        )

        val header = jwtPart(first, 0)
        val payload = jwtPart(first, 1)
        assertEquals("dpop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertNotNull(header["jwk"])
        assertEquals("POST", payload["htm"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example:8443/token", payload["htu"]?.jsonPrimitive?.content)
        assertEquals("server-nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertNotNull(payload["ath"])
        assertNotEquals(payload["jti"], jwtPart(second, 1)["jti"])
    }

    @Test
    fun rejectsUnsupportedHolderAlgorithm() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        assertFailsWith<IllegalArgumentException> {
            DPoPProofBuilder().buildProof(
                key = key,
                httpMethod = "POST",
                targetUri = "https://issuer.example/token",
                supportedAlgorithms = setOf("EdDSA"),
            )
        }
    }

    private fun jwtPart(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split('.')[index].decodeFromBase64Url().decodeToString()).jsonObject
}
