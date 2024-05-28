package id.walt.sdjwt

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.khronos.webgl.Uint8Array
import kotlin.test.*

class SDJwtTestJS {

    // Generate shared secret for HMAC crypto algorithm
    private val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun test1() = GlobalScope.promise {
        val cryptoProvider = SimpleAsyncJWTCryptoProvider(
            "HS256",
            Uint8Array(sharedSecret.encodeToByteArray().toTypedArray()), null
        )
        // Create original JWT claims set, using nimbusds claims set builder
        val originalClaimsSet = js("{ \"sub\": \"123\", \"aud\": \"456\" }")

        // Create undisclosed claims set, by removing e.g. subject property from original claims set
        val undisclosedClaimsSet = js("{ \"aud\": \"456\" }")

        // Create SD payload by comparing original claims set with undisclosed claims set
        val sdPayload = SDPayloadBuilder(originalClaimsSet).buildForUndisclosedPayload(undisclosedClaimsSet)

        // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
        val sdJwt = SDJwtJS.signAsync(sdPayload, cryptoProvider).await()
        // Print SD-JWT
        println(sdJwt)

        assertFalse(actual = sdJwt.undisclosedPayload.containsKey("sub"))
        assertContains(map = sdJwt.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertContains(map = sdJwt.undisclosedPayload, key = "aud")
        assertEquals(expected = 1, actual = sdJwt.disclosures.size)
        assertEquals(expected = "sub", actual = sdJwt.digestedDisclosures[sdJwt.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray[0].jsonPrimitive.content]!!.key)
        println("BLA")
        assertEquals(expected = Json.parseToJsonElement(JSON.stringify(originalClaimsSet)).jsonObject, actual = sdJwt.fullPayload)
        println("ASDASD")

        assertTrue(actual = sdJwt.verifyAsync(cryptoProvider).verified)
        assertFalse(actual = SDJwt.parse("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiI0NTciLCJfc2QiOlsibTcyQ0tyVHhYckhlWUJSQTFBVFQ1S0t4NGdFWExlOVhqVFROakdRWkVQNCJdfQ.Tltz2SGxmdIpD_ny1XSTn89rQSmYsl9EcsXxsfJE0wo")
            .verifyAsync(cryptoProvider).verified)
    }

    @Test
    fun testSdMap() {
        val originalClaimsSet = js("{ \"sub\": \"123\", \"aud\": \"456\" }")

        val sdMap = SDMapBuilderJS().addField(
            "sub", true,
            SDMapBuilderJS().addField("child", true).buildAsJSON()
        ).buildAsJSON()

        //val sdMap = js("{\"fields\":{\"sub\":{\"sd\":true,\"children\":{\"fields\":{\"child\":{\"sd\":true,\"children\":null}},\"decoyMode\":\"NONE\",\"decoys\":0}}},\"decoyMode\":\"FIXED\",\"decoys\":2}")
        val sdPayload = SDPayloadBuilder(originalClaimsSet).buildForSDMap(sdMap)
        assertFalse(actual = sdPayload.undisclosedPayload.containsKey("sub"))
        assertContains(map = sdPayload.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
    }
}
