package id.walt.walletdemo.compose.android

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Minimal HCE payment-category service so this app qualifies for Android's Wallet role
 * (Settings → Apps → Default apps → Wallet app).
 *
 * This is not a real payment or ISO 18013-5 applet: every APDU is declined. Credential Manager
 * Digital Credentials flows do not use this service.
 */
class WalletHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray =
        STATUS_FILE_NOT_FOUND

    override fun onDeactivated(reason: Int) = Unit

    private companion object {
        /** ISO 7816-4 SW1-SW2: file not found. */
        private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    }
}
