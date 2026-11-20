package id.walt.verifier2.verification2

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A presentation too large to store must be refused before the session grows around it.
 *
 * An mDL with a 250 KB portrait used to be accepted, verified, and only then found unstorable: the driver threw
 * BsonMaximumSizeExceededException over MongoDB's 16 MB document limit, and the engine's own error handling could
 * not record the reason because writing the failure onto the oversized session failed as well. The wallet saw a
 * 500 and the operator saw a driver exception with no mention of which credential caused it.
 */
class PresentationStorableSizeTest {

    @Test
    fun `an ordinary presentation is accepted`() {
        // A three-claim mDL device response is about 2 KB; a portrait-carrying one about 450 KB. Both fit.
        assertNull(presentationTooLargeToStore(mapOf("mdl" to listOf("a".repeat(2_000)))))
        assertNull(presentationTooLargeToStore(mapOf("mdl" to listOf("a".repeat(450_000)))))
    }

    @Test
    fun `a presentation beyond the storable budget is refused with the numbers that explain it`() {
        val oversized = maxStorablePresentationBytes + 1

        val reason = assertNotNull(presentationTooLargeToStore(mapOf("mdl" to listOf("a".repeat(oversized)))))

        assertContains(reason, "$oversized bytes is too large to store")
        assertContains(reason, "$STORE_DOCUMENT_LIMIT_BYTES")
        assertContains(reason, "portrait")
    }

    /** The budget covers a whole vp_token, not one presentation: several credentials share the same document. */
    @Test
    fun `presentations are measured together`() {
        val half = maxStorablePresentationBytes / 2 + 1

        assertNotNull(
            presentationTooLargeToStore(
                mapOf(
                    "mdl" to listOf("a".repeat(half)),
                    "photoid" to listOf("b".repeat(half)),
                )
            )
        )
    }
}
