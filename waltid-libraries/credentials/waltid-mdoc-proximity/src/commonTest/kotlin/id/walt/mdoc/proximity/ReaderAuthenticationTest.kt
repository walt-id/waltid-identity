@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSignDetached
import id.walt.cose.toCoseSigner
import id.walt.cose.CoseContentType
import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.*
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.decodeFromByteArray
import kotlin.test.assertFalse
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
        assertIs<ReaderAuthenticationResult.Absent>(absent.documents.single())
        assertEquals(emptyList(), absent.wholeRequest)

        val malformedRequest = unsigned.copy(
            docRequests = listOf(
                unsigned.docRequests.single().copy(
                    readerAuth = CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1)),
                )
            )
        )
        assertIs<ReaderAuthenticationResult.Malformed>(
            verifier(ReaderTrustState.TRUSTED).verify(malformedRequest, transcript).documents.single()
        )

        val signed = signedRequest(unsigned, document = true, whole = false)
        val tamperedSignature = requireNotNull(signed.docRequests.single().readerAuth).let { signature ->
            signature.copy(signature = signature.signature.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        }
        val invalid = signed.copy(docRequests = listOf(signed.docRequests.single().copy(readerAuth = tamperedSignature)))
        assertIs<ReaderAuthenticationResult.Invalid>(
            verifier(ReaderTrustState.TRUSTED).verify(invalid, transcript).documents.single()
        )

        listOf(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            ReaderTrustState.REVOKED,
            ReaderTrustState.TRUSTED,
        ).forEach { trustState ->
            val result = verifier(trustState).verify(signed, transcript).documents.single()
            val validity = assertIs<ReaderAuthenticationResult.Valid>(result)
            assertEquals(ReaderAuthenticationScope.Document(0), validity.evidence.scope)
            assertEquals(0, validity.evidence.authenticationIndex)
            assertEquals(trustState, validity.trust.state)
        }
    }

    @Test
    fun `ReaderAuthAll binds the exact request while document authentication remains independently absent`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val signed = signedRequest(unsigned, document = false, whole = true)
        val verified = verifier(ReaderTrustState.TRUSTED).verify(signed, transcript)

        assertIs<ReaderAuthenticationResult.Absent>(verified.documents.single())
        val whole = assertIs<ReaderAuthenticationResult.Valid>(verified.wholeRequest.single())
        assertEquals(ReaderAuthenticationScope.WholeRequest, whole.evidence.scope)
        assertEquals(0, whole.evidence.authenticationIndex)

        val changedItems = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("family_name")))
            .docRequests.single().itemsRequest
        val changed = signed.copy(
            docRequests = listOf(signed.docRequests.single().copy(itemsRequest = changedItems)),
        )
        assertIs<ReaderAuthenticationResult.Invalid>(
            verifier(ReaderTrustState.TRUSTED).verify(changed, transcript).wholeRequest.single()
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
            assertIs<ReaderAuthenticationResult.Valid>(it).evidence.authenticationIndex
        })
        assertEquals(listOf(0, 1), verified.toDisplaySafe().wholeRequest.map { it.authenticationIndex })
    }

    @Test
    fun `reader authentication accepts the RFC 9864 fully specified P256 algorithm`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val signed = signedRequest(
            unsigned = unsigned,
            document = true,
            whole = false,
            algorithm = Cose.Algorithm.ESP256,
        )

        assertIs<ReaderAuthenticationResult.Valid>(
            verifier(ReaderTrustState.TRUSTED).verify(signed, transcript).documents.single()
        )
    }

    @Test
    fun `COSE profile failures never reach reader trust evaluation`() = runTest {
        val unsigned = DeviceRequest("org.example.mdoc", mapOf("org.example" to listOf("given_name")))
        val key = runtime.generateMdocTestKey("reader-cose-profile", setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            key, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) { subjectDn = "CN=COSE profile reader" }
        val headers = CoseHeaders(x5chain = listOf(CoseCertificate(certificate.encodedDer.toByteArray())))
        val payload = ReaderAuthenticationPayloads.forDocument(transcript, unsigned.docRequests.single().itemsRequest)
        suspend fun sign(protected: CoseHeaders = CoseHeaders(algorithm = -7), unprotected: CoseHeaders = headers) =
            CoseSign1.createAndSignDetached(protected, unprotected, payload, key.toCoseSigner(-7))
        val valid = sign()
        val bagWire = CborArray(listOf(
            CborByteString(valid.protected),
            CborMap(mapOf(CborInteger(32) to CborByteString(certificate.encodedDer.toByteArray()))),
            CborNull(), CborByteString(valid.signature),
        ))
        val alteredPayload = payload.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val attached = CoseSign1.createAndSign(
            CoseHeaders(algorithm = -7), headers,
            coseCompliantCbor.encodeToByteArray<CborElement>(CborArray(listOf(
                CborString("Signature1"), CborByteString(valid.protected), CborByteString(byteArrayOf()), CborByteString(alteredPayload),
            ))),
            key.toCoseSigner(-7),
        )
        val cases = linkedMapOf(
            "malformed-DER" to valid.copy(unprotected = CoseHeaders(x5chain = listOf(CoseCertificate(byteArrayOf(0x30, 0))))),
            "21" to valid.copy(unprotected = CoseHeaders(x5chain = emptyList())),
            "23" to sign(CoseHeaders(contentType = CoseContentType.AsString("application/cbor"))),
            "24" to sign(CoseHeaders(algorithm = -37)),
            "25" to sign(unprotected = headers.copy(algorithm = -7)),
            "26" to coseCompliantCbor.decodeFromByteArray<CoseSign1>(coseCompliantCbor.encodeToByteArray<CborElement>(bagWire)),
            "27" to attached,
            "28" to valid.copy(signature = valid.signature.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }),
        )
        var trustCalls = 0
        val verifier = ReaderAuthenticationVerifier(ReaderTrustEvaluator {
            trustCalls++
            ReaderTrustDecision(ReaderTrustState.TRUSTED)
        }, setOf(-7))
        for ((case, signature) in cases) {
            val request = unsigned.copy(docRequests = listOf(unsigned.docRequests.single().copy(readerAuth = signature)))
            val result = verifier.verify(request, transcript).documents.single()
            assertFalse(result is ReaderAuthenticationResult.Valid, "mDL_SM_mdocRAuth_UF_$case")
            assertEquals(0, trustCalls, "mDL_SM_mdocRAuth_UF_$case")
        }
        assertIs<ReaderAuthenticationResult.Valid>(verifier.verify(
            unsigned.copy(docRequests = listOf(unsigned.docRequests.single().copy(readerAuth = valid))), transcript,
        ).documents.single())
        assertEquals(1, trustCalls)
    }

    private suspend fun signedRequest(
        unsigned: DeviceRequest,
        document: Boolean,
        whole: Boolean,
        algorithm: Int = Cose.Algorithm.ES256,
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
            protectedHeaders = CoseHeaders(algorithm = algorithm),
            unprotectedHeaders = headers,
            detachedPayload = ReaderAuthenticationPayloads.forDocument(transcript, sourceDoc.itemsRequest),
            key = key,
        ) else null
        val requestWithoutWhole = DeviceRequest(
            version = DeviceRequest.VERSION,
            docRequests = listOf(sourceDoc.copy(readerAuth = docAuth)),
        )
        val wholeAuth = if (whole) CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = algorithm),
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
        allowedAlgorithms = setOf(Cose.Algorithm.ES256, Cose.Algorithm.ESP256),
    )

    private companion object { var readerCounter = 0 }
}
