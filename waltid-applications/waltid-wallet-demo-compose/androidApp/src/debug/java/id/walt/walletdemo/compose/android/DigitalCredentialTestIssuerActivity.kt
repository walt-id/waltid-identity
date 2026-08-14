@file:OptIn(ExperimentalDigitalCredentialApi::class)

package id.walt.walletdemo.compose.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.CreateDigitalCredentialRequest
import androidx.credentials.CreateDigitalCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.ExperimentalDigitalCredentialApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only native issuer used by the Digital Credentials create instrumentation E2E. */
class DigitalCredentialTestIssuerActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val result = runCatching {
                CredentialManager.create(this@DigitalCredentialTestIssuerActivity).createCredential(
                    context = this@DigitalCredentialTestIssuerActivity,
                    request = CreateDigitalCredentialRequest(
                        requestJson = DigitalCredentialTestIssuer.requestJson,
                        origin = null,
                    ),
                ) as CreateDigitalCredentialResponse
            }
            result.exceptionOrNull()?.let { Log.e("DigitalCredentialE2E", "Credential Manager create failed", it) }
            DigitalCredentialTestIssuer.complete(result)
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
    private var result = CompletableDeferred<Result<CreateDigitalCredentialResponse>>()

    lateinit var requestJson: String
        private set

    fun reset(requestJson: String) {
        result = CompletableDeferred()
        this.requestJson = requestJson
    }

    fun complete(value: Result<CreateDigitalCredentialResponse>) {
        result.complete(value)
    }

    suspend fun await(): Result<CreateDigitalCredentialResponse> = result.await()

    fun isComplete(): Boolean = result.isCompleted
}
