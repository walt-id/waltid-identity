package id.walt.crypto.keys

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

// Java Specific implementation
fun convertDERtoIEEEP1363(sig: ByteArray): ByteArray {
    Security.addProvider(BouncyCastleProvider())

    // Parse the DER-encoded signature to extract r and s
    val asn1InputStream = ASN1InputStream(sig)
    val seq = asn1InputStream.readObject() as DLSequence
    val r = seq.getObjectAt(0) as ASN1Integer
    val s = seq.getObjectAt(1) as ASN1Integer

    // Convert r and s to 32-byte arrays
    val rBytes = r.positiveValue.toByteArray()
    val sBytes = s.positiveValue.toByteArray()
    val fixedRBytes = ByteArray(32)
    val fixedSBytes = ByteArray(32)

    // Correct potential leading zeros and copy bytes into the fixed arrays
    System.arraycopy(rBytes, maxOf(0, rBytes.size - 32), fixedRBytes, maxOf(0, 32 - rBytes.size), minOf(32, rBytes.size))
    System.arraycopy(sBytes, maxOf(0, sBytes.size - 32), fixedSBytes, maxOf(0, 32 - sBytes.size), minOf(32, sBytes.size))

    // Concatenate r and s for IEEE P1363 format
    return fixedRBytes + fixedSBytes
}
