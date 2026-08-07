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

internal object DigitalCredentialTestVerifier {
    private var result = CompletableDeferred<Result<GetCredentialResponse>>()

    fun reset() {
        result = CompletableDeferred()
    }

    fun complete(value: Result<GetCredentialResponse>) {
        result.complete(value)
    }

    suspend fun await(): Result<GetCredentialResponse> = result.await()

    val requestJson: String =
        """
        {
          "requests": [
            {
              "protocol": "openid4vp-v1-unsigned",
              "data": {
                "response_type": "vp_token",
                "response_mode": "dc_api",
                "nonce": "android-dc-api-e2e-nonce",
                "dcql_query": {
                  "credentials": [
                    {
                      "id": "mdl",
                      "format": "mso_mdoc",
                      "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                      "claims": [
                        { "path": ["org.iso.18013.5.1", "family_name"] },
                        { "path": ["org.iso.18013.5.1", "given_name"] },
                        { "path": ["org.iso.18013.5.1", "age_over_21"] }
                      ]
                    }
                  ]
                }
              }
            }
          ]
        }
        """.trimIndent()
}
