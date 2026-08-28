@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSignDetached
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderAuthenticationTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val transcript = SessionTranscript.forQr(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))

    @Test
    fun `reader authentication separates absence malformed invalid validity and trust`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val absent = verifier(ReaderTrustState.TRUSTED).verify(unsigned, transcript)
        assertIs<ReaderAuthenticationValidity.Absent>(absent.documents.single().validity)
        assertEquals(emptyList(), absent.wholeRequest)

        val malformedRequest = unsigned.copy(
            docRequests = listOf(
                unsigned.docRequests.single().copy(
                    readerAuth = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1)),
                )
            )
        )
        assertIs<ReaderAuthenticationValidity.Malformed>(
            verifier(ReaderTrustState.TRUSTED).verify(malformedRequest, transcript).documents.single().validity
        )

        val signed = signedRequest(unsigned, document = true, whole = false)
        val tamperedSignature = requireNotNull(signed.docRequests.single().readerAuth).let { signature ->
            signature.copy(signature = signature.signature.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        }
        val invalid = signed.copy(docRequests = listOf(signed.docRequests.single().copy(readerAuth = tamperedSignature)))
        assertIs<ReaderAuthenticationValidity.Invalid>(
            verifier(ReaderTrustState.TRUSTED).verify(invalid, transcript).documents.single().validity
        )

        listOf(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            ReaderTrustState.REVOKED,
            ReaderTrustState.TRUSTED,
        ).forEach { trustState ->
            val result = verifier(trustState).verify(signed, transcript).documents.single()
            val validity = assertIs<ReaderAuthenticationValidity.Valid>(result.validity)
            assertEquals(ReaderAuthenticationScope.DOCUMENT, validity.evidence.scope)
            assertEquals(0, validity.evidence.documentRequestIndex)
            assertEquals(0, validity.evidence.authenticationIndex)
            assertEquals(trustState, result.trust.state)
        }
    }

    @Test
    fun `ReaderAuthAll binds the exact request while document authentication remains independently absent`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val signed = signedRequest(unsigned, document = false, whole = true)
        val verified = verifier(ReaderTrustState.TRUSTED).verify(signed, transcript)

        assertIs<ReaderAuthenticationValidity.Absent>(verified.documents.single().validity)
        val whole = assertIs<ReaderAuthenticationValidity.Valid>(verified.wholeRequest.single().validity)
        assertEquals(ReaderAuthenticationScope.WHOLE_REQUEST, whole.evidence.scope)
        assertEquals(null, whole.evidence.documentRequestIndex)
        assertEquals(0, whole.evidence.authenticationIndex)

        val changedItems = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("family_name")))
            .docRequests.single().itemsRequest
        val changed = signed.copy(
            docRequests = listOf(signed.docRequests.single().copy(itemsRequest = changedItems)),
        )
        assertIs<ReaderAuthenticationValidity.Invalid>(
            verifier(ReaderTrustState.TRUSTED).verify(changed, transcript).wholeRequest.single().validity
        )
    }

    @Test
    fun `multiple whole-request authentications retain distinct statement indices`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val signed = signedRequest(unsigned, document = false, whole = true)
        val authentication = requireNotNull(signed.readerAuthAll).single()

        val verified = verifier(ReaderTrustState.TRUSTED).verify(
            signed.copy(readerAuthAll = listOf(authentication, authentication)),
            transcript,
        )

        assertEquals(listOf(0, 1), verified.wholeRequest.map {
            assertIs<ReaderAuthenticationValidity.Valid>(it.validity).evidence.authenticationIndex
        })
        assertEquals(listOf(0, 1), verified.toDisplaySafe().wholeRequest.map { it.authenticationIndex })
    }

    private suspend fun signedRequest(
        unsigned: DeviceRequest,
        document: Boolean,
        whole: Boolean,
    ): DeviceRequest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                KeyId("reader-${readerCounter++}"),
                KeySpec.Ec(EcCurve.P256),
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            key,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=Reader authentication test" }
        val headers = CoseHeaders(x5chain = listOf(CoseCertificate(certificate.encodedDer.toByteArray())))
        val sourceDoc = unsigned.docRequests.single()
        val docAuth = if (document) CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            unprotectedHeaders = headers,
            detachedPayload = ReaderAuthenticationPayloads.forDocument(transcript, sourceDoc.itemsRequest),
            key = key,
        ) else null
        val requestWithoutWhole = DeviceRequest(
            version = DeviceRequest.VERSION,
            docRequests = listOf(sourceDoc.copy(readerAuth = docAuth)),
        )
        val wholeAuth = if (whole) CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            unprotectedHeaders = headers,
            detachedPayload = ReaderAuthenticationPayloads.forAllDocuments(
                transcript,
                requestWithoutWhole.docRequests.map { it.itemsRequest },
                requestWithoutWhole.deviceRequestInfo,
            ),
            key = key,
        ) else null
        return requestWithoutWhole.copy(
            version = if (wholeAuth == null) DeviceRequest.VERSION else DeviceRequest.VERSION_WITH_SIGNING,
            readerAuthAll = wholeAuth?.let(::listOf),
        )
    }

    private fun verifier(state: ReaderTrustState) = ReaderAuthenticationVerifier(
        trustEvaluator = ReaderTrustEvaluator {
            ReaderTrustDecision(state, displayName = "Synthetic reader")
        },
        allowedAlgorithms = setOf(Cose.Algorithm.ES256),
    )

    private companion object { var readerCounter = 0 }
}
