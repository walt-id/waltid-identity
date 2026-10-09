package id.walt.openid4vci.metadata.issuer.signing

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwkMetadata
import id.walt.crypto2.jose.JwkOperation
import id.walt.crypto2.jose.JwkUse
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import kotlinx.coroutines.CancellationException

internal class JwkMetadataSigner private constructor(private val signingKey: Crypto2JwtSigningKey) : MetadataJwtSigner {
    override suspend fun sign(metadata: CredentialIssuerMetadata): String = metadata.toSignedJwt(
        signingKey = signingKey.key,
        algorithm = signingKey.algorithm,
        keyId = signingKey.keyId,
    )

    companion object {
        suspend fun load(config: MetadataSigningMethod.StaticJwk): JwkMetadataSigner {
            val material = checked("jwk must contain a valid private JWK JSON object") {
                val bytes = config.jwk.toString().encodeToByteArray()
                EncodedKey.Jwk(BinaryData(bytes), privateMaterial = true).also {
                    require(Jwk.containsPrivateMaterial(Jwk.parse(it)))
                }
            }
            return checked("JWK must be a valid asymmetric signing key matching algorithm, alg, use and key_ops") {
                val metadata = Jwk.metadata(material)
                val keyId = metadata.keyId ?: Jwk.sha256Thumbprint(material)
                val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
                val stored = material.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.SIGN))
                val key = runtime.restore(stored)
                fromKey(key, keyId)
            }
        }

        suspend fun fromKey(key: Key, publishedKeyId: String? = null): JwkMetadataSigner =
            checked("key must support metadata signing and public-key export with a compatible algorithm") {
                val algorithm = key.selectJwsAlgorithm(acceptedAlgorithms = null)
                val exported = key.exportPublicJwk()
                val keyId = publishedKeyId ?: Jwk.sha256Thumbprint(exported)
                val publicMaterial = Jwk.withMetadata(
                    exported,
                    JwkMetadata(keyId, JwkUse.SIGNATURE, setOf(JwkOperation.VERIFY), algorithm.identifier),
                )
                val publicKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
                    publicMaterial.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.VERIFY)),
                )
                val proof = CompactJws.sign("metadata signer initialization".encodeToByteArray(), key, algorithm)
                CompactJws.verify(proof, publicKey, algorithm)
                // Preserve the original signing capability and restrictions. Publish verification operations
                // for the public JWK instead of copying private-key key_ops=[sign] from imported or managed keys.
                val headerKey = object : Key by key {
                    override val capabilities = key.capabilities.copy(publicKeyExporter = object : PublicKeyExporter {
                        override suspend fun exportPublicKey(): EncodedKey = publicMaterial
                    })
                }
                val signingKey = Crypto2JwtSigningKey(headerKey, algorithm, keyId)
                JwkMetadataSigner(signingKey)
            }

        private inline fun <T> checked(message: String, block: () -> T): T = try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // Never propagate parser/provider messages that might include private JWK members.
            throw IllegalArgumentException("Invalid signedMetadata configuration: $message")
        }
    }
}
