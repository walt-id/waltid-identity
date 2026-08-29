@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.mdoc.objects.session.SessionData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionMessageProfileTest {
    @Test
    fun `conventional messages omit and reject provisional sequence fields`() {
        val sequencer = MdocSessionMessageSequencer(MdocSessionMessageProfile.Conventional)
        assertNull(sequencer.sequence(SessionData(status = 20u)).seq)
        assertFailsWith<IllegalArgumentException> {
            sequencer.sequence(SessionData(status = 20u, seq = 0u))
        }
        sequencer.validateIncoming(SessionData(status = 20u))
        val failure = assertFailsWith<ProximityException> {
            sequencer.validateIncoming(SessionData(status = 20u, seq = 0u))
        }
        assertEquals("unexpected_session_sequence", failure.error.code)
    }

    @Test
    fun `provisional outgoing sequence is direction local and monotonic`() {
        val firstDirection = MdocSessionMessageSequencer(MdocSessionMessageProfile.ProvisionalNfcV2)
        val secondDirection = MdocSessionMessageSequencer(MdocSessionMessageProfile.ProvisionalNfcV2)

        assertEquals(0u, firstDirection.sequence(SessionData(status = 20u)).seq)
        assertEquals(1u, firstDirection.sequence(SessionData(status = 20u)).seq)
        assertEquals(0u, secondDirection.sequence(SessionData(status = 20u)).seq)
    }

    @Test
    fun `provisional inbound sequence remains permissive until the authoritative contract is available`() {
        val sequencer = MdocSessionMessageSequencer(MdocSessionMessageProfile.ProvisionalNfcV2)

        listOf<UInt?>(null, 7u, 8u, 8u, 3u, UInt.MAX_VALUE).forEach { sequence ->
            sequencer.validateIncoming(SessionData(status = 20u, seq = sequence))
        }
    }
}
