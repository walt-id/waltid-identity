package id.walt.wallet2.mobile

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Runtime-generated reader CA and re-signed certificate mutations; no retained private-key material. */
internal class ReaderCertificateProfileFixture private constructor(
    val root: X509Certificate,
    val leaf: X509Certificate,
    val readerKey: Key,
    private val rootKey: Key,
) {
    /** Signs a complete direct CRL for wallet integration; parser interoperability uses independent vectors. */
    suspend fun crl(
        revoked: List<X509Certificate> = emptyList(),
        thisUpdate: Instant = Clock.System.now() - 1.seconds,
        nextUpdate: Instant = thisUpdate + 1.hours,
    ): ByteArray {
        fun time(value: Instant): Der = Der(0x17,
            Instant.fromEpochSeconds(value.epochSeconds).toString().filter(Char::isDigit).drop(2).plus("Z").encodeToByteArray())
        fun extension(oid: Int, value: Der): Der = Der.container(0x30, listOf(
            Der(6, byteArrayOf(0x55, 0x1d, oid.toByte())), Der(4, value.encode()),
        ))
        val algorithm = Der.read(root.encodedDer.toByteArray()).children()[1]
        val items = mutableListOf(Der(2, byteArrayOf(1)), algorithm, Der.read(root.data.subjectDnRaw.toByteArray()),
            time(thisUpdate), time(nextUpdate))
        if (revoked.isNotEmpty()) items += Der.container(0x30, revoked.map { certificate ->
            Der.container(0x30, listOf(Der(2, certificate.data.serialNumberRaw.toByteArray()), time(thisUpdate)))
        })
        items += Der.container(0xa0, listOf(Der.container(0x30, listOf(
            extension(35, Der.container(0x30, listOf(Der(0x80, root.data.extensionSubjectKeyIdentifier!!.keyIdentifier.toByteArray())))),
            extension(20, Der(2, byteArrayOf(1))),
        ))))
        val tbs = Der.container(0x30, items)
        val signature = rootKey.capabilities.signer!!.sign(tbs.encode(), certificateAlgorithm)
        return Der.container(0x30, listOf(tbs, algorithm, Der(3, byteArrayOf(0) + signature))).encode()
    }

    /**
     * Returns a copy of [leaf] with one profile violation applied, re-signed by the root key so that the
     * signature itself stays valid and only the named profile rule is broken.
     *
     * Numeric cases mirror the mDL_SM_mdocRAuth_UF_NN reader-authentication certificate unhappy flows; the
     * "contact-*" cases add an issuer alternative name carrying contact information.
     *
     * Indices into the TBSCertificate children: 0 = [0] version, 1 = serialNumber, 2 = signature algorithm,
     * 3 = issuer, 4 = validity, 5 = subject, 6 = subjectPublicKeyInfo, then the [3] extensions.
     */
    suspend fun modified(case: String): ByteArray {
        val certificate = Der.read(leaf.encodedDer.toByteArray()).children().toMutableList()
        val tbs = certificate[0].children().toMutableList()
        // Edits the list of extensions in the [3] EXPLICIT extensions field.
        fun extensions(change: (MutableList<Der>) -> Unit) {
            val i = tbs.indexOfFirst { it.tag == 0xa3 }
            val values = tbs[i].children().single().children().toMutableList()
            change(values)
            tbs[i] = Der.container(0xa3, listOf(Der.container(0x30, values)))
        }
        // Edits one extension (matched by its 2.5.29.x OID last byte); the last field is its OCTET STRING value.
        fun extension(oidLastByte: Int, change: (MutableList<Der>) -> Unit) = extensions { values ->
            val i = values.indexOfFirst { it.children().first().content.contentEquals(byteArrayOf(0x55, 0x1d, oidLastByte.toByte())) }
            val fields = values[i].children().toMutableList()
            change(fields)
            values[i] = Der.container(0x30, fields)
        }
        when (case) {
            // Appends an issuerAltName (2.5.29.18) whose single GeneralName is the contact string, as a
            // URI (0x86), rfc822Name (0x81) or dNSName (0x82). The "-critical" variant is a URI marked critical.
            "contact-uri", "contact-email", "contact-dns", "contact-critical" -> extensions { values ->
                val tag = when (case) { "contact-email" -> 0x81; "contact-dns" -> 0x82; else -> 0x86 }
                val fields = mutableListOf(Der(6, byteArrayOf(0x55, 0x1d, 18)))
                if (case == "contact-critical") fields += Der(1, byteArrayOf(0xff.toByte()))
                fields += Der(4, Der.container(0x30, listOf(Der(tag, "https://issuer.example/contact".encodeToByteArray()))).encode())
                values += Der.container(0x30, fields)
            }
            "01" -> tbs.removeAt(1) // Required serial number.
            // Replaces the inner (TBS) signature AlgorithmIdentifier with ecdsa-with-SHA384 (1.2.840.10045.4.3.3).
            // The outer signatureAlgorithm stays ecdsa-with-SHA256 and the real signature is SHA-256, so the two
            // algorithm fields disagree (RFC 5280 requires them to be identical) and SHA-384 is off-profile.
            "02" -> tbs[2] = Der.container(0x30, listOf(Der(6, byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 4, 3, 3))))
            // Moves notBefore to 2099-01-01, so the certificate is not yet valid.
            "05" -> {
                val validity = tbs[4].children().toMutableList()
                validity[0] = Der(0x18, "20990101000000Z".encodeToByteArray())
                tbs[4] = Der.container(0x30, validity)
            }
            // Replaces the subject with an empty DN (empty SEQUENCE); a reader certificate must name its subject.
            "07" -> tbs[5] = Der.container(0x30, emptyList())
            // Corrupts the subjectPublicKeyInfo: 08 = wrong key algorithm OID, 09 = wrong curve, 10 = invalid point.
            "08", "09", "10" -> {
                val spki = tbs[6].children().toMutableList()
                if (case == "10") {
                    val point = spki[1].content.copyOf()
                    // A zero affine point is deterministically outside P-256.
                    point.fill(0, 2)
                    spki[1] = Der(3, point)
                } else {
                    val algorithm = spki[0].children().toMutableList()
                    // 08: algorithm OID 1.2.840.10045.2 (the id-publicKeyType arc) instead of id-ecPublicKey (...2.1).
                    // 09: curve parameter OID 1.3.36.3.3.2.8.1.1.6 (a Brainpool curve by OID arc) instead of P-256.
                    if (case == "08") algorithm[0] = Der(6, byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 2))
                    else algorithm[1] = Der(6, byteArrayOf(0x2b, 0x24, 3, 3, 2, 8, 1, 1, 6))
                    spki[0] = Der.container(0x30, algorithm)
                }
                tbs[6] = Der.container(0x30, spki)
            }
            // Removes the first extension from the leaf, so a required extension is missing.
            "11" -> extensions { it.removeAt(0) }
            // Appends an unrecognised extension (OID 2.5.29.79) marked critical, which a verifier must reject.
            "12" -> extensions { it += Der.container(0x30, listOf(
                Der(6, byteArrayOf(0x55, 0x1d, 79)), Der(1, byteArrayOf(0xff.toByte())), Der(4, byteArrayOf(5, 0)),
            )) }
            // Appends a copy of the first extension, so the same extension appears twice (RFC 5280 forbids this).
            "13" -> extensions { it += it.first() }
            // Flips the last bit of the authorityKeyIdentifier (2.5.29.35) value, so it no longer matches the issuer's SKI.
            "14" -> extension(35) { fields ->
                val bytes = fields.last().content.copyOf()
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                fields[fields.lastIndex] = Der(4, bytes)
            }
            // Replaces keyUsage (2.5.29.15) with an empty BIT STRING (no bits set), dropping digitalSignature.
            "15" -> extension(15) { fields -> fields[fields.lastIndex] = Der(4, byteArrayOf(3, 2, 0, 0)) }
            // Changes the last byte of the extKeyUsage (2.5.29.37) OID so it is not the mdoc reader-authentication EKU.
            "16" -> extension(37) { fields ->
                val bytes = fields.last().content.copyOf()
                bytes[bytes.lastIndex] = 7 // A different EKU; reader-authentication purpose is absent.
                fields[fields.lastIndex] = Der(4, bytes)
            }
            else -> error("Unknown certificate mutation $case")
        }
        // Re-sign the modified TBS with the root key (outer signatureAlgorithm is kept as-is) and self-check it.
        val exactTbs = Der.container(0x30, tbs).encode()
        val signature = rootKey.capabilities.signer!!.sign(exactTbs, certificateAlgorithm)
        assertTrue(rootKey.capabilities.verifier!!.verify(exactTbs, signature, certificateAlgorithm), "Re-signed certificate $case")
        return Der.container(0x30, listOf(Der.read(exactTbs), certificate[1], Der(3, byteArrayOf(0) + signature))).encode()
    }

    companion object {
        private val certificateAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        suspend fun create(
            runtime: CryptoRuntime,
            rootHasCrl: Boolean = true,
            parent: ReaderCertificateProfileFixture? = null,
        ): ReaderCertificateProfileFixture {
            suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId(id + if (parent == null) "" else "-intermediate"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            val rootKey = key("profile-reader-root")
            val readerKey = key("profile-reader-leaf")
            val root = if (parent != null) X509CertificateUtil.createCertificate(parent.rootKey, parent.root, certificateAlgorithm) {
                subjectDn = "CN=Profile reader intermediate"
                subjectPublicKey(rootKey)
                extensionSubjectKeyIdentifier()
                extensionBasicConstraints { cA = true }
                extensionCrlDistributionPoints { addUriDistributionPoint("https://reader.example/intermediate-crl") }
                extensionKeyUsage { critical = true; addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign, KeyUsageExtension.KeyUsage.cRLSign) }
            } else X509CertificateUtil.createSelfSignedCertificate(rootKey, certificateAlgorithm) {
                subjectDn = "CN=Profile reader root"
                if (rootHasCrl) extensionCrlDistributionPoints { addUriDistributionPoint("https://reader.example/ca-crl") }
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
