package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.SignatureAlgorithm
import id.walt.crypto.keys.jwk.JWKKey
import kotlin.io.encoding.Base64

// Preserve the existing JS/Node verification behavior in this change.
// Unlike JVM/iOS, the current JS key API does not expose a typed invalid-signature failure.
@Suppress("UNUSED_PARAMETER")
internal actual suspend fun verifySignumSignature(
    publicKey: CryptoPublicKey,
    algorithm: SignatureAlgorithm,
    signedData: ByteArray,
    signature: CryptoSignature,
): Boolean {
    val publicKeyPem = Base64.encode(publicKey.encodeToTlv().derEncoded)
        .chunked(64)
        .joinToString("\n")
        .let { "-----BEGIN PUBLIC KEY-----\n$it\n-----END PUBLIC KEY-----" }
    val key = JWKKey.importPEM(publicKeyPem).getOrThrow()
    return key.verifyRaw(
        signed = signature.encodeToDer(),
        detachedPlaintext = signedData,
    ).fold(
        onSuccess = { true },
        onFailure = { cause ->
            if (cause.message?.contains("verification") == true) false else throw cause
        },
    )
}
