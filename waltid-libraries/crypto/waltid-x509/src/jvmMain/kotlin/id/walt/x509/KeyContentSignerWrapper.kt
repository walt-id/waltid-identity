package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.x509.iso.getJcaSigningAlgorithmNameFromKeyType
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.ByteArrayOutputStream
import java.io.OutputStream

internal class KeyContentSignerWrapper(
    val key: Key,
) : ContentSigner {

    private val algorithmIdentifier = getJcaSigningAlgorithmNameFromKeyType(key.keyType)
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