package id.walt.crypto2.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.Digest
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.RSAPadding
import at.asitplus.signum.indispensable.SignatureAlgorithm as SignumSignatureAlgorithm
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.dsl.DISCOURAGED
import at.asitplus.signum.supreme.dsl.PREFERRED
import at.asitplus.signum.supreme.dsl.REQUIRED
import at.asitplus.signum.supreme.os.PlatformSignerConfigurationBase
import at.asitplus.signum.supreme.os.PlatformSigningKeyConfigurationBase
import at.asitplus.signum.supreme.os.PlatformSigningProviderSigner
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.verifierFor
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

internal fun PlatformSigningKeyConfigurationBase<*>.configureSignumKey(
    spec: KeySpec,
    usages: Set<KeyUsage>,
    policy: SignumKeyPolicy,
) {
    when (spec) {
        is KeySpec.Ec -> ec {
            curve = spec.curve.toSignumCurve()
            digests = setOf(spec.curve.nativeDigest().toSignumDigest())
            purposes {
                signing = usages.any { it == KeyUsage.SIGN || it == KeyUsage.VERIFY || it == KeyUsage.KEY_AGREEMENT }
                keyAgreement = KeyUsage.KEY_AGREEMENT in usages
            }
        }
        is KeySpec.Rsa -> rsa {
            bits = spec.bits
            digests = setOf(Digest.SHA256, Digest.SHA384, Digest.SHA512)
            paddings = setOf(RSAPadding.PKCS1, RSAPadding.PSS)
            purposes {
                signing = KeyUsage.SIGN in usages || KeyUsage.VERIFY in usages
                decrypting = false
            }
        }
        else -> error("Unsupported Signum key specification: $spec")
    }
    if (policy.hardware != SignumHardwarePolicy.DISCOURAGED ||
        policy.authentication !is SignumAuthenticationPolicy.None ||
        policy.attestationChallenge != null
    ) {
        hardware {
            backing = when (policy.hardware) {
                SignumHardwarePolicy.REQUIRED -> REQUIRED
                SignumHardwarePolicy.PREFERRED -> PREFERRED
                SignumHardwarePolicy.DISCOURAGED -> DISCOURAGED
            }
            policy.attestationChallenge?.let { challenge ->
                attestation { this.challenge = challenge.toByteArray() }
            }
            (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.let { auth ->
                protection {
                    timeout = auth.timeoutSeconds.seconds
                    factors {
                        biometry = auth.biometric
                        biometryWithNewFactors = auth.allowNewBiometrics
                        deviceLock = auth.deviceCredential
                    }
                }
            }
        }
    }
}

internal fun PlatformSignerConfigurationBase.configureSignumOperation(
    algorithm: SignatureAlgorithm,
    authentication: SignumAuthenticationPolicy,
) {
    when (algorithm) {
        is SignatureAlgorithm.Ecdsa -> ec { digest = algorithm.digest.toSignumDigest() }
        is SignatureAlgorithm.RsaPkcs1 -> rsa {
            digest = algorithm.digest.toSignumDigest()
            padding = RSAPadding.PKCS1
        }
        is SignatureAlgorithm.RsaPss -> rsa {
            require(algorithm.mgfDigest == algorithm.digest) { "Signum RSA-PSS MGF digest must match message digest" }
            digest = algorithm.digest.toSignumDigest()
            padding = RSAPadding.PSS
        }
        else -> error("Unsupported Signum signature algorithm: $algorithm")
    }
    (authentication as? SignumAuthenticationPolicy.UserPresence)?.let { auth ->
        unlockPrompt {
            message = auth.prompt
            cancelText = auth.cancelText
        }
    }
}

internal class SignumPlatformKeyHandle(
    override val alias: String,
    override val spec: KeySpec,
    override val protectionLevel: SignumProtectionLevel,
    override val attestation: SignumKeyAttestation?,
    private val authentication: SignumAuthenticationPolicy,
    private val signerFor: suspend (SignatureAlgorithm) -> PlatformSigningProviderSigner<*, *>,
    defaultSigner: PlatformSigningProviderSigner<*, *>,
    keyAgreementEnabled: Boolean,
) : SignumPlatformKey {
    override val publicKey = EncodedKey.SpkiDer(BinaryData(defaultSigner.publicKey.encodeToTlv().derEncoded))
    override val signatureAlgorithms = spec.nativeSignatureAlgorithms()
    override val keyAgreementAlgorithms = if (keyAgreementEnabled) setOf(KeyAgreementAlgorithm.Ecdh) else emptySet()

    override suspend fun sign(data: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
        require(algorithm in signatureAlgorithms) { "Unsupported Signum signature algorithm" }
        return when (val result = signerFor(algorithm).sign(data)) {
            is SignatureResult.Success -> result.signature.rawByteArray
            is SignatureResult.Failure -> throw SignumUserCancelledException(result.problem)
            is SignatureResult.Error -> throw result.exception
        }
    }

    override suspend fun verify(data: ByteArray, signature: ByteArray, algorithm: SignatureAlgorithm): Boolean {
        require(algorithm in signatureAlgorithms) { "Unsupported Signum signature algorithm" }
        val signumAlgorithm = algorithm.toSignumAlgorithm()
        val cryptoSignature = when (spec) {
            is KeySpec.Ec -> CryptoSignature.EC.fromRawBytes(spec.curve.toSignumCurve(), signature)
            is KeySpec.Rsa -> CryptoSignature.RSA(signature)
            else -> error("Unsupported Signum key specification")
        }
        return signumAlgorithm.verifierFor(signerFor(algorithm).publicKey).getOrThrow()
            .verify(SignatureInput(data), cryptoSignature).isSuccess
    }

    override suspend fun generateSharedSecret(
        peerPublicKey: EncodedKey,
        algorithm: KeyAgreementAlgorithm,
    ): BinaryData {
        require(algorithm == KeyAgreementAlgorithm.Ecdh && algorithm in keyAgreementAlgorithms) {
            "Unsupported Signum key-agreement algorithm"
        }
        val ecSpec = spec as? KeySpec.Ec ?: throw IllegalArgumentException("Signum ECDH requires an EC key")
        val signer = signerFor(SignatureAlgorithm.Ecdsa(ecSpec.curve.nativeDigest())) as? Signer.ECDSA
            ?: error("Signum key does not expose ECDH")
        val peer = peerPublicKey.toSignumEcdhPeer(ecSpec)
        return BinaryData(signer.keyAgreement(peer).getOrThrow())
    }
}

internal suspend fun EncodedKey.toSignumEcdhPeer(spec: KeySpec.Ec): CryptoPublicKey.EC {
    val spki = try {
        toSpkiDer(spec)
    } catch (cause: Throwable) {
        throw IllegalArgumentException("Signum ECDH peer key must be an EC JWK or SPKI key", cause)
    }
    val peer = try {
        CryptoPublicKey.decodeFromDer(spki.data.toByteArray())
    } catch (cause: Throwable) {
        throw IllegalArgumentException("Signum ECDH peer key must contain valid SPKI DER", cause)
    } as? CryptoPublicKey.EC ?: throw IllegalArgumentException("Signum ECDH peer key must be EC")
    require(peer.curve == spec.curve.toSignumCurve()) { "Signum ECDH peer key curve does not match managed key" }
    return peer
}

internal fun PlatformSigningProviderSigner<*, *>.toAttestation(): SignumKeyAttestation? = attestation?.let {
    SignumKeyAttestation(
        format = "signum-json",
        statement = BinaryData(Json.encodeToString(it).encodeToByteArray()),
    )
}

private fun KeySpec.nativeSignatureAlgorithms(): Set<SignatureAlgorithm> = when (this) {
    is KeySpec.Ec -> setOf(SignatureAlgorithm.Ecdsa(curve.nativeDigest()))
    is KeySpec.Rsa -> listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384, DigestAlgorithm.SHA_512)
        .flatMap { listOf(SignatureAlgorithm.RsaPkcs1(it), SignatureAlgorithm.RsaPss(it, saltLengthBytes = it.size())) }
        .toSet()
    else -> emptySet()
}

private fun SignatureAlgorithm.toSignumAlgorithm(): SignumSignatureAlgorithm = when (this) {
    is SignatureAlgorithm.Ecdsa -> when (digest) {
        DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.ECDSAwithSHA256
        DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.ECDSAwithSHA384
        DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.ECDSAwithSHA512
        else -> error("Unsupported Signum ECDSA digest: ${digest.name}")
    }
    is SignatureAlgorithm.RsaPkcs1 -> when (digest) {
        DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.RSAwithSHA256andPKCS1Padding
        DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.RSAwithSHA384andPKCS1Padding
        DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.RSAwithSHA512andPKCS1Padding
        else -> error("Unsupported Signum RSA digest: ${digest.name}")
    }
    is SignatureAlgorithm.RsaPss -> when (digest) {
        DigestAlgorithm.SHA_256 -> SignumSignatureAlgorithm.RSAwithSHA256andPSSPadding
        DigestAlgorithm.SHA_384 -> SignumSignatureAlgorithm.RSAwithSHA384andPSSPadding
        DigestAlgorithm.SHA_512 -> SignumSignatureAlgorithm.RSAwithSHA512andPSSPadding
        else -> error("Unsupported Signum RSA digest: ${digest.name}")
    }
    else -> error("Unsupported Signum signature algorithm: $this")
}

private fun EcCurve.toSignumCurve(): ECCurve = when (this) {
    EcCurve.P256 -> ECCurve.SECP_256_R_1
    EcCurve.P384 -> ECCurve.SECP_384_R_1
    EcCurve.P521 -> ECCurve.SECP_521_R_1
    else -> error("Unsupported Signum EC curve: $name")
}

private fun EcCurve.nativeDigest(): DigestAlgorithm = when (this) {
    EcCurve.P256 -> DigestAlgorithm.SHA_256
    EcCurve.P384 -> DigestAlgorithm.SHA_384
    EcCurve.P521 -> DigestAlgorithm.SHA_512
    else -> error("Unsupported Signum EC curve: $name")
}

private fun DigestAlgorithm.toSignumDigest(): Digest = when (this) {
    DigestAlgorithm.SHA_256 -> Digest.SHA256
    DigestAlgorithm.SHA_384 -> Digest.SHA384
    DigestAlgorithm.SHA_512 -> Digest.SHA512
    else -> error("Unsupported Signum digest: $name")
}

private fun DigestAlgorithm.size(): Int = when (this) {
    DigestAlgorithm.SHA_256 -> 32
    DigestAlgorithm.SHA_384 -> 48
    DigestAlgorithm.SHA_512 -> 64
    else -> error("Unsupported Signum digest: $name")
}
