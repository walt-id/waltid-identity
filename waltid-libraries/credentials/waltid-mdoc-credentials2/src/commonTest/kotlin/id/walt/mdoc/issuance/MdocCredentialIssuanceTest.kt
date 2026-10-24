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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MdocCredentialIssuanceTest {

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `issuer and holder keys support successive credential issuance and verification`() = runTest {
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

        repeat(2) {
            val document = Document(docType = docType, issuerSigned = issue())
            assertTrue(
                verifyIssuerAuthentication(document = document, validateCertificateConstraints = false)
                    .certificateChain.isNotEmpty(),
            )
            assertEquals(JsonObject(data.namespaces), document.issuerSigned.namespacesToJson())
        }
    }
}
