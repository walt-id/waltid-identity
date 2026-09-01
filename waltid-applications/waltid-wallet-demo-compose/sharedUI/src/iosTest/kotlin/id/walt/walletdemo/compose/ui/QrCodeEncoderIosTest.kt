package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.logic.QrCodePayload
import id.walt.walletdemo.compose.ui.components.encodeQrCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QrCodeEncoderIosTest {
    @Test
    fun encodesConfirmedPlaceholderPayload() {
        val payload = "12/POC(N)000001|Aung Min Thu|1990-06-15|Male|Myanmar|Yangon, Myanmar|true"
        val image = encodeQrCode(QrCodePayload.Text(payload))

        assertTrue(image.width > 0)
        assertEquals(image.width, image.height)
    }
}
