@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.deviceretrieval.AlternativeDataElementsSet
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceRequestInfo
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequestInfo
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.ItemRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequestList
import id.walt.mdoc.objects.deviceretrieval.UseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RequestMatchingTest {
    @Test
    fun `mandatory use cases select the first satisfiable set and preserve alternative retention`() = runTest {
        val givenName = ElementReference("org.example", "given_name")
        val portrait = ElementReference("org.example", "portrait")
        val age = ElementReference("org.example", "age_over_18")
        val request = DeviceRequest(
            version = "1.1",
            docRequests = listOf(
                docRequest("id", givenName, alternative = portrait, retain = true),
                docRequest("age", age),
            ),
            deviceRequestInfo = ByteStringWrapper(
                DeviceRequestInfo(
                    useCases = listOf(
                        UseCase(true, mapOf("org.iso.jtc1.sc17" to 1), listOf(listOf(0u, 1u))),
                    )
                )
            ),
        )
        val candidates = listOf(
            MdocCredentialCandidate("id-1", "id", emptyList(), listOf(portrait)),
            MdocCredentialCandidate("age-1", "age", emptyList(), listOf(age)),
        )

        val result = assertIs<MdocRequestMatchResult.Matched>(MdocRequestMatcher().match(request, candidates))
        assertEquals(listOf(0, 1), result.selection.useCases.single().documentSet)
        assertEquals(
            SelectedElement(portrait, intentToRetain = true, satisfiesAlternativesFor = setOf(givenName)),
            result.selection.documents.first().elements.single(),
        )
    }

    @Test
    fun `issuer constraints and unsatisfied mandatory use cases fail closed`() = runTest {
        val element = ElementReference("org.example", "name")
        val request = DeviceRequest(
            version = "1.1",
            docRequests = listOf(
                docRequest("id", element, issuerIds = listOf(byteArrayOf(1))),
            ),
            deviceRequestInfo = ByteStringWrapper(
                DeviceRequestInfo(useCases = listOf(UseCase(true, documentSets = listOf(listOf(0u)))))
            ),
        )
        val candidate = MdocCredentialCandidate(
            "id-1", "id", listOf(ImmutableBytes.of(byteArrayOf(2))), listOf(element)
        )

        assertIs<MdocRequestMatchResult.Unsatisfied>(MdocRequestMatcher().match(request, listOf(candidate)))
    }

    @Test
    fun `unique document semantics and overlapping alternatives do not duplicate a returned element`() = runTest {
        val requestedName = ElementReference("org.example", "given_name")
        val requestedFamily = ElementReference("org.example", "family_name")
        val fullName = ElementReference("org.example", "full_name")
        val items = ItemsRequest(
            docType = "id",
            namespaces = mapOf(
                "org.example" to ItemsRequestList(
                    listOf(ItemRequest("given_name", false), ItemRequest("family_name", true))
                )
            ),
            requestInfo = DocRequestInfo(
                alternativeDataElements = listOf(
                    AlternativeDataElementsSet(requestedName, listOf(listOf(fullName))),
                    AlternativeDataElementsSet(requestedFamily, listOf(listOf(fullName))),
                ),
                uniqueDocSetRequired = false,
            ),
        )
        val request = DeviceRequest(
            DeviceRequest.VERSION,
            listOf(DocRequest(ByteStringWrapper(items))),
        )
        val candidates = listOf(
            MdocCredentialCandidate("first", "id", emptyList(), listOf(fullName)),
            MdocCredentialCandidate("second", "id", emptyList(), listOf(fullName)),
        )

        val multiple = assertIs<MdocRequestMatchResult.Matched>(MdocRequestMatcher().match(request, candidates))
        assertEquals(listOf("first", "second"), multiple.selection.documents.map { it.credentialId })
        multiple.selection.documents.forEach { selected ->
            assertEquals(1, selected.elements.size)
            assertEquals(setOf(requestedName, requestedFamily), selected.elements.single().satisfiesAlternativesFor)
            assertTrue(selected.elements.single().intentToRetain)
        }

        val uniqueItems = items.copy(requestInfo = items.requestInfo!!.copy(uniqueDocSetRequired = true))
        val unique = assertIs<MdocRequestMatchResult.Matched>(
            MdocRequestMatcher().match(
                DeviceRequest(DeviceRequest.VERSION, listOf(DocRequest(ByteStringWrapper(uniqueItems)))),
                candidates,
            )
        )
        assertEquals(listOf("first"), unique.selection.documents.map { it.credentialId })

        val undefinedItems = items.copy(requestInfo = items.requestInfo!!.copy(uniqueDocSetRequired = null))
        val undefined = assertIs<MdocRequestMatchResult.Matched>(
            MdocRequestMatcher().match(
                DeviceRequest(DeviceRequest.VERSION, listOf(DocRequest(ByteStringWrapper(undefinedItems)))),
                candidates,
            )
        )
        assertEquals(listOf("first"), undefined.selection.documents.map { it.credentialId })
        assertEquals(
            listOf("first", "second"),
            undefined.selection.eligibleDocuments.map { it.credentialId },
        )
    }

    @Test
    fun `overlapping mandatory use cases share selected documents and preserve unknown purpose hints`() = runTest {
        val first = ElementReference("org.example", "first")
        val second = ElementReference("org.example", "second")
        val purpose = mapOf("org.example.private-purpose" to -7)
        val request = DeviceRequest(
            version = DeviceRequest.VERSION_WITH_SIGNING,
            docRequests = listOf(docRequest("first", first), docRequest("second", second)),
            deviceRequestInfo = ByteStringWrapper(
                DeviceRequestInfo(
                    listOf(
                        UseCase(true, purpose, listOf(listOf(0u, 1u))),
                        UseCase(true, documentSets = listOf(listOf(1u))),
                    )
                )
            ),
        )
        val result = assertIs<MdocRequestMatchResult.Matched>(
            MdocRequestMatcher().match(
                request,
                listOf(
                    MdocCredentialCandidate("first", "first", emptyList(), listOf(first)),
                    MdocCredentialCandidate("second", "second", emptyList(), listOf(second)),
                ),
            )
        )

        assertEquals(2, result.selection.documents.size)
        assertEquals(2, result.selection.useCases.size)
        assertEquals(purpose, result.selection.useCases.first().purposeHints)
    }

    private fun docRequest(
        docType: String,
        element: ElementReference,
        alternative: ElementReference? = null,
        retain: Boolean = false,
        issuerIds: List<ByteArray>? = null,
    ): DocRequest = DocRequest(
        itemsRequest = ByteStringWrapper(
            ItemsRequest(
                docType,
                mapOf(element.namespace to ItemsRequestList(listOf(ItemRequest(element.elementIdentifier, retain)))),
                if (alternative != null || issuerIds != null) DocRequestInfo(
                    alternativeDataElements = alternative?.let { listOf(AlternativeDataElementsSet(element, listOf(listOf(it)))) },
                    issuerIdentifiers = issuerIds,
                ) else null,
            )
        )
    )
}
