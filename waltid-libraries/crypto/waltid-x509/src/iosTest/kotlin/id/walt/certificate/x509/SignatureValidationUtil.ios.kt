package id.walt.certificate.x509

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1Null
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.asn1.encoding.parse
import at.asitplus.signum.indispensable.isSupported
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

actual object SignatureValidationUtil {

    actual fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {

        val rootCert = SignumCertificate.decodeFromPem(selfSignedCaPem).getOrThrow()

        // 1. Parse the unified PEM chain string into distinct Base64 blocks
        val certBlocks = chainPem
            .split("-----END CERTIFICATE-----")
            .map { it.replace("-----BEGIN CERTIFICATE-----", "").trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace(Regex("\\s+"), "") } // Remove newlines and spaces

        assertFalse(certBlocks.isEmpty(), "No certificates found in the PEM chain.")

        // 2. Decode and parse each certificate block using Signum's ASN.1 parser
        val allCertificates = certBlocks.map { base64Str ->
            val derBytes = Base64.decode(base64Str) // Use a KMP-friendly Base64 decoder
            SignumCertificate.decodeFromDer(derBytes)
        }

        assertEquals(1, allCertificates.size, "Only a root or a single child certificate is supported.")
        val certificateToVerify = allCertificates.single()
        if (certificateToVerify.tbsCertificate.subjectName == certificateToVerify.tbsCertificate.issuerName) {
                assertEquals(
                    ByteString(rootCert.tbsCertificate.serialNumber),
                    ByteString(certificateToVerify.tbsCertificate.serialNumber),
                    "Root cert serial number must match."
                )
        }
        verifyCertSignature(rootCert, certificateToVerify)
    }

    actual fun verifyCsrPem(csrPem: String) {

        // Decode the raw DER bytes and parse via Signum's ASN.1 sequence
        val csr = Pkcs10CertificationRequest.decodeFromPem(csrPem).getOrThrow()

        // Extract the embedded public key which must have generated the signature
        val csrPublicKey = csr.tbsCsr.publicKey

        //  Read the unvalidated signature algorithm description
        val description = csr.signatureAlgorithm

        // Validate the algorithm structure and trigger Kotlin's smart-cast contract
        assertTrue(description.isSupported())

        // Access the smart-cast algorithm representation
        val signatureAlgorithm = description.algorithm

        // Acquire the multiplatform Supreme Verifier engine
        val verifier = signatureAlgorithm.verifierFor(csrPublicKey).getOrThrow()

        // Gather the raw components: To-Be-Signed (TBS) binary and Signature block
        val tbsData = csr.tbsCsr.encodeToDer()
        val cryptoSignature = csr.decodedSignature.getOrThrow()

        // Perform the self-signed cryptographic assertion check
        assertTrue(verifier.verify(tbsData, cryptoSignature).isSuccess)
    }

    fun verifyCertSignature(parentCert: SignumCertificate, childCert: SignumCertificate) {

        // Ensure the parent's subject actually issued the child's certificate
        assertEquals(
            childCert.tbsCertificate.issuerName, parentCert.tbsCertificate.subjectName,
            "child cert issuer dn '${childCert.tbsCertificate.issuerName} not equal to authority cert subject dn '${parentCert.tbsCertificate.subjectName}'"
        )

        // Extract the parent's public key to verify the child's signature
        val parentPublicKey = fixedPublicKeyInfo(parentCert.tbsCertificate.rawPublicKey.derEncoded)
        val sigDesc = childCert.signatureAlgorithm
        assertTrue(sigDesc.isSupported())
        val signatureAlgorithm = sigDesc.algorithm

        // Acquire the matching Signum cryptographic verifier engine
        val verifier = signatureAlgorithm.verifierFor(parentPublicKey).getOrThrow()
        val cryptoSignature = childCert.decodedSignature.getOrThrow()
        val tbsData = childCert.tbsCertificate.encodeToDer()

        // Verify the child's signed data against its signature
        val isValidSignature = verifier.verify(tbsData, cryptoSignature)
        assertTrue(
            isValidSignature.isSuccess,
            "Signature verification of child certificate against parent certificate failed."
        )
    }

    private fun fixedPublicKeyInfo(keyInfoRaw: ByteArray): CryptoPublicKey {
        val infoSeq = Asn1Element.parse(keyInfoRaw).asSequence()
        assertTrue(infoSeq.children.size == 2, "Illegal Subject Public Key Info")
        val algInfo = infoSeq.first().asSequence()
        return if (algInfo.children.size == 2) {
            CryptoPublicKey.decodeFromDer(keyInfoRaw)
        } else {
            //RSA doesn't require parameter but Signum expects sequence of size 2
            val fixed = Asn1.Sequence {
                +Asn1.Sequence {
                    +algInfo.children.first()
                    +Asn1Null
                }
                +infoSeq.children[1]
            }
            val result = CryptoPublicKey.decodeFromTlv(fixed)
            result
        }
    }
}
