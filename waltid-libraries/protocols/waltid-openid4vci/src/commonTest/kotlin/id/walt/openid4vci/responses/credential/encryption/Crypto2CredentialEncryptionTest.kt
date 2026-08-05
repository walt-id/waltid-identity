package id.walt.openid4vci.responses.credential.encryption

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.requests.credential.encryption.CredentialEncryptionProfile
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import id.walt.openid4vci.requests.credential.encryption.Crypto2JweCredentialRequestDecryptor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Covers the crypto2 replacements for the JVM-only crypto1 JWE bridge, in both directions:
 * the issuer decrypting a Credential Request and encrypting a Credential Response, per OpenID4VCI 1.0 section 8.3.
 */
class Crypto2CredentialEncryptionTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `credential request round trips through the crypto2 decryptor`() = runTest {
        val issuerKey = agreementKey("issuer-encryption-key")
        val request = buildJsonObject { put("credential_configuration_id", "test-credential") }

        val compactJwe = CompactJwe.encrypt(
            plaintext = request.toString().encodeToByteArray(),
            recipientPublicKey = issuerKey.encryptionJwk(),
            contentEncryption = JweContentEncryption.A128GCM,
        )

        assertEquals(request, Crypto2JweCredentialRequestDecryptor(issuerKey).decrypt(compactJwe))
    }

    @Test
    fun `credential response round trips through the crypto2 encryptor`() = runTest {
        val walletKey = agreementKey("wallet-key")
        val payload = buildJsonObject { put("credentials", "issued") }

        val compactJwe = Crypto2JweCredentialResponseEncryptor.encrypt(
            payload = payload,
            encryption = CredentialResponseEncryptionParameters(
                jwk = walletKey.metadataJwk(kid = "wallet-key"),
                enc = CredentialEncryptionProfile.ENC_A128GCM,
            ),
        )

        val header = CompactJwe.decrypt(compactJwe, walletKey, setOf(JweContentEncryption.A128GCM))
        assertEquals(payload.toString(), header.plaintext.decodeToString())
        assertEquals(CredentialEncryptionProfile.ALG_ECDH_ES, header.protectedHeader["alg"]?.jsonPrimitive?.content)
        assertEquals(CredentialEncryptionProfile.ENC_A128GCM, header.protectedHeader["enc"]?.jsonPrimitive?.content)
        // Section 8.3: "If the selected public key contains a kid parameter, the JWE MUST include the same value in
        // the kid JWE Header Parameter."
        assertEquals("wallet-key", header.protectedHeader["kid"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a response JWK without a kid produces a JWE without a kid header`() = runTest {
        val walletKey = agreementKey("wallet-key")

        val compactJwe = Crypto2JweCredentialResponseEncryptor.encrypt(
            payload = buildJsonObject { put("credentials", "issued") },
            encryption = CredentialResponseEncryptionParameters(
                jwk = walletKey.metadataJwk(kid = null),
                enc = CredentialEncryptionProfile.ENC_A128GCM,
            ),
        )

        val decrypted = CompactJwe.decrypt(compactJwe, walletKey, setOf(JweContentEncryption.A128GCM))
        assertTrue("kid" !in decrypted.protectedHeader)
    }

    @Test
    fun `a decryption key that cannot perform key agreement is rejected at construction`() = runTest {
        val signingKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("signing-only"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        // Failing here rather than on the first credential request means a misconfigured issuer cannot start.
        assertFails { Crypto2JweCredentialRequestDecryptor(signingKey) }
    }

    @Test
    fun `requests outside the advertised profile are rejected`() = runTest {
        val issuerKey = agreementKey("issuer-encryption-key")
        val decryptor = Crypto2JweCredentialRequestDecryptor(issuerKey)

        // A256GCM is never advertised in credential_request_encryption.enc_values_supported, so accepting it would let
        // a wallet downgrade to an algorithm the issuer never published.
        val strongerThanAdvertised = CompactJwe.encrypt(
            plaintext = "{}".encodeToByteArray(),
            recipientPublicKey = issuerKey.encryptionJwk(),
            contentEncryption = JweContentEncryption.A256GCM,
        )
        assertFails { decryptor.decrypt(strongerThanAdvertised) }

        // A payload that is not a JSON object cannot be a Credential Request.
        val notAnObject = CompactJwe.encrypt(
            plaintext = "\"string\"".encodeToByteArray(),
            recipientPublicKey = issuerKey.encryptionJwk(),
            contentEncryption = JweContentEncryption.A128GCM,
        )
        assertFails { decryptor.decrypt(notAnObject) }

        // A tampered authentication tag must fail closed.
        val valid = CompactJwe.encrypt(
            plaintext = "{}".encodeToByteArray(),
            recipientPublicKey = issuerKey.encryptionJwk(),
            contentEncryption = JweContentEncryption.A128GCM,
        )
        val parts = valid.split('.').toMutableList()
        parts[4] = (if (parts[4].first() == 'A') "B" else "A") + parts[4].drop(1)
        assertFails { decryptor.decrypt(parts.joinToString(".")) }
    }

    @Test
    fun `a wrong decryption key does not decrypt`() = runTest {
        val issuerKey = agreementKey("issuer-encryption-key")
        val otherKey = agreementKey("other-key")

        val compactJwe = CompactJwe.encrypt(
            plaintext = "{}".encodeToByteArray(),
            recipientPublicKey = issuerKey.encryptionJwk(),
            contentEncryption = JweContentEncryption.A128GCM,
        )

        assertFails { Crypto2JweCredentialRequestDecryptor(otherKey).decrypt(compactJwe) }
    }

    private suspend fun agreementKey(id: String): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.KEY_AGREEMENT),
        ),
    )

    private suspend fun Key.publicJwk(): EncodedKey.Jwk =
        capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(spec)

    /** Bare public JWK, as CompactJwe.encrypt takes it. */
    private suspend fun Key.encryptionJwk(): EncodedKey.Jwk = publicJwk()

    /** Public JWK shaped the way an issuer publishes it, or a wallet sends it, in the OpenID4VCI profile. */
    private suspend fun Key.metadataJwk(kid: String?): JsonObject = buildJsonObject {
        Jwk.parse(publicJwk()).forEach { (name, value) -> put(name, value) }
        kid?.let { put("kid", JsonPrimitive(it)) }
        put("alg", CredentialEncryptionProfile.ALG_ECDH_ES)
        put("use", CredentialEncryptionProfile.KEY_USE_ENC)
    }

}
