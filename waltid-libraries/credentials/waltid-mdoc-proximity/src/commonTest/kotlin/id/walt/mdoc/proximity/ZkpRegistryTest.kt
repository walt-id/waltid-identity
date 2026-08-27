@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequest
import id.walt.mdoc.objects.deviceretrieval.DocRequestInfo
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.ItemRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.ItemsRequestList
import id.walt.mdoc.objects.deviceretrieval.ZkDocument
import id.walt.mdoc.objects.deviceretrieval.ZkDocumentData
import id.walt.mdoc.objects.deviceretrieval.ZkRequest
import id.walt.mdoc.objects.deviceretrieval.ZkSystemSpec
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ZkpRegistryTest {
    private val element = ElementReference("org.example", "age_over_18")
    private val candidate = MdocCredentialCandidate("credential", "org.example.mdoc", emptyList(), listOf(element))
    private val supportedSpec = ZkSystemSpec(
        zkSystemId = "proof-1",
        system = "org.example.zk",
        params = CborMap(mapOf(CborString("opaque") to CborByteString(byteArrayOf(1, 2, 3)))),
    )

    @Test
    fun `required unsupported ZKP fails while optional unsupported ZKP falls back to a normal document`() = runTest {
        val registry = ZkpRegistry.EMPTY
        val required = assertIs<MdocRequestMatchResult.Unsatisfied>(
            MdocRequestMatcher(registry).match(request(zkRequired = true), listOf(candidate))
        )
        assertEquals(setOf(0), required.unsatisfiedRequestIndices)

        val optional = assertIs<MdocRequestMatchResult.Matched>(
            MdocRequestMatcher(registry).match(request(zkRequired = false), listOf(candidate))
        )
        assertNull(optional.selection.documents.single().zkp)
    }

    @Test
    fun `supported ZKP binds the exact system specification and dispatches only to its selected provider`() = runTest {
        var proofCalls = 0
        val provider = object : ZkpProvider {
            override val id = "provider"
            override val system = supportedSpec.system
            override suspend fun supports(candidate: MdocCredentialCandidate, specification: ZkSystemSpec) =
                candidate.id == "credential" && specification == supportedSpec

            override suspend fun createProof(input: ZkpProofInput): ZkDocument {
                proofCalls++
                val data = ZkDocumentData(
                    docType = "org.example.mdoc",
                    zkSystemId = input.specification.zkSystemId,
                    timestamp = LocalDate(2026, 8, 27),
                )
                return ZkDocument(
                    ByteStringWrapper(
                        data,
                        coseCompliantCbor.encodeToByteArray(ZkDocumentData.serializer(), data),
                    ),
                    proof = byteArrayOf(9),
                )
            }
        }
        val registry = ZkpRegistry(listOf(provider))
        val matched = assertIs<MdocRequestMatchResult.Matched>(
            MdocRequestMatcher(registry).match(request(zkRequired = true), listOf(candidate))
        )
        val selected = requireNotNull(matched.selection.documents.single().zkp)
        assertEquals(supportedSpec, selected.specification)

        val input = ZkpProofInput(
            credentialId = candidate.id,
            specification = supportedSpec,
            selectedElements = matched.selection.documents.single().elements,
            transcript = SessionTranscript.forQr(byteArrayOf(1), byteArrayOf(2)),
        )
        assertEquals("proof-1", registry.createProof(selected, input).documentData.value.zkSystemId)
        assertEquals(1, proofCalls)

        assertFailsWith<IllegalArgumentException> {
            registry.createProof(
                selected,
                input.copy(specification = supportedSpec.copy(params = CborMap(emptyMap()))),
            )
        }
        assertEquals(1, proofCalls)
    }

    private fun request(zkRequired: Boolean) = DeviceRequest(
        version = DeviceRequest.VERSION,
        docRequests = listOf(
            DocRequest(
                ByteStringWrapper(
                    ItemsRequest(
                        docType = "org.example.mdoc",
                        namespaces = mapOf(
                            element.namespace to ItemsRequestList(listOf(ItemRequest(element.elementIdentifier, false)))
                        ),
                        requestInfo = DocRequestInfo(
                            zkRequest = ZkRequest(zkRequired, listOf(supportedSpec)),
                        ),
                    )
                )
            )
        ),
    )
}
