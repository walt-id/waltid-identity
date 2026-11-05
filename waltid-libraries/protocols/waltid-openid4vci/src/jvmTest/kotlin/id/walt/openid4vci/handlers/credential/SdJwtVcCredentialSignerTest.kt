package id.walt.openid4vci.handlers.credential

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.sdjwt.ArrayElementDisclosure
import id.walt.sdjwt.ObjectPropertyDisclosure
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVerificationException
import id.walt.sdjwt.SDMap
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The issued SD-JWT VC keeps the issuer's selective disclosure map: nested disclosures, hidden array elements
 * and decoys survive signing, and the claims the issuer always adds stay in clear text.
 */
class SdJwtVcCredentialSignerTest {

    private val credentialData = Json.parseToJsonElement(
        """
        {
          "given_name": "John",
          "nationalities": ["DE", "FR", "AT"],
          "address": {"street": "Main St 1", "locality": "Vienna", "geo": {"lat": 48.2, "lng": 16.37}},
          "degrees": [{"type": "BSc", "year": 2015}, {"type": "MSc", "year": 2018}]
        }
        """.trimIndent()
    ).jsonObject

    private suspend fun issue(sdMap: String, extraClaims: JsonObject? = null, data: JsonObject = credentialData): SDJwt = SDJwt.parse(
        SdJwtVcCredentialSigner.generateSdJwtVC(
            verifiedBinding = VerifiedCredentialBinding(holderKey = key("holder")),
            credentialData = data,
            issuerKey = key("issuer"),
            algorithm = JwsAlgorithm.ES256,
            issuerId = ISSUER,
            vct = VCT,
            selectiveDisclosure = Json.decodeFromString<SDMap>(sdMap),
            sdJwtCredentialClaims = extraClaims,
        )
    )

    private fun SDJwt.disclosureNamed(name: String) =
        disclosureObjects.filterIsInstance<ObjectPropertyDisclosure>().single { it.key == name }

    private val JsonElement.isDigestWrapper get() = this is JsonObject && keys == setOf("...")

    @Test
    fun hiddenArrayKeepsItsHiddenElements() = runTest {
        val sdJwt = issue("""{"fields": {"nationalities": {"sd": true, "arrayChildren": {"wildcard": {"sd": true}}}}}""")

        assertFalse("nationalities" in sdJwt.undisclosedPayload)
        val array = sdJwt.disclosureNamed("nationalities").value.jsonArray
        assertEquals(3, array.size)
        assertTrue(array.all { it.isDigestWrapper })
        assertEquals(3, sdJwt.disclosureObjects.filterIsInstance<ArrayElementDisclosure>().size)
        assertEquals(credentialData["nationalities"], sdJwt.fullPayload["nationalities"])
    }

    @Test
    fun hiddenObjectKeepsItsHiddenFields() = runTest {
        val sdJwt = issue(
            """{"fields": {"address": {"sd": true, "children": {"fields": {
                "street": {"sd": true}, "locality": {"sd": true},
                "geo": {"sd": true, "children": {"fields": {"lat": {"sd": true}, "lng": {"sd": true}}}}}}}}}"""
        )

        assertFalse("address" in sdJwt.undisclosedPayload)
        assertEquals(setOf("_sd"), sdJwt.disclosureNamed("address").value.jsonObject.keys)
        assertEquals(setOf("_sd"), sdJwt.disclosureNamed("geo").value.jsonObject.keys)
        assertEquals(
            setOf("address", "street", "locality", "geo", "lat", "lng"),
            sdJwt.disclosureObjects.filterIsInstance<ObjectPropertyDisclosure>().map { it.key }.toSet(),
        )
        assertEquals(credentialData["address"], sdJwt.fullPayload["address"])
    }

    @Test
    fun hiddenArrayElementKeepsItsHiddenFields() = runTest {
        val sdJwt = issue(
            """{"fields": {"degrees": {"sd": false, "arrayChildren": {"wildcard": {"sd": true,
                "children": {"fields": {"year": {"sd": true}}}}}}}}"""
        )

        val elements = sdJwt.disclosureObjects.filterIsInstance<ArrayElementDisclosure>()
        assertEquals(2, elements.size)
        assertTrue(elements.all { "year" !in it.value.jsonObject && "_sd" in it.value.jsonObject })
        assertEquals(2, sdJwt.disclosureObjects.filterIsInstance<ObjectPropertyDisclosure>().count { it.key == "year" })
        assertEquals(credentialData["degrees"], sdJwt.fullPayload["degrees"])
    }

    @Test
    fun arrayDecoysKeepTheElementsHidden() = runTest {
        val sdJwt = issue(
            """{"fields": {"nationalities": {"sd": false, "arrayChildren": {
                "wildcard": {"sd": true}, "decoyMode": "FIXED", "decoys": 2}}}}"""
        )

        val signed = sdJwt.undisclosedPayload.getValue("nationalities").jsonArray
        assertEquals(5, signed.size)
        assertTrue(signed.all { it.isDigestWrapper })
        assertEquals(3, sdJwt.disclosureObjects.size)
        assertEquals(credentialData["nationalities"], sdJwt.fullPayload["nationalities"])
    }

    @Test
    fun claimsTheIssuerAddsStayInClearTextEvenWhenTheMapNamesThem() = runTest {
        val sdJwt = issue(
            """{"fields": {"iss": {"sd": true}, "vct": {"sd": true}, "cnf": {"sd": true},
                "status": {"sd": true}, "given_name": {"sd": true}}}""",
            extraClaims = buildJsonObject { putJsonObject("status") { put("idx", 7) } },
        )

        assertEquals(ISSUER, sdJwt.undisclosedPayload.getValue("iss").jsonPrimitive.content)
        assertEquals(VCT, sdJwt.undisclosedPayload.getValue("vct").jsonPrimitive.content)
        assertTrue("jwk" in sdJwt.undisclosedPayload.getValue("cnf").jsonObject)
        assertEquals(7, sdJwt.undisclosedPayload.getValue("status").jsonObject.getValue("idx").jsonPrimitive.int)
        assertFalse("given_name" in sdJwt.undisclosedPayload)
        assertEquals(listOf("given_name"), sdJwt.disclosureObjects.filterIsInstance<ObjectPropertyDisclosure>().map { it.key })
    }

    @Test
    fun claimsBreakingSdJwtProcessingAreRejectedBeforeSigning() = runTest {
        val malformed = buildJsonObject { putJsonObject("value") { put("_sd", "not-an-array") } }

        assertFailsWith<SDJwtVerificationException> { issue("""{"fields": {"value": {"sd": true}}}""", data = malformed) }
        assertFailsWith<SDJwtVerificationException> { issue("""{"fields": {}}""", data = malformed) }
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val VCT = "https://issuer.example/identity"
        val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

        suspend fun key(id: String) = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
    }
}
