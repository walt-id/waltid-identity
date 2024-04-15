package id.walt.sdjwt

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.khronos.webgl.Uint8Array
import kotlin.test.Test

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

        sdJwt.undisclosedPayload shouldNotContainKey "sub"
        sdJwt.undisclosedPayload shouldContainKey SDJwt.DIGESTS_KEY
        sdJwt.undisclosedPayload shouldContainKey "aud"
        sdJwt.disclosures shouldHaveSize 1
        sdJwt.digestedDisclosures[sdJwt.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray[0].jsonPrimitive.content]!!.key shouldBe "sub"
        println("BLA")
        sdJwt.fullPayload.toString() shouldEqualJson JSON.stringify(originalClaimsSet)
        println("ASDASD")

        sdJwt.verifyAsync(cryptoProvider).verified shouldBe true
        SDJwt.parse("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiI0NTciLCJfc2QiOlsibTcyQ0tyVHhYckhlWUJSQTFBVFQ1S0t4NGdFWExlOVhqVFROakdRWkVQNCJdfQ.Tltz2SGxmdIpD_ny1XSTn89rQSmYsl9EcsXxsfJE0wo")
            .verifyAsync(cryptoProvider).verified shouldBe false
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
        sdPayload.undisclosedPayload.keys shouldNotContain "sub"
        sdPayload.undisclosedPayload.keys shouldContain SDJwt.DIGESTS_KEY
    }
}
