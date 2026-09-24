package id.walt.verifier2.verification2

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

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
        checkPresentationIsStorable(mapOf("mdl" to listOf("a".repeat(2_000))))
        checkPresentationIsStorable(mapOf("mdl" to listOf("a".repeat(450_000))))
    }

    @Test
    fun `a presentation beyond the storable budget is refused with the numbers that explain it`() {
        val oversized = maxStorablePresentationBytes + 1

        val failure = assertFailsWith<IllegalStateException> {
            checkPresentationIsStorable(mapOf("mdl" to listOf("a".repeat(oversized.toInt()))))
        }

        assertContains(failure.message!!, "$oversized bytes is too large to store")
        assertContains(failure.message!!, "$STORE_DOCUMENT_LIMIT_BYTES")
        assertContains(failure.message!!, "portrait")
    }

    /** The budget covers a whole vp_token, not one presentation: several credentials share the same document. */
    @Test
    fun `presentations are measured together`() {
        val half = (maxStorablePresentationBytes / 2 + 1).toInt()

        assertFailsWith<IllegalStateException> {
            checkPresentationIsStorable(
                mapOf(
                    "mdl" to listOf("a".repeat(half)),
                    "photoid" to listOf("b".repeat(half)),
                )
            )
        }
    }
}
