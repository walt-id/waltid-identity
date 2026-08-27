package id.walt.mdoc.proximity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HolderSessionStateTest {
    private val preview = MdocRequestPreview(
        documents = listOf(
            PreviewDocument(
                docType = "org.iso.18013.5.1.mDL",
                credentialIds = listOf("credential-1"),
                elements = listOf(
                    PreviewElement("org.iso.18013.5.1", "given_name", intentToRetain = false)
                ),
            )
        ),
        submissionBindingDigest = ImmutableBytes.of(ByteArray(32) { it.toByte() }),
    )

    @Test
    fun `display states expose only their legal user actions`() {
        val prompt = MdocConsentPrompt(ImmutableBytes.of(ByteArray(32)), 1, preview)

        assertEquals(emptySet(), MdocHolderSessionState.Idle.legalActions)
        assertEquals(
            setOf(MdocHolderAction.CANCEL),
            MdocHolderSessionState.AwaitingRequest(2).legalActions,
        )
        assertEquals(
            setOf(MdocHolderAction.APPROVE, MdocHolderAction.DENY, MdocHolderAction.CANCEL),
            MdocHolderSessionState.ReviewRequired(prompt).legalActions,
        )
        assertEquals(emptySet(), MdocHolderSessionState.Completed(2).legalActions)
        assertEquals(
            emptySet(),
            MdocHolderSessionState.Failed(ProximityError.Protocol("invalid_request", "Invalid request")).legalActions,
        )
    }

    @Test
    fun `display state identifiers and consent bindings reject impossible values`() {
        assertFailsWith<IllegalArgumentException> { MdocHolderSessionState.AwaitingRequest(0) }
        assertFailsWith<IllegalArgumentException> { MdocHolderSessionState.Completed(0) }
        assertFailsWith<IllegalArgumentException> {
            MdocConsentPrompt(ImmutableBytes.of(ByteArray(31)), 1, preview)
        }
        assertFailsWith<IllegalArgumentException> {
            PreviewDocument(
                "org.iso.18013.5.1.mDL",
                listOf("credential-1", "credential-1"),
                preview.documents.single().elements,
            )
        }
    }
}
