package id.walt.itb

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.toPublicJwk
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import id.walt.wallet2.handlers.KeyAttestationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItbSyntheticKeyAttesterTest {
    @Test
    fun `fixture binds the proof key without asserting an ISO assurance level`() = runTest {
        val proofKey = WalletCredentialIssuer().holderCrypto2Key()
        val attesterKey = WalletCredentialIssuer().holderCrypto2Key()
        val proofJwk = requireNotNull(proofKey.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(proofKey.spec)
        val jwt = ItbSyntheticKeyAttester(attesterKey).attest(
            KeyAttestationRequest("https://issuer.example", proofJwk, "fresh-nonce", KeyAttestationsRequired())
        )

        val claims = Json.parseToJsonElement(
            CompactJws.verify(jwt, attesterKey, JwsAlgorithm.ES256).payload.decodeToString()
        ).jsonObject
        assertEquals("fresh-nonce", claims["nonce"]?.jsonPrimitive?.content)
        assertEquals(Json.parseToJsonElement(proofJwk.data.toByteArray().decodeToString()), claims["attested_keys"]!!.jsonArray.single())
        for (name in listOf("key_storage", "user_authentication")) {
            assertTrue(claims[name]!!.jsonArray.all { it.jsonPrimitive.content.startsWith("https://example.invalid/") })
        }
        assertTrue(claims["certification"]!!.jsonPrimitive.content.startsWith("https://example.invalid/"))
        assertTrue(claims["key_storage_status"]!!.jsonObject["status"]!!.jsonObject["status_list"]!!
            .jsonObject["uri"]!!.jsonPrimitive.content.startsWith("https://example.invalid/"))
    }
}
