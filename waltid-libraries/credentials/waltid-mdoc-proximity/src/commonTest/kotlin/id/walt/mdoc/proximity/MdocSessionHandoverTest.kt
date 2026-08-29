package id.walt.mdoc.proximity

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MdocSessionHandoverTest {
    private val deviceEngagement = ImmutableBytes.of(byteArrayOf(1, 2))
    private val readerKey = ImmutableBytes.of(byteArrayOf(3, 4))

    @Test
    fun `QR and conventional NFC select conventional session messages`() {
        assertEquals(MdocSessionMessageProfile.Conventional, MdocSessionHandover.Qr.sessionMessageProfile)
        val static = MdocSessionHandover.NfcConnection(ImmutableBytes.of(byteArrayOf(5)))
        val negotiated = MdocSessionHandover.NfcConnection(
            ImmutableBytes.of(byteArrayOf(5)),
            ImmutableBytes.of(byteArrayOf(6)),
        )

        assertNull(static.createTranscript(deviceEngagement, readerKey).nfcHandover?.handoverRequest)
        assertContentEquals(
            byteArrayOf(6),
            negotiated.createTranscript(deviceEngagement, readerKey).nfcHandover?.handoverRequest,
        )
    }

    @Test
    fun `NFCv2 handover requires both exact messages and selects its profile`() {
        val handover = MdocSessionHandover.ProvisionalNfcV2(
            ImmutableBytes.of(byteArrayOf(5)),
            ImmutableBytes.of(byteArrayOf(6)),
        )

        assertEquals(MdocSessionMessageProfile.ProvisionalNfcV2, handover.sessionMessageProfile)
        assertContentEquals(byteArrayOf(5), handover.createTranscript(deviceEngagement, readerKey).nfcHandover?.handoverSelect)
        assertFailsWith<IllegalArgumentException> {
            MdocSessionHandover.ProvisionalNfcV2(
                ImmutableBytes.of(byteArrayOf()),
                ImmutableBytes.of(byteArrayOf(6)),
            )
        }
    }
}
