package id.walt.itb

import id.walt.walletdemo.attestation.DemoKeyAttestationProviders
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.wallet2.handlers.KeyAttestationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class DemoKeyAttestationProvidersTest {
    @Test
    fun unknownAndLookalikeIssuersNeverContactTheMockService() = runTest {
        val resolver = DemoKeyAttestationProviders { error("Unexpected HTTP request") }
        for (issuer in listOf("https://unknown.example", DemoKeyAttestationProviders.EUDI_ISSUER + "/",
            DemoKeyAttestationProviders.ITB_ISSUER + "/other", "http://dev-i4mlab.aegean.gr/rfc-issuer",
            "https://issuer.eudiw.dev.evil.example")) {
            assertNull(resolver.resolve(issuer))
        }
    }

    @Test
    fun itbAttestationIsSignedAndBoundAndMakesOnlyUnassessedClaims() = runTest {
        val resolver = DemoKeyAttestationProviders { error("ITB must not call the EUDI service") }
        val provider = assertNotNull(resolver.resolve(DemoKeyAttestationProviders.ITB_ISSUER))
        val proof = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("proof"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        )
        val jwk = proof.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(proof.spec)
        val jwt = provider.attest(KeyAttestationRequest(DemoKeyAttestationProviders.ITB_ISSUER, jwk, "fresh-nonce", KeyAttestationsRequired()))
        val verified = CompactJws.verify(jwt, provider.verificationKey, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
        assertEquals("key-attestation+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("fresh-nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertEquals(Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()), payload["attested_keys"]?.jsonArray?.single())
        assertEquals(300, payload.getValue("exp").jsonPrimitive.long - payload.getValue("iat").jsonPrimitive.long)
        for (name in listOf("key_storage", "user_authentication")) {
            assertTrue(payload.getValue(name).jsonArray.all { it.jsonPrimitive.content.startsWith("https://example.invalid/") })
        }
        assertTrue(payload.getValue("certification").jsonPrimitive.content.endsWith("no-certification"))
        val status = payload.getValue("key_storage_status").jsonObject
        assertEquals(3600, status.getValue("exp").jsonPrimitive.long - payload.getValue("iat").jsonPrimitive.long)
        val statusList = status.getValue("status").jsonObject.getValue("status_list").jsonObject
        assertEquals(0, statusList.getValue("idx").jsonPrimitive.int)
        assertTrue(statusList.getValue("uri").jsonPrimitive.content.startsWith("https://example.invalid/"))
    }

    @Test
    fun eudiFailureAndCancellationDoNotFallBackToSyntheticClaims() = runTest {
        val unavailable = DemoKeyAttestationProviders {
            HttpClient(MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) })
        }
        assertFailsWith<IllegalStateException> { unavailable.resolve(DemoKeyAttestationProviders.EUDI_ISSUER) }
        val cancelled = DemoKeyAttestationProviders { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { cancelled.resolve(DemoKeyAttestationProviders.EUDI_ISSUER) }
    }
}
