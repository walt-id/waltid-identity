package id.walt.issuer.services.onboarding

import id.walt.crypto.keys.Key
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream


internal class KeyContentSignerWrapper(
    private val algorithmIdentifier: AlgorithmIdentifier,
    private val key: Key,
): ContentSigner {

    private val outputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algorithmIdentifier

    override fun getOutputStream(): OutputStream = outputStream

    override fun getSignature(): ByteArray {
        val data = outputStream.toByteArray()
        return runBlocking {
            key.signRaw(data) as ByteArray
        }
    }
}