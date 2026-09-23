package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.requireCompatibleWith
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class BouncyContentSigner(
    val signingKey: Key,
    val signatureAlgorithm: SignatureAlgorithm
) : ContentSigner {

    init {
        signatureAlgorithm.requireCompatibleWith(signingKey)
    }

    private val signer = requireNotNull(signingKey.capabilities.signer) { "X.509 signing key does not support signing" }

    val info: X509SigningAlgorithmInfo =
        X509SigningAlgorithmInfo.ofKey(signingKey, signatureAlgorithm)

    private val buffer: ByteArrayOutputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier =
        AlgorithmIdentifier(ASN1ObjectIdentifier(info.signingAlgorithmOid))

    override fun getOutputStream(): OutputStream = buffer

    override fun getSignature(): ByteArray = runBlocking {
        signer.sign(buffer.toByteArray(), signatureAlgorithm)
    }
}