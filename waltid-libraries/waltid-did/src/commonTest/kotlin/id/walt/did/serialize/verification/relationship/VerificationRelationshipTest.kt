package id.walt.did.serialize.verification.relationship

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.models.verification.method.VerificationMaterialType
import id.walt.did.dids.document.models.verification.method.VerificationMethod
import id.walt.did.dids.document.models.verification.method.VerificationMethodType
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationship
import id.walt.did.utils.JsonCanonicalization
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals


class VerificationRelationshipTest {

    @Test
    fun testVerificationRelationshipId() {
        val verRel = VerificationRelationship.buildFromId(
            id = "some-id"
        )
        val verRelJsonString = """"some-id""""
        //encode
        assertEquals(
            expected = verRelJsonString,
            actual = Json.encodeToString(verRel),
        )
        //decode
        assertEquals(
            expected = verRel,
            actual = Json.decodeFromString(verRelJsonString),
        )
    }

    @Test
    fun testVerificationRelationshipMethod() = runTest {
        val keyString = """{"alg":"EdDSA","crv":"Ed25519","kid":"151df6ec01714883b812f26f2d63e584","kty":"OKP","use":"sig","x":"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM"}"""
        val key = JWKKey.importJWK(keyString).getOrThrow()
        val method = VerificationMethod(
            id = "some-id",
            type = VerificationMethodType.JsonWebKey2020,
            controller = "some-controller",
            material = Pair(VerificationMaterialType.PublicKeyJwk, key.exportJWKObject()),
        )
        val verRel = VerificationRelationship.buildFromVerificationMethod(
            method,
        )
        val verRelJsonString = "{\"id\":\"some-id\",\"type\":\"JsonWebKey2020\",\"controller\":\"some-controller\",\"publicKeyJwk\":{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}}"
        val canonicalVerRelJsonString = JsonCanonicalization
            .getCanonicalString("{\"id\":\"some-id\",\"type\":\"JsonWebKey2020\",\"controller\":\"some-controller\",\"publicKeyJwk\":{\"alg\":\"EdDSA\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"kty\":\"OKP\",\"use\":\"sig\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\"}}")
        //encode
        assertEquals(
            expected = canonicalVerRelJsonString,
            actual = JsonCanonicalization.getCanonicalString(Json.encodeToString(verRel)),
        )
        //decode
        assertEquals(
            expected = verRel,
            actual = Json.decodeFromString<VerificationRelationship>(verRelJsonString)
        )
    }
}
