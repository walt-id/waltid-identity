package id.walt.policies2.vp.policies

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VPPolicyListAlgorithmSerializationTest {
    @Test
    fun `default KB-JWT algorithm configuration uses protocol supported algorithms`() {
        assertTrue(KbJwtSignatureSdJwtVPPolicy().hasDefaultAlgorithmConfiguration)
        val encoded = Json { encodeDefaults = true }.encodeToString(KbJwtSignatureSdJwtVPPolicy())
        val algorithms = Json.parseToJsonElement(encoded).jsonObject.getValue("allowedAlgorithms")
            .jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf("ES256", "ES384", "ES512", "ES256K", "Ed25519", "Ed448", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512"),
            algorithms,
        )
    }

    @Test
    fun `custom signature algorithm allowlists survive serialization`() {
        val policies = VPPolicyList(
            jwtVcJson = listOf(
                SignatureJwtVcJsonVPPolicy(setOf("ES256")),
                ExpCheckJwtVcJsonVPPolicy(clockSkewSeconds = 30),
                NbfCheckJwtVcJsonVPPolicy(clockSkewSeconds = 31),
            ),
            dcSdJwt = listOf(
                KbJwtSignatureSdJwtVPPolicy(setOf("Ed25519")),
                KbJwtIatCheckSdJwtVPPolicy(maxAgeMinutes = 10),
                ExpCheckSdJwtVPPolicy(clockSkewSeconds = 32),
                NbfCheckSdJwtVPPolicy(clockSkewSeconds = 33),
            ),
            msoMdoc = listOf(MsoVerificationMdocVpPolicy(strictEtsiPrecision = true)),
        )

        val encoded = Json.encodeToString(policies)
        val json = Json.parseToJsonElement(encoded).jsonObject
        assertTrue(json.getValue("jwt_vc_json").jsonArray.all { it is JsonObject })
        assertTrue(json.getValue("dc+sd-jwt").jsonArray.all { it is JsonObject })
        assertTrue(json.getValue("mso_mdoc").jsonArray.all { it is JsonObject })

        fun algorithms(value: JsonObject, format: String): Set<String> =
            value.getValue(format).jsonArray[0].jsonObject.getValue("allowedAlgorithms")
                .jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("ES256"), algorithms(json, "jwt_vc_json"))
        assertEquals(setOf("Ed25519"), algorithms(json, "dc+sd-jwt"))

        val decoded = Json.decodeFromString<VPPolicyList>(encoded)
        assertFalse(assertIs<SignatureJwtVcJsonVPPolicy>(decoded.jwtVcJson[0]).hasDefaultAlgorithmConfiguration)
        assertFalse(assertIs<KbJwtSignatureSdJwtVPPolicy>(decoded.dcSdJwt[0]).hasDefaultAlgorithmConfiguration)
        assertEquals(30, assertIs<ExpCheckJwtVcJsonVPPolicy>(decoded.jwtVcJson[1]).clockSkewSeconds)
        assertEquals(31, assertIs<NbfCheckJwtVcJsonVPPolicy>(decoded.jwtVcJson[2]).clockSkewSeconds)
        assertEquals(10, assertIs<KbJwtIatCheckSdJwtVPPolicy>(decoded.dcSdJwt[1]).maxAgeMinutes)
        assertEquals(32, assertIs<ExpCheckSdJwtVPPolicy>(decoded.dcSdJwt[2]).clockSkewSeconds)
        assertEquals(33, assertIs<NbfCheckSdJwtVPPolicy>(decoded.dcSdJwt[3]).clockSkewSeconds)
        assertTrue(assertIs<MsoVerificationMdocVpPolicy>(decoded.msoMdoc.single()).strictEtsiPrecision)
        val reencoded = Json.parseToJsonElement(Json.encodeToString(decoded)).jsonObject
        assertEquals(setOf("ES256"), algorithms(reencoded, "jwt_vc_json"))
        assertEquals(setOf("Ed25519"), algorithms(reencoded, "dc+sd-jwt"))
    }
}
