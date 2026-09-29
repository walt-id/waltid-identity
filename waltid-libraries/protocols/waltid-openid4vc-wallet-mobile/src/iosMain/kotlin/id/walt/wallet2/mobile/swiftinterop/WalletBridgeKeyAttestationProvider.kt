package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationRequest

/**
 * Public proof key and issuer requirements supplied to a Swift wallet provider.
 * @property credentialIssuer Credential issuer requesting the proof.
 * @property proofKeyJwk Selected proof key encoded as a public JWK JSON object.
 * @property nonce Current issuer nonce, when supplied.
 * @property requiredKeyStorage Accepted storage values, or null when unconstrained.
 * @property requiredUserAuthentication Accepted authentication values, or null when unconstrained.
 */
public data class WalletBridgeKeyAttestationRequest(
    public val credentialIssuer: String,
    public val proofKeyJwk: String,
    public val nonce: String?,
    public val requiredKeyStorage: List<String>?,
    public val requiredUserAuthentication: List<String>?,
)

/** Runtime provider; trust in this provider's assertions is established by the application. */
public interface WalletBridgeKeyAttestationProvider {
    /** Independently obtained public JWK used to verify the provider's signed attestations. */
    public val verificationPublicJwk: String

    /** Returns a signed key-attestation JWT for this key, nonce and set of requirements. */
    public suspend fun attest(request: WalletBridgeKeyAttestationRequest): String
}

internal suspend fun WalletBridgeKeyAttestationProvider.toKeyAttestationProvider(): KeyAttestationProvider {
    val provider = this
    val publicKey = EncodedKey.Jwk(BinaryData(verificationPublicJwk.encodeToByteArray()), false)
    val key = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
        publicKey.toStoredSoftwareKey(KeyId("wallet-key-attester"), setOf(KeyUsage.VERIFY)),
    )
    return object : KeyAttestationProvider {
        override val verificationKey = key

        override suspend fun attest(request: KeyAttestationRequest): String = provider.attest(
            WalletBridgeKeyAttestationRequest(
                request.credentialIssuer,
                request.proofKey.data.toByteArray().decodeToString(),
                request.nonce,
                request.requirements.keyStorage?.toList(),
                request.requirements.userAuthentication?.toList(),
            ),
        )
    }
}
