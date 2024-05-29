package id.walt.sdjwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import korlibs.crypto.SHA256
import korlibs.crypto.encoding.ASCII
import korlibs.crypto.encoding.base64Url
import kotlinx.serialization.json.*
import kotlin.math.sign
import kotlin.test.Test

class SDJwtTestJVM {
    // Generate shared secret for HMAC crypto algorithm
    private val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

    @Test
    fun testSignJwt() {

        // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))

        // Create original JWT claims set, using nimbusds claims set builder
        val originalClaimsSet = JWTClaimsSet.Builder()
            .subject("123")
            .audience("456")
            .build()

        // Create undisclosed claims set, by removing e.g. subject property from original claims set
        val undisclosedClaimsSet = JWTClaimsSet.Builder(originalClaimsSet)
            .subject(null)
            .build()

        // Create SD payload by comparing original claims set with undisclosed claims set
        val sdPayload = SDPayload.createSDPayload(originalClaimsSet, undisclosedClaimsSet)

        // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
        val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)
        // Print SD-JWT
        println(sdJwt)

        sdJwt.undisclosedPayload shouldNotContainKey "sub"
        sdJwt.undisclosedPayload shouldContainKey SDJwt.DIGESTS_KEY
        sdJwt.undisclosedPayload shouldContainKey "aud"
        sdJwt.disclosures shouldHaveSize 1
        sdJwt.digestedDisclosures[sdJwt.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray[0].jsonPrimitive.content]!!.key shouldBe "sub"
        sdJwt.fullPayload.toString() shouldEqualJson originalClaimsSet.toString()

        sdJwt.verify(cryptoProvider).verified shouldBe true
    }

    @Test
    fun presentSDJwt() {
        // parse previously created SD-JWT
        val sdJwt =
            SDJwt.parse("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0")

        // present without disclosing SD fields
        val presentedUndisclosedJwt = sdJwt.present(discloseAll = false)
        println("present without disclosing SD fields: $presentedUndisclosedJwt")

        // present disclosing all SD fields
        val presentedDisclosedJwt = sdJwt.present(discloseAll = true)
        println("present disclosing all SD fields: $presentedDisclosedJwt")

        // present disclosing selective fields, using SDMap
        val presentedSelectiveJwt = sdJwt.present(SDMapBuilder().addField("sub", true).build())
        println("present disclosing selective fields, using SDMap: $presentedSelectiveJwt")

        // present disclosing fields, using JSON paths
        val presentedSelectiveJwt2 = sdJwt.present(SDMap.generateSDMap(listOf("sub")))
        println("present disclosing fields, using JSON paths: $presentedSelectiveJwt2")

    }

    @Test
    fun parseAndVerify() {
        // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, jwsSigner = null, jwsVerifier = MACVerifier(sharedSecret))

        val undisclosedJwt =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~"

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

    @Test
    fun testJwtWithCustomHeaders() {
        // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))
        val signedJwt = cryptoProvider.sign(buildJsonObject { put("test", JsonPrimitive("hello")) },
            headers = mapOf(
                "h1" to "v1",
                "h2" to 2,
                "h3" to mapOf("h3.1" to "v3")
            )
        )
        val parsedJwt = SDJwt.parse(signedJwt)
        parsedJwt.header.keys shouldContainAll listOf("h1", "h2", "h3")
        parsedJwt.header["h1"]!!.jsonPrimitive.content shouldBe "v1"
        parsedJwt.header["h2"]!!.jsonPrimitive.int shouldBe 2
        parsedJwt.header["h3"]!!.jsonObject["h3.1"]!!.jsonPrimitive.content shouldBe "v3"
    }

    @Test
    fun testPresentingSdJwtWithKeyBindingJwt() {
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))
        val aud = "test-audience"
        val nonce = "test-nonce"
        val signedJwt = SDJwt.sign(SDPayload.Companion.createSDPayload(
            buildJsonObject { put("test", JsonPrimitive("hello")) },
            SDMapBuilder().addField("test", true).build()), cryptoProvider)
        val presentedJwtNoKb = signedJwt.present(true)
        presentedJwtNoKb.keyBindingJwt shouldBe null
        val presentedJwtWithKb = signedJwt.present(true, aud, nonce, cryptoProvider)
        presentedJwtWithKb.keyBindingJwt shouldNotBe null
        presentedJwtWithKb.toString() shouldStartWith presentedJwtNoKb.toString()
        presentedJwtWithKb.keyBindingJwt!!.audience shouldBe aud
        presentedJwtWithKb.keyBindingJwt!!.nonce shouldBe nonce
        presentedJwtWithKb.keyBindingJwt!!.sdHash shouldBe SHA256.digest(ASCII.encode(presentedJwtNoKb.toString())).base64Url

    }
}
