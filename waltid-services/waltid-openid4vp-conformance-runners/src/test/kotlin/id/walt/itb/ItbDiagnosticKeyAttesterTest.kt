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
import kotlin.test.assertFalse

class ItbDiagnosticKeyAttesterTest {
    @Test
    fun `test attester binds the actual key and nonce without assurance claims`() = runTest {
        val holder = WalletCredentialIssuer().holderCrypto2Key()
        val attesterKey = WalletCredentialIssuer().holderCrypto2Key()
        val holderJwk = holder.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(holder.spec)
        val jwt = ItbDiagnosticKeyAttester(attesterKey).attest(
            KeyAttestationRequest("https://issuer.example", holderJwk, "fresh-nonce", KeyAttestationsRequired())
        )

        val verified = CompactJws.verify(jwt, attesterKey, JwsAlgorithm.ES256)
        val claims = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
        assertEquals("fresh-nonce", claims["nonce"]?.jsonPrimitive?.content)
        assertEquals(
            Json.parseToJsonElement(holderJwk.data.toByteArray().decodeToString()),
            claims["attested_keys"]!!.jsonArray.single(),
        )
        for (claim in listOf("key_storage", "user_authentication", "certification", "key_storage_status")) {
            assertFalse(claim in claims)
        }
    }
}
