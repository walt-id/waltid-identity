@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProximityOwnershipTest {
    @Test
    fun `consent preview owns input and exported collections through nested values`() {
        val ids = mutableListOf("one", "two")
        val elements = mutableListOf(PreviewElement("n", "a", false), PreviewElement("n", "b", true))
        val document = PreviewDocument("doc", ids, elements)
        val details = mutableListOf(
            MdocApplicationAuthorizationDetail("a", "A", "one"),
            MdocApplicationAuthorizationDetail("b", "B", "two"),
        )
        val authorization = MdocApplicationAuthorization("profile/v1", "Review", details, ImmutableBytes.of(ByteArray(32)))
        val before = authorization.consentBindingDigest()
        val documents = mutableListOf(document, document)
        val purposes = mutableMapOf("a" to 1, "b" to 2)
        val authorizations = mutableListOf(authorization, authorization)
        val preview = MdocRequestPreview(documents, purposes, submissionBindingDigest = ImmutableBytes.of(ByteArray(32)), applicationAuthorizations = authorizations)
        ids.clear(); elements.clear(); details.clear(); documents.clear(); purposes.clear(); authorizations.clear()
        clearIfMutable(preview.documents)
        clearIfMutable(document.credentialIds)
        clearIfMutable(document.elements)
        clearIfMutable(authorization.details)
        clearIfMutable(preview.applicationAuthorizations)
        (preview.purposeHints as? MutableMap)?.clear()
        assertEquals(listOf("one", "two"), document.credentialIds)
        assertEquals(2, document.elements.size)
        assertEquals(2, preview.documents.size)
        assertEquals(mapOf("a" to 1, "b" to 2), preview.purposeHints)
        assertEquals(2, preview.applicationAuthorizations.size)
        assertEquals(before, authorization.consentBindingDigest())
    }

    @Test
    fun `candidate and authentication evidence retain owned membership`() {
        val first = ElementReference("n", "a")
        val second = ElementReference("n", "b")
        val elements = mutableSetOf(first, second)
        val booleans = mutableMapOf(first to true, second to false)
        val chain = mutableListOf(ImmutableBytes.of(byteArrayOf(1)), ImmutableBytes.of(byteArrayOf(2)))
        val candidate = MdocCredentialCandidate("id", "doc", chain, elements, booleans)
        val evidence = ReaderAuthenticationEvidence(ReaderAuthenticationScope.Document(0), certificateChainDer = chain)
        val result = ReaderAuthenticationResult.Valid(evidence, ReaderTrustDecision(ReaderTrustState.TRUSTED))
        val results = mutableListOf<ReaderAuthenticationResult>(result, ReaderAuthenticationResult.Absent)
        val authentication = DeviceRequestReaderAuthentication(results, results)
        chain.clear(); elements.clear(); booleans.clear(); results.clear()
        (candidate.availableElements as? MutableSet)?.clear()
        (candidate.booleanElements as? MutableMap)?.clear()
        clearIfMutable(evidence.certificateChainDer)
        clearIfMutable(authentication.documents)
        val display = authentication.toDisplaySafe()
        clearIfMutable(display.documents)
        assertEquals(setOf(first, second), candidate.availableElements)
        assertEquals(mapOf(first to true, second to false), candidate.booleanElements)
        assertEquals(2, candidate.issuerAuthorityKeyIdentifiers.size)
        assertEquals(2, evidence.certificateChainDer.size)
        assertEquals(2, authentication.documents.size)
        assertEquals(2, display.documents.size)
        assertFailsWith<IllegalArgumentException> { ReaderAuthenticationScope.Document(-1) }
    }

    @Test
    fun `request context restores projections from original exact protocol bytes`() {
        val request = DeviceRequest("doc", mapOf("n" to listOf("a")))
        val requestBytes = coseCompliantCbor.encodeToByteArray(request)
        val transcript = SessionTranscript.forQr(byteArrayOf(1), byteArrayOf(2))
        val transcriptBytes = MdocCryptoHelper.buildSessionTranscriptBytes(transcript)
        val key = CoseKey(kty = 2, x = byteArrayOf(3), y = byteArrayOf(4))
        val keyBytes = coseCompliantCbor.encodeToByteArray(key)
        val context = MdocHolderRequestContext(
            ExactCbor.of(request, requestBytes), ExactCbor.of(transcript, transcriptBytes), ExactCbor.of(key, keyBytes), 1,
        )
        requireNotNull(transcript.deviceEngagementBytes)[0] = 9
        requireNotNull(key.x)[0] = 9
        requireNotNull(context.transcript.value.deviceEngagementBytes)[0] = 8
        requireNotNull(context.readerEphemeralKey.value.x)[0] = 8
        context.request.value.docRequests.single().itemsRequest.serialized.fill(0)
        assertContentEquals(byteArrayOf(1), context.transcript.value.deviceEngagementBytes)
        assertContentEquals(byteArrayOf(3), context.readerEphemeralKey.value.x)
        assertContentEquals(requestBytes, context.request.encodedCopy())
        assertContentEquals(transcriptBytes, context.transcript.encodedCopy())
        assertEquals("doc", context.request.value.docRequests.single().itemsRequest.value.docType)
    }

    @Test
    fun `RICAL policy owns accepted types and trusted roots`() {
        val types = mutableSetOf("reader", "other")
        val roots = mutableListOf(ImmutableBytes.of(byteArrayOf(1)), ImmutableBytes.of(byteArrayOf(2)))
        val policy = RicalPolicy("provider", types, roots)
        types.clear(); roots.clear()
        (policy.acceptedTypes as? MutableSet)?.clear()
        clearIfMutable(policy.trustedProviderRootsDer)
        assertEquals(setOf("reader", "other"), policy.acceptedTypes)
        assertEquals(2, policy.trustedProviderRootsDer.size)
    }

    private fun <T> clearIfMutable(values: List<T>) {
        (values as? MutableList<T>)?.clear()
    }
}
