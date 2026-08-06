package id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class BouncyContentSigner(val signingKey: Key) : ContentSigner {

    val info: X509SigningAlgorithmInfo
        get() = TODO()

    private val buffer: ByteArrayOutputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier =
        AlgorithmIdentifier(ASN1ObjectIdentifier(info.signingAlgorithmOid))

    override fun getOutputStream(): OutputStream = buffer

    override fun getSignature(): ByteArray = TODO()
}