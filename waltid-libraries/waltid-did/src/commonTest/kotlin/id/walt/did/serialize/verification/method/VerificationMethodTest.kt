package id.walt.did.serialize.verification.method

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.models.verification.method.VerificationMaterialType
import id.walt.did.dids.document.models.verification.method.VerificationMethod
import id.walt.did.dids.document.models.verification.method.VerificationMethodType
import id.walt.did.utils.JsonCanonicalization
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VerificationMethodTest {

    private val keyString = """{"alg":"EdDSA","crv":"Ed25519","kid":"151df6ec01714883b812f26f2d63e584","kty":"OKP","use":"sig","x":"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM"}"""

    @Test
    fun testSerialization() = runTest {
        val key = JWKKey.importJWK(keyString).getOrThrow()
        val method = VerificationMethod(
            id = "some-id",
            type = VerificationMethodType.JsonWebKey2020,
            controller = "some-controller",
            material = Pair(VerificationMaterialType.PublicKeyJwk, key.exportJWKObject()),
        )
        val methodJsonString = "{\"id\":\"some-id\",\"type\":\"JsonWebKey2020\",\"controller\":\"some-controller\",\"publicKeyJwk\":{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}}"
        val canonicalMethodJsonString = JsonCanonicalization
            .getCanonicalString("{\"id\":\"some-id\",\"type\":\"JsonWebKey2020\",\"controller\":\"some-controller\",\"publicKeyJwk\":{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}}")
        //encode
        assertEquals(
            expected = canonicalMethodJsonString,
            actual = JsonCanonicalization.getCanonicalString(Json.encodeToString(method)),
        )
        //decode
        assertEquals(
            expected = method,
            actual = Json.decodeFromString<VerificationMethod>(methodJsonString)
        )
    }
}