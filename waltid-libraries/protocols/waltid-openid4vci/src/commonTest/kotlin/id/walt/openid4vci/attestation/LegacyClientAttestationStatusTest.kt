@file:Suppress("DEPRECATION")

package id.walt.openid4vci.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.jose.CompactJws
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationResult
import id.walt.openid4vci.clientauth.attestation.verifier.KeyBasedClientAttestationVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class LegacyClientAttestationStatusTest {
    @Test fun optionalOmissionDoesNotAddAnExportRequirementToLegacyVerification() = runTest {
        val key = ExportUnavailableKey(JWKKey.generate(KeyType.Ed25519))
        assertEquals(ClientAttestationVerificationResult.Verified, verify(key, JsonObject(emptyMap())))
        assertEquals(0, key.publicRetrievals)
    }

    @Test fun unavailableStatusSignerExportPreservesSuccessfulSignatureVerification() = runTest {
        val key = ExportUnavailableKey(JWKKey.generate(KeyType.Ed25519))
        assertEquals(ClientAttestationVerificationResult.Verified, verify(key, statusPayload()))
        assertEquals(1, key.publicRetrievals)
    }

    @Test fun cancellationDuringLegacyStatusSignerExportPropagates() = runTest {
        val key = ExportUnavailableKey(JWKKey.generate(KeyType.Ed25519), cancel = true)
        assertFailsWith<CancellationException> { verify(key, statusPayload()) }
    }

    @Test fun suppliedStatusRetainsAnAvailableTrustedPublicSigner() = runTest {
        val privateKey = JWKKey.generate(KeyType.Ed25519)
        val trusted = privateKey.getPublicKey()
        val payload = statusPayload()
        val jwt = privateKey.signJws(payload.toString().encodeToByteArray())
        val decoded = CompactJws.decodeUnverified(jwt)
        val result = KeyBasedClientAttestationVerifier { _, _ -> listOf(trusted) }
            .verifyAttestationJwt(jwt, decoded.protectedHeader, payload)
        assertEquals(trusted.exportJWKObject(), assertIs<ClientAttestationVerificationResult.VerifiedWithSigner>(result).publicJwk)
    }

    private fun statusPayload() = buildJsonObject {
        putJsonObject("status") { putJsonObject("status_list") { put("uri", "https://attester.example/status"); put("idx", 0) } }
    }

    private suspend fun verify(key: ExportUnavailableKey, payload: JsonObject): ClientAttestationVerificationResult {
        val jwt = key.signJws(payload.toString().encodeToByteArray())
        val decoded = CompactJws.decodeUnverified(jwt)
        return KeyBasedClientAttestationVerifier { _, _ -> listOf(key) }
            .verifyAttestationJwt(jwt, decoded.protectedHeader, payload)
    }

    /** Real signature verification is available even when public-material retrieval is not. */
    private class ExportUnavailableKey(private val delegate: Key, private val cancel: Boolean = false) : Key() {
        var publicRetrievals = 0
        override val keyType get() = delegate.keyType
        override val hasPrivateKey get() = delegate.hasPrivateKey
        override suspend fun getKeyId() = delegate.getKeyId()
        override suspend fun getThumbprint() = delegate.getThumbprint()
        override suspend fun exportJWK() = delegate.exportJWK()
        override suspend fun exportJWKObject() = delegate.exportJWKObject()
        override suspend fun exportPEM() = delegate.exportPEM()
        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?) = delegate.signRaw(plaintext, customSignatureAlgorithm)
        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>) = delegate.signJws(plaintext, headers)
        override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?) =
            delegate.verifyRaw(signed, detachedPlaintext, customSignatureAlgorithm)
        override suspend fun verifyJws(signedJws: String) = delegate.verifyJws(signedJws)
        override suspend fun getPublicKey(): Key {
            publicRetrievals++
            if (cancel) throw CancellationException("cancelled")
            error("Public-key export unavailable")
        }
        override suspend fun getPublicKeyRepresentation() = delegate.getPublicKeyRepresentation()
        override suspend fun getMeta() = delegate.getMeta()
        override suspend fun deleteKey() = delegate.deleteKey()
    }
}
