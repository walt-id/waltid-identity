package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.did.dids.DidService
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.handlers.SignProofTestSupport.CONFIG_ID
import id.walt.wallet2.handlers.SignProofTestSupport.ISSUER
import id.walt.wallet2.handlers.SignProofTestSupport.issuerMetadataClient
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the three-tier DID resolution chain for [WalletIssuanceHandler.signProof]:
 *   1. inline [SignProofRequest.did] takes precedence
 *   2. [SignProofRequest.didReference] is resolved from [Wallet.didStore]
 *   3. if neither yields a DID, falls back to JWK binding
 */
class WalletIssuanceHandlerDidReferenceTest {

    @Test
    fun `signProof uses DID from store when only didReference is set`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val didStore = InMemoryDidStore().also {
            it.addDid(WalletDidEntry(did = did, document = buildJsonObject {}))
        }

        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "test", staticKey = key, didStore = didStore),
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
                didReference = did,
            ),
            httpClient = issuerMetadataClient(
                proofAlgorithms = setOf("ES256", "EdDSA"),
                bindingMethods = setOf("did:key"),
            ),
        ).proofJwt

        assertEquals(
            "$did#${did.removePrefix("did:key:")}",
            CompactJws.decodeUnverified(proof).protectedHeader["kid"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `signProof inline did takes precedence over didReference`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did

        // Store a different DID string under a reference so we can detect if it is accidentally used.
        val didStore = InMemoryDidStore().also {
            it.addDid(WalletDidEntry(did = "did:key:other", document = buildJsonObject {}))
        }

        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "test", staticKey = key, didStore = didStore),
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
                did = did,
                didReference = "did:key:other",
            ),
            httpClient = issuerMetadataClient(
                proofAlgorithms = setOf("ES256", "EdDSA"),
                bindingMethods = setOf("did:key"),
            ),
        ).proofJwt

        assertEquals(
            "$did#${did.removePrefix("did:key:")}",
            CompactJws.decodeUnverified(proof).protectedHeader["kid"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `signProof falls back to JWK binding when didReference is not in the store`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val emptyDidStore = InMemoryDidStore()

        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "test", staticKey = key, didStore = emptyDidStore),
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
                didReference = "did:key:not-in-store",
            ),
            httpClient = issuerMetadataClient(
                proofAlgorithms = setOf("ES256", "EdDSA"),
                bindingMethods = setOf("jwk", "did:key"),
            ),
        ).proofJwt

        val header = CompactJws.decodeUnverified(proof).protectedHeader
        assertNotNull(header["jwk"], "Expected JWK binding when didReference is absent from the store")
        assertNull(header["kid"]?.jsonPrimitive?.content, "Unexpected DID kid when didReference was not found")
    }

    @Test
    fun `signProof falls back to JWK binding when wallet has no DID store and didReference is set`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)

        val proof = WalletIssuanceHandler.signProof(
            wallet = Wallet(id = "test", staticKey = key),
            request = SignProofRequest(
                issuerUrl = Url(ISSUER),
                credentialConfigurationId = CONFIG_ID,
                nonce = "nonce",
                didReference = "did:key:any",
            ),
            httpClient = issuerMetadataClient(
                proofAlgorithms = setOf("ES256", "EdDSA"),
                bindingMethods = setOf("jwk"),
            ),
        ).proofJwt

        val header = CompactJws.decodeUnverified(proof).protectedHeader
        assertNotNull(header["jwk"], "Expected JWK binding when wallet has no DID store")
        assertNull(header["kid"]?.jsonPrimitive?.content, "Unexpected DID kid when no DID store is present")
    }
}
