@file:OptIn(ExperimentalDigitalCredentialApi::class)

package id.walt.walletdemo.compose.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.CreateDigitalCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Debug-only native issuer used by the Digital Credentials create instrumentation E2E. */
class DigitalCredentialTestIssuerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                CredentialManager.create(this@DigitalCredentialTestIssuerActivity).createCredential(
                    context = this@DigitalCredentialTestIssuerActivity,
                    request = CreateDigitalCredentialRequest(
                        requestJson = DigitalCredentialTestIssuer.requestJson,
                        origin = null,
                    ),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                Log.e("DigitalCredentialE2E", "Credential Manager create failed", exception)
            }
            finish()
        }
    }
}

/**
 * Test-side handoff for [DigitalCredentialTestIssuerActivity].
 *
 * The request JSON is injected so the E2E can pass a real issuer2 credential offer.
 */
internal object DigitalCredentialTestIssuer {
    lateinit var requestJson: String
        private set

    fun reset(requestJson: String) {
        this.requestJson = requestJson
    }
}
