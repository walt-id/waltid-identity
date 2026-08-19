package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.Digest as SignumDigest
import at.asitplus.signum.indispensable.SignatureAlgorithm as SignumSignatureAlgorithm
import at.asitplus.signum.indispensable.CryptoPublicKey as SignumCryptoPublicKey
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.RSAPadding
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Sequence
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.keys.SoftwareKey
import kotlin.collections.plus

class SignumCrypto2SignatureValidationImpl : SignumSignatureValidationImpl {

    override suspend fun verifySignumSignature(
        cryptoRuntime: CryptoRuntime,
        publicKey: CryptoPublicKey,
        algorithm: SignatureAlgorithm,
        signedData: ByteArray,
        signature: CryptoSignature
    ): Boolean {

        val issuerKey = cryptoRuntime.restore(SignumSpki(publicKey))
        val raw = rawDssBytes(algorithm, publicKey, signature.encodeToTlv())

        val verifier =
            checkNotNull(issuerKey.capabilities.verifier) { "Can not use Key from CryptoRuntime to verify signature" }
        return verifier.verify(signedData, raw, algorithm.toCrypto2())
    }

    private fun rawDssBytes(
        algorithm: SignumSignatureAlgorithm,
        publicKey: SignumCryptoPublicKey,
        signature: Asn1Element
    ): ByteArray =
        when (algorithm) {
            is SignumSignatureAlgorithm.ECDSA -> {
                val curve = (publicKey as? SignumCryptoPublicKey.EC)?.curve
                checkNotNull(curve) { "No curve set" }
                when (curve) {
                    ECCurve.SECP_256_R_1 -> concatSignatureComponents(signature.asSequence(), 32)
                    ECCurve.SECP_384_R_1 -> concatSignatureComponents(signature.asSequence(), 48)
                    ECCurve.SECP_521_R_1 -> concatSignatureComponents(signature.asSequence(), 66)
                }
            }

            is SignumSignatureAlgorithm.RSA -> {
                val bitString = Asn1BitString(signature.asPrimitive().content)
                // remove first byte padding bits (0x00)
                bitString.rawBytes.sliceArray(1 until bitString.rawBytes.size)
            }
        }

    private fun concatSignatureComponents(
        signatureComponentValues: Asn1Sequence,
        componentLengthBytes: Int
    ): ByteArray {
        val it = signatureComponentValues.iterator()
        var raw = ByteArray(0)
        while (it.hasNext()) {
            val componentValue = it.next().asPrimitive().content
            val fixedComponentValue = if (componentValue.size == componentLengthBytes) {
                componentValue
            } else if (componentValue.size > componentLengthBytes) {
                componentValue.copyOfRange(componentValue.size - componentLengthBytes, componentValue.size)
            } else {
                // need to add leading zero bytes
                ByteArray(componentLengthBytes - componentValue.size) + componentValue
            }
            raw += fixedComponentValue
        }
        return raw
    }

    private class SignumSpki(publicKey: SignumCryptoPublicKey) : SoftwareKey {
        val keyInfo = SignumPublicKeyInfo.ofCryptoPublicKey(publicKey)
        override val storedKey = keyInfo.keySpec
    }

    private fun SignumSignatureAlgorithm.toCrypto2(): id.walt.crypto2.algorithms.SignatureAlgorithm =
        when (this) {
            is SignumSignatureAlgorithm.ECDSA -> {
                id.walt.crypto2.algorithms.SignatureAlgorithm.Ecdsa(
                    digest = digest?.toCrypto2()
                        ?: throw IllegalArgumentException("No digest specified in signature algorithm"),
                )
            }

            is SignumSignatureAlgorithm.RSA -> {
                val digest = digest.toCrypto2()
                when (padding) {
                    RSAPadding.PKCS1 -> id.walt.crypto2.algorithms.SignatureAlgorithm.RsaPkcs1(digest)
                    RSAPadding.PSS -> id.walt.crypto2.algorithms.SignatureAlgorithm.RsaPss(digest)
                }
            }
        }

    private fun SignumDigest.toCrypto2(): DigestAlgorithm =
        when (this) {
            SignumDigest.SHA1 -> DigestAlgorithm.SHA_1
            SignumDigest.SHA256 -> DigestAlgorithm.SHA_256
            SignumDigest.SHA384 -> DigestAlgorithm.SHA_384
            SignumDigest.SHA512 -> DigestAlgorithm.SHA_512
        }



}