package id.walt.openid4vci.metadata.issuer.signing

import id.walt.certificate.x509.X509CertificateUtil
import kotlinx.io.bytestring.ByteString
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.openid4vci.tokens.jwt.Crypto2JwtSigningKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.toSignedJwt
import kotlinx.coroutines.CancellationException
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage as CertificateKeyUsage
import id.walt.crypto.utils.ShaUtils
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.io.encoding.Base64

/** Local certificate consistency checks are not a wallet trust policy. No trust anchors are installed here. */
internal class CertificateMetadataSigner private constructor(
    private val signingKey: Crypto2JwtSigningKey,
    private val certificates: List<X509Certificate>,
    private val now: () -> Instant,
) : MetadataJwtSigner {
    private val certificateChain = certificates.map { Base64.Default.encode(it.encodedDer.toByteArray()) }

    override suspend fun sign(metadata: CredentialIssuerMetadata): String {
        validateValidity(certificates, now)
        return metadata.toSignedJwt(signingKey.key, signingKey.algorithm, certificateChain = certificateChain, keyId = signingKey.keyId)
    }

    companion object {
        suspend fun load(config: MetadataSigningMethod.X509Chain, now: () -> Instant = { Clock.System.now() }): CertificateMetadataSigner {
            val certificates = loadCertificates(config.certificateChainPem, now)
            val publicKey = leafPublicKey(certificates)
            val material = checked("privateKeyPem must contain one unencrypted PKCS#8 PEM private key") {
                config.privateKeyPem.trimIndent().trim().decodePrivateKeyPem()
            }
            val key = checked("private key must be a supported asymmetric signing key") {
                CryptoRuntime(defaultSoftwareKeyProviders()).restore(
                    StoredKey.Software(
                        version = StoredKey.CURRENT_VERSION,
                        id = KeyId(certificateKeyId(certificates.first())),
                        spec = publicKey.spec,
                        usages = setOf(KeyUsage.SIGN),
                        material = material,
                    ),
                )
            }
            return create(key, certificates, publicKey, now)
        }

        suspend fun fromKey(
            key: Key,
            certificateChainPem: List<String>,
            now: () -> Instant = { Clock.System.now() },
        ): CertificateMetadataSigner {
            val certificates = loadCertificates(certificateChainPem, now)
            return create(key, certificates, leafPublicKey(certificates), now)
        }

        private suspend fun create(
            key: Key, certificates: List<X509Certificate>, publicKey: Key, now: () -> Instant,
        ): CertificateMetadataSigner = checked("private key does not match the leaf certificate or cannot sign with the selected algorithm") {
            val algorithm = key.selectJwsAlgorithm(acceptedAlgorithms = null)
            val signingKey = Crypto2JwtSigningKey(key, algorithm, certificateKeyId(certificates.first()))
            val proof = CompactJws.sign("metadata signer initialization".encodeToByteArray(), key, algorithm)
            CompactJws.verify(proof, publicKey, algorithm)
            CertificateMetadataSigner(signingKey, certificates, now)
        }

        private suspend fun leafPublicKey(certificates: List<X509Certificate>): Key =
            checked("leaf certificate must contain a supported public signing key") {
                certificates.first().restoreSubjectPublicKey(CryptoRuntime(defaultSoftwareKeyProviders()))
            }

        private fun certificateKeyId(leaf: X509Certificate): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            ShaUtils.sha256(leaf.data.subjectPublicKeyInfo.encodedDer.toByteArray()),
        )

        private suspend fun loadCertificates(pem: List<String>, now: () -> Instant): List<X509Certificate> {
            val certificates = checked("certificateChainPem must contain a non-empty list of PEM certificates, leaf first") {
                require(pem.isNotEmpty())
                pem.map { entry -> parseCertificates(entry).single() }
            }
            validateValidity(certificates, now)
            checked("certificate chain is unordered or has invalid signatures or CA constraints") {
                require(certificates.map { Base64.Default.encode(it.encodedDer.toByteArray()) }.distinct().size == certificates.size)
                certificates.zipWithNext().forEachIndexed { index, (child, issuer) ->
                    require(child.data.issuerDnRaw == issuer.data.subjectDnRaw)
                    val constraints = requireNotNull(issuer.data.extensionBasicConstraints)
                    require(constraints.cA)
                    val subordinateCas = certificates.subList(1, index + 1)
                        .count { it.data.subjectDnRaw != it.data.issuerDnRaw }
                    require(constraints.pathLenConstraint == null || constraints.pathLenConstraint!! >= subordinateCas)
                    require(issuer.data.extensionKeyUsage?.keyPurposeIdList?.contains(CertificateKeyUsage.keyCertSign) != false)
                    require(X509CertificateUtil.services.signatureValidator.validateCertificateSignature(
                        CryptoRuntime(defaultSoftwareKeyProviders()), issuer.data.subjectPublicKeyInfo, child,
                    ))
                }
            }
            checked("leaf certificate must permit digital signatures") {
                require(certificates.first().data.extensionKeyUsage?.keyPurposeIdList?.contains(CertificateKeyUsage.digitalSignature) != false)
            }
            return certificates
        }

        private fun validateValidity(certificates: List<X509Certificate>, now: () -> Instant) {
            checked("certificate chain contains an expired or not-yet-valid certificate") {
                val at = now()
                certificates.forEach { require(at >= it.data.validity.notBefore && at <= it.data.validity.notAfter) }
            }
        }

        private val certificatePem = Regex(
            "-----BEGIN CERTIFICATE-----([A-Za-z0-9+/=\\r\\n\\t ]+)-----END CERTIFICATE-----",
        )

        private fun parseCertificates(pem: String): List<X509Certificate> {
            require(certificatePem.replace(pem, "").isBlank())
            return certificatePem.findAll(pem).map { match ->
                val encoded = match.groupValues[1].filterNot { it.isWhitespace() }
                val der = Base64.Default.decode(encoded)
                require(Base64.Default.encode(der) == encoded)
                X509CertificateUtil.parseCertificateDerEncoded(ByteString(der)).also {
                    require(it.encodedDer.toByteArray().contentEquals(der))
                }
            }.toList().also { require(it.isNotEmpty()) }
        }

        // Parser/provider exception messages can contain input material. Expose only our bounded diagnostic.
        private inline fun <T> checked(message: String, block: () -> T): T = try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid signedMetadata configuration: $message")
        }
    }
}
