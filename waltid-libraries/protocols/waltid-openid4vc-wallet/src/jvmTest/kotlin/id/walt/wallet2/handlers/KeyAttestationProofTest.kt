package id.walt.wallet2.handlers

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.handlers.SignProofTestSupport.CONFIG_ID
import id.walt.wallet2.handlers.SignProofTestSupport.ISSUER
import id.walt.wallet2.handlers.SignProofTestSupport.issuerMetadataClient
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class KeyAttestationProofTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `required attestation is signed and bound to proof key and nonce`() = runTest {
        val attester = newKey("attester")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(attester))
        val proof = sign(wallet)
        val attestation = assertNotNull(CompactJws.decodeUnverified(proof).protectedHeader["key_attestation"])
            .jsonPrimitive.content
        val verified = CompactJws.verify(attestation, attester, JwsAlgorithm.ES256)
        val claims = Json.parseToJsonElement(verified.payload.decodeToString()) as JsonObject
        assertEquals("nonce", claims["nonce"]?.jsonPrimitive?.content)
        assertEquals("key-attestation+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
    }

    @Test
    fun `required attestation fails without provider`() = runTest {
        assertFailsWith<IllegalArgumentException> { sign(walletWithProofKey()) }
    }

    @Test
    fun `attestation for another key is rejected before proof is sent`() = runTest {
        val wrongKey = newKey("other")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, attestedKey = wrongKey)
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("does not contain the credential proof key"))
    }

    @Test
    fun `attestation with wrong nonce is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, nonce = "different")
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("nonce does not match"))
    }

    @Test
    fun `expired attestation is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(TestProvider(newKey("attester"), payload = {
            request -> claims(request, expiresAt = Clock.System.now().toEpochMilliseconds() / 1000 - 1)
        }))
        val error = assertFailsWith<IllegalArgumentException> { sign(wallet) }
        assertTrue(error.message.orEmpty().contains("not currently valid"))
    }

    @Test
    fun `attestation signed by another provider is rejected`() = runTest {
        val wallet = walletWithProofKey().attachKeyAttestationProvider(
            TestProvider(newKey("expected-attester"), signingKey = newKey("other-attester"))
        )
        assertFailsWith<IllegalArgumentException> { sign(wallet) }
    }

    @Test
    fun `provider is not invoked when issuer has no attestation requirement`() = runTest {
        val unusedAttester = newKey("unused-attester")
        val wallet = walletWithProofKey().attachKeyAttestationProvider(object : KeyAttestationProvider {
            override val verificationKey = unusedAttester
            override suspend fun attest(request: KeyAttestationRequest): String = error("Unexpected attestation")
        })
        val proof = WalletIssuanceHandler.signProof(
            wallet = wallet,
            request = SignProofRequest(Url(ISSUER), CONFIG_ID, "nonce"),
            httpClient = issuerMetadataClient(),
        ).proofJwt
        assertTrue("key_attestation" !in CompactJws.decodeUnverified(proof).protectedHeader)
    }

    private suspend fun sign(wallet: Wallet): String = WalletIssuanceHandler.signProof(
        wallet = wallet,
        request = SignProofRequest(Url(ISSUER), CONFIG_ID, "nonce"),
        httpClient = issuerMetadataClient(requiresKeyAttestation = true),
    ).proofJwt

    private suspend fun walletWithProofKey(): Wallet = Wallet(
        id = "wallet",
        keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(newKey("proof")) }),
    )

    private suspend fun newKey(id: String): Crypto2Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private inner class TestProvider(
        override val verificationKey: Crypto2Key,
        private val payload: suspend (KeyAttestationRequest) -> JsonObject = { claims(it) },
        private val signingKey: Crypto2Key = verificationKey,
    ) : KeyAttestationProvider {
        override suspend fun attest(request: KeyAttestationRequest): String {
            val attesterJwk = verificationKey.capabilities.publicKeyExporter!!.exportPublicKey()
                .toPublicJwk(verificationKey.spec)
            return CompactJws.sign(
                payload = payload(request).toString().encodeToByteArray(),
                key = signingKey,
                algorithm = JwsAlgorithm.ES256,
                protectedHeader = buildJsonObject {
                    put("typ", "key-attestation+jwt")
                    put("jwk", Json.parseToJsonElement(attesterJwk.data.toByteArray().decodeToString()))
                },
            )
        }
    }

    private suspend fun claims(
        request: KeyAttestationRequest,
        attestedKey: Crypto2Key? = null,
        nonce: String? = request.nonce,
        expiresAt: Long = Clock.System.now().toEpochMilliseconds() / 1000 + 300,
    ): JsonObject {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val jwk = attestedKey?.let { key ->
            key.capabilities.publicKeyExporter?.exportPublicKey()?.toPublicJwk(key.spec)
        }
            ?: request.proofKey
        return buildJsonObject {
            put("iat", now)
            put("exp", expiresAt)
            nonce?.let { put("nonce", it) }
            put("attested_keys", Json.parseToJsonElement("[${jwk.data.toByteArray().decodeToString()}]"))
        }
    }
}
