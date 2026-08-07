package id.walt.walletdemo.compose.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only native verifier used by the Digital Credentials instrumentation E2E. */
@OptIn(ExperimentalDigitalCredentialApi::class)
class DigitalCredentialTestVerifierActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            val result = runCatching {
                CredentialManager.create(this@DigitalCredentialTestVerifierActivity).getCredential(
                    context = this@DigitalCredentialTestVerifierActivity,
                    request = GetCredentialRequest(
                        credentialOptions = listOf(GetDigitalCredentialOption(DigitalCredentialTestVerifier.requestJson)),
                    ),
                )
            }
            result.exceptionOrNull()?.let { Log.e("DigitalCredentialE2E", "Credential Manager request failed", it) }
            DigitalCredentialTestVerifier.complete(result)
            finish()
        }
    }
}

/**
 * Test-side handoff for [DigitalCredentialTestVerifierActivity].
 *
 * The request is injected rather than hardcoded: the nonce must be the one a real verifier issued,
 * otherwise nothing about the response can be verified.
 */
internal object DigitalCredentialTestVerifier {
    private var result = CompletableDeferred<Result<GetCredentialResponse>>()

    lateinit var requestJson: String
        private set

    fun reset(requestJson: String) {
        result = CompletableDeferred()
        this.requestJson = requestJson
    }

    fun complete(value: Result<GetCredentialResponse>) {
        result.complete(value)
    }

    suspend fun await(): Result<GetCredentialResponse> = result.await()
}
