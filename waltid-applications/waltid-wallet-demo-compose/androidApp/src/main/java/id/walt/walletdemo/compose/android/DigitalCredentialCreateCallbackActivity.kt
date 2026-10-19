package id.walt.walletdemo.compose.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Isolates Chrome's CLEAR_TOP callback from the in-flight Credential Manager request stack. */
class DigitalCredentialCreateCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null && DigitalCredentialCreateAuthHandoff.deliver(this, uri) ==
            DigitalCredentialCreateAuthHandoff.Delivery.Orphan) {
            // Process loss removed the provider result channel. Recover the persisted issuance in
            // the wallet; MainActivity drains the queued callback without consuming it a second time.
            startActivity(Intent(this, MainActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        finish()
    }
}
