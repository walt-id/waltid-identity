package id.walt.sdjwt

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class SDJwtTestIOS {
    private val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

    @Test
    fun testSignJwt() {
        val cryptoProvider = HMACJWTCryptoProvider("HS256", sharedSecret.encodeToByteArray())

        val originalSet = mutableMapOf<String, JsonElement> (
            "sub" to  JsonPrimitive("123"),
            "aud" to JsonPrimitive("456")
        )

        val originalClaimsSet = JsonObject(originalSet)

        // Create undisclosed claims set, by removing e.g. subject property from original claims set
        val undisclosedSet = mutableMapOf<String, JsonElement> (
            "aud" to JsonPrimitive("456")
        )

        val undisclosedClaimsSet = JsonObject(undisclosedSet)

        // Create SD payload by comparing original claims set with undisclosed claims set
        val sdPayload = SDPayload.createSDPayload(originalClaimsSet, undisclosedClaimsSet)

        // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
        val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)
        // Print SD-JWT
        println(sdJwt)

        assertFalse(actual = sdJwt.undisclosedPayload.containsKey("sub"))
        assertContains(map = sdJwt.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertContains(map = sdJwt.undisclosedPayload, key = "aud")
        assertEquals(expected = 1, actual = sdJwt.disclosures.size)
        assertEquals(expected = "sub", actual = sdJwt.digestedDisclosures[sdJwt.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray[0].jsonPrimitive.content]!!.key)
        assertEquals(expected = originalClaimsSet, actual = Json.parseToJsonElement(sdJwt.fullPayload.toString()).jsonObject)

        assertTrue(actual = assersdJwt.verify(cryptoProvider).verified)
    }

    @Test
    fun presentSDJwt() {
        // parse previously created SD-JWT
        val sdJwt =
            SDJwt.parse("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0")

        // present without disclosing SD fields
        val presentedUndisclosedJwt = sdJwt.present(discloseAll = false)
        println(presentedUndisclosedJwt)

        // present disclosing all SD fields
        val presentedDisclosedJwt = sdJwt.present(discloseAll = true)
        println(presentedDisclosedJwt)

        // present disclosing selective fields, using SDMap
        val presentedSelectiveJwt = sdJwt.present(SDMapBuilder().addField("sub", true).build())
        println(presentedSelectiveJwt)

        // present disclosing fields, using JSON paths
        val presentedSelectiveJwt2 = sdJwt.present(SDMap.generateSDMap(listOf("sub")))
        println(presentedSelectiveJwt2)

    }

    @Test
    fun parseAndVerify() {
        // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
        val cryptoProvider = HMACJWTCryptoProvider("HS256", sharedSecret.encodeToByteArray())
        val undisclosedJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~"

        // verify and parse presented SD-JWT with all fields undisclosed, throws Exception if verification fails!
        val parseAndVerifyResult = SDJwt.verifyAndParse(undisclosedJwt, cryptoProvider)

        // print full payload with disclosed fields only
        println("Undisclosed JWT payload:")
        println(parseAndVerifyResult.sdJwt.fullPayload.toString())

        // alternatively parse and verify in 2 steps:
        val parsedUndisclosedJwt = SDJwt.parse(undisclosedJwt)
        val isValid = parsedUndisclosedJwt.verify(cryptoProvider).verified
        println("Undisclosed SD-JWT verified: $isValid")

        val parsedDisclosedJwtVerifyResult = SDJwt.verifyAndParse(
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~",
            cryptoProvider
        )
        // print full payload with disclosed fields
        println("Disclosed JWT payload:")
        println(parsedDisclosedJwtVerifyResult.sdJwt.fullPayload.toString())
    }
}