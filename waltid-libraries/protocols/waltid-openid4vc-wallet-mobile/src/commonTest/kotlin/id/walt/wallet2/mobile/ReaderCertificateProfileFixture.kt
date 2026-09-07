package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.Companion.extensionCrlDistributionPoints
import id.walt.certificate.x509.extension.ExtendedKeyUsageExtension.Companion.extensionExtendedKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.x509.MdocReaderAuthenticationEkuOid
import kotlin.test.assertTrue

/** Runtime-generated reader CA and re-signed certificate mutations; no retained private-key material. */
internal class ReaderCertificateProfileFixture private constructor(
    val root: X509Certificate,
    val leaf: X509Certificate,
    val readerKey: Key,
    private val rootKey: Key,
) {
    suspend fun modified(case: String): ByteArray {
        val certificate = Der.read(leaf.encodedDer.toByteArray()).children().toMutableList()
        val tbs = certificate[0].children().toMutableList()
        fun extensions(change: (MutableList<Der>) -> Unit) {
            val i = tbs.indexOfFirst { it.tag == 0xa3 }
            val values = tbs[i].children().single().children().toMutableList()
            change(values)
            tbs[i] = Der.container(0xa3, listOf(Der.container(0x30, values)))
        }
        fun extension(oidLastByte: Int, change: (MutableList<Der>) -> Unit) = extensions { values ->
            val i = values.indexOfFirst { it.children().first().content.contentEquals(byteArrayOf(0x55, 0x1d, oidLastByte.toByte())) }
            val fields = values[i].children().toMutableList()
            change(fields)
            values[i] = Der.container(0x30, fields)
        }
        when (case) {
            "01" -> tbs.removeAt(1) // Required serial number.
            "02" -> tbs[2] = Der.container(0x30, listOf(Der(6, byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 4, 3, 3))))
            "05" -> {
                val validity = tbs[4].children().toMutableList()
                validity[0] = Der(0x18, "20990101000000Z".encodeToByteArray())
                tbs[4] = Der.container(0x30, validity)
            }
            "07" -> tbs[5] = Der.container(0x30, emptyList())
            "08", "09", "10" -> {
                val spki = tbs[6].children().toMutableList()
                if (case == "10") {
                    val point = spki[1].content.copyOf()
                    // A zero affine point is deterministically outside P-256.
                    point.fill(0, 2)
                    spki[1] = Der(3, point)
                } else {
                    val algorithm = spki[0].children().toMutableList()
                    if (case == "08") algorithm[0] = Der(6, byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 2))
                    else algorithm[1] = Der(6, byteArrayOf(0x2b, 0x24, 3, 3, 2, 8, 1, 1, 6))
                    spki[0] = Der.container(0x30, algorithm)
                }
                tbs[6] = Der.container(0x30, spki)
            }
            "11" -> extensions { it.removeAt(0) }
            "12" -> extensions { it += Der.container(0x30, listOf(
                Der(6, byteArrayOf(0x55, 0x1d, 79)), Der(1, byteArrayOf(0xff.toByte())), Der(4, byteArrayOf(5, 0)),
            )) }
            "13" -> extensions { it += it.first() }
            "14" -> extension(35) { fields ->
                val bytes = fields.last().content.copyOf()
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                fields[fields.lastIndex] = Der(4, bytes)
            }
            "15" -> extension(15) { fields -> fields[fields.lastIndex] = Der(4, byteArrayOf(3, 2, 0, 0)) }
            "16" -> extension(37) { fields ->
                val bytes = fields.last().content.copyOf()
                bytes[bytes.lastIndex] = 7 // A different EKU; reader-authentication purpose is absent.
                fields[fields.lastIndex] = Der(4, bytes)
            }
            else -> error("Unknown certificate mutation $case")
        }
        val exactTbs = Der.container(0x30, tbs).encode()
        val signature = rootKey.capabilities.signer!!.sign(exactTbs, certificateAlgorithm)
        assertTrue(rootKey.capabilities.verifier!!.verify(exactTbs, signature, certificateAlgorithm), "Re-signed certificate $case")
        return Der.container(0x30, listOf(Der.read(exactTbs), certificate[1], Der(3, byteArrayOf(0) + signature))).encode()
    }

    companion object {
        private val certificateAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        suspend fun create(runtime: CryptoRuntime): ReaderCertificateProfileFixture {
            suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            val rootKey = key("profile-reader-root")
            val readerKey = key("profile-reader-leaf")
            val root = X509CertificateUtil.createSelfSignedCertificate(rootKey, certificateAlgorithm) {
                subjectDn = "CN=Profile reader root"
                extensionKeyUsage { critical = true; addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign) }
            }
            val leaf = X509CertificateUtil.createCertificate(rootKey, root, certificateAlgorithm) {
                subjectDn = "CN=Profile reader"
                subjectPublicKey(readerKey)
                extensionSubjectKeyIdentifier()
                extensionKeyUsage { critical = true; addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
                extensionExtendedKeyUsage { critical = true; addKeyUsage(MdocReaderAuthenticationEkuOid) }
                extensionCrlDistributionPoints { addUriDistributionPoint("https://reader.example/crl") }
            }
            return ReaderCertificateProfileFixture(root, leaf, readerKey, rootKey)
        }
    }
}

/** Small DER fixture editor, used only with certificates generated by this test suite. */
private data class Der(val tag: Int, val content: ByteArray) {
    fun encode(): ByteArray {
        val n = content.size
        val length = when {
            n < 128 -> byteArrayOf(n.toByte())
            n < 256 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n ushr 8).toByte(), n.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + content
    }
    fun children(): List<Der> = buildList {
        var cursor = 0
        while (cursor < content.size) {
            val node = read(content.copyOfRange(cursor, content.size))
            add(node)
            cursor += node.encode().size
        }
        check(cursor == content.size)
    }
    companion object {
        fun read(bytes: ByteArray): Der {
            var cursor = 2
            var length = bytes[1].toInt() and 0xff
            if (length >= 128) {
                val count = length and 0x7f
                require(count in 1..2)
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[cursor++].toInt() and 0xff) }
            }
            return Der(bytes[0].toInt() and 0xff, bytes.copyOfRange(cursor, cursor + length))
        }
        fun container(tag: Int, children: List<Der>) = Der(tag, children.fold(byteArrayOf()) { bytes, child -> bytes + child.encode() })
    }
}
