@file:OptIn(ExperimentalTime::class)

package id.walt.sdjwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import korlibs.crypto.SHA256
import korlibs.crypto.encoding.ASCII
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

        assertFalse(actual = sdJwt.undisclosedPayload.containsKey("sub"))
        assertContains(map = sdJwt.undisclosedPayload, key = SDJwt.DIGESTS_KEY)
        assertContains(map = sdJwt.undisclosedPayload, key = "aud")
        assertEquals(expected = 1, actual = sdJwt.disclosures.size)
        assertEquals(
            expected = "sub",
            actual = sdJwt.digestedDisclosures[sdJwt.undisclosedPayload[SDJwt.DIGESTS_KEY]!!.jsonArray[0].jsonPrimitive.content]!!.key
        )
        assertContentEquals(
            expected = Json.parseToJsonElement(originalClaimsSet.toString()).jsonObject.toSortedMap().asIterable(),
            actual = sdJwt.fullPayload.toSortedMap().asIterable()
        )

        assertTrue(actual = sdJwt.verify(cryptoProvider).verified)
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

        val disclosedJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI0NTYiLCJfc2QiOlsiaGx6ZmpmMDRvNVpzTFIyNWhhNGMtWS05SFcyRFVseGNnaU1ZZDMyNE5nWSJdfQ.2fsLqzujWt0hS0peLS8JLHyyo3D5KCDkNnHcBYqQwVo~WyJ4RFk5VjBtOG43am82ZURIUGtNZ1J3Iiwic3ViIiwiMTIzIl0~"
        val parsedDisclosedJwtVerifyResult = SDJwt.verifyAndParse(
            disclosedJwt,
            cryptoProvider
        )
        // print full payload with disclosed fields
        println("Disclosed JWT payload:")
        println(parsedDisclosedJwtVerifyResult.sdJwt.fullPayload.toString())

        val forgedDisclosure = parsedDisclosedJwtVerifyResult.sdJwt.jwt + "~" + forgeDislosure(parsedDisclosedJwtVerifyResult.sdJwt.disclosureObjects.first())
        val forgedDisclosureVerifyResult = SDJwt.verifyAndParse(
            forgedDisclosure, cryptoProvider
        )
        assertFalse(forgedDisclosureVerifyResult.verified)
        assertTrue(forgedDisclosureVerifyResult.signatureVerified)
        assertFalse(forgedDisclosureVerifyResult.disclosuresVerified)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun forgeDislosure(disclosure: SDisclosure): String {
        return Base64.UrlSafe.encode(buildJsonArray {
            add(disclosure.salt)
            add(disclosure.key)
            add(JsonPrimitive("<forged>"))
        }.toString().encodeToByteArray()).trimEnd('=')
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
        assertContains(parsedJwt.header.keys, "h1")
        assertContains(parsedJwt.header.keys, "h2")
        assertContains(parsedJwt.header.keys, "h3")
        assertEquals("v1", parsedJwt.header["h1"]!!.jsonPrimitive.content)
        assertEquals(2, parsedJwt.header["h2"]!!.jsonPrimitive.int)
        assertEquals("v3", parsedJwt.header["h3"]!!.jsonObject["h3.1"]!!.jsonPrimitive.content)
    }

    @Test
    fun testPresentingSdJwtWithKeyBindingJwt() {
        val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))
        val aud = "test-audience"
        val nonce = "test-nonce"
        val issuanceTime = Clock.System.now()
        val signedJwt = SDJwt.sign(SDPayload.Companion.createSDPayload(
            buildJsonObject { put("test", JsonPrimitive("hello")) },
            SDMapBuilder().addField("test", true).build()), cryptoProvider)
        val presentedJwtNoKb = signedJwt.present(true)
        assertNull(presentedJwtNoKb.keyBindingJwt)
        val presentedJwtWithKb = signedJwt.present(true, aud, nonce, cryptoProvider)
        assertNotNull(presentedJwtWithKb.keyBindingJwt)
        assertTrue(presentedJwtWithKb.toString().startsWith(presentedJwtNoKb.toString()))
        assertTrue(presentedJwtWithKb.keyBindingJwt.issuedAt >= issuanceTime.epochSeconds)
        assertEquals(aud, presentedJwtWithKb.keyBindingJwt.audience)
        assertEquals(nonce, presentedJwtWithKb.keyBindingJwt.nonce)
        assertEquals(SHA256.digest(ASCII.encode(presentedJwtNoKb.toString())).base64Url, presentedJwtWithKb.keyBindingJwt.sdHash)

    }
}
