@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.issuance

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.verification.verifyIssuerAuthentication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * What issuing and verifying one mdoc credential actually costs, with no HTTP, no database and no
 * framework around it.
 *
 * This sizes the protocol floor so that end-to-end figures can be attributed. Measured against it: the
 * enterprise API spends about 32ms of CPU per presentation while the raw ES256 verify underneath is
 * 0.7ms, so the question is how much of the remainder is credential work and how much is plumbing.
 *
 * Phase length is deliberately short so the suite stays fast; raise it with
 * `-Dwaltid.credentialBenchmarkSeconds=30` for a real reading.
 */
class MdocCredentialThroughputTest {

    private val durationMillis =
        (System.getProperty("waltid.credentialBenchmarkSeconds")?.toLongOrNull() ?: 3L) * 1_000L

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `mdoc issuance and credential verification throughput`() = runTest {
        val issuerKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val holderKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val holderCoseKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Benchmark mdoc issuer" }
        val issuerCertificate = listOf(CoseCertificate(certificate.encodedDer.toByteArray()))
        val docType = "org.iso.18013.5.1.mDL"
        val data = MdocIssuer.MdocUniversalIssuanceData(
            namespaces = mapOf(
                "org.iso.18013.5.1" to JsonObject(
                    mapOf(
                        "family_name" to JsonPrimitive("Doe"),
                        "given_name" to JsonPrimitive("Jane"),
                        "birth_date" to JsonPrimitive("1990-01-01"),
                        "document_number" to JsonPrimitive("D1234567"),
                        "issuing_country" to JsonPrimitive("AT"),
                    )
                )
            )
        )

        suspend fun issue() = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = issuerCertificate,
            holderKey = holderCoseKey,
            docType = docType,
            data = data,
        )

        // A credential to verify repeatedly, and a JIT warm-up for both phases.
        val document = Document(docType = docType, issuerSigned = issue())
        repeat(50) {
            issue()
            verifyIssuerAuthentication(document = document, validateCertificateConstraints = false)
        }
        assertTrue(
            verifyIssuerAuthentication(document = document, validateCertificateConstraints = false)
                .certificateChain.isNotEmpty(),
            "the benchmarked credential must actually verify - this throws if it does not - or the loop " +
                "measures nothing",
        )

        suspend fun measure(label: String, operation: suspend () -> Unit) {
            var count = 0L
            val started = TimeSource.Monotonic.markNow()
            while (started.elapsedNow().inWholeMilliseconds < durationMillis) {
                operation()
                count++
            }
            val millis = started.elapsedNow().inWholeMilliseconds
            val perSecond = count * 1000.0 / millis
            println(
                "MDOC %-8s %,8d ops in %,6d ms = %,8.1f ops/s single thread (%.3f ms each), %,9.1f ops/s on 24 cores"
                    .format(label, count, millis, perSecond, millis.toDouble() / count, perSecond * 24)
            )
        }

        measure("issue") { issue() }
        measure("verify") { verifyIssuerAuthentication(document = document, validateCertificateConstraints = false) }
    }
}
