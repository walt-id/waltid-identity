package id.walt.certificate.x509

import at.asitplus.signum.indispensable.isSupported
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
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
        val certificates = listOf(rootCert) + certBlocks.map { base64Str ->
            val derBytes = Base64.decode(base64Str) // Use a KMP-friendly Base64 decoder
            SignumCertificate.decodeFromDer(derBytes)
        }

        // 3. Sequentially verify the cryptographic trust path
        for (i in 0 until certificates.size - 1) {
            val childCert = certificates[i]
            val parentCert = certificates[i + 1]

            // Ensure the parent's subject actually issued the child's certificate
            assertEquals(
                childCert.tbsCertificate.issuerName, parentCert.tbsCertificate.subjectName,
                "child cert issuer dn '${childCert.tbsCertificate.issuerName} not equal to authority cert subject dn '${parentCert.tbsCertificate.subjectName}'"
            )

            // Extract the parent's public key to verify the child's signature
            val parentPublicKey = parentCert.tbsCertificate.decodedPublicKey.getOrThrow()

            val sigDesc = childCert.signatureAlgorithm
            assertTrue(sigDesc.isSupported())

            // Match the signature algorithm utilized by the child certificate
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
}