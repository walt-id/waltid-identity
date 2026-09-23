package id.walt.wallet2.mobile

import android.content.Intent
import android.util.Base64
import androidx.credentials.CreateDigitalCredentialRequest
import androidx.credentials.CreateDigitalCredentialResponse
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import id.waltid.openid4vp.wallet.DcApiWallet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Verified Android Credential Manager create input after caller and protocol extraction.
 *
 * @property request OpenID4VCI create request extracted from Credential Manager.
 * @property providerRequest Original Credential Manager create request.
 */
public data class AndroidDigitalCredentialCreateProviderInput(
    public val request: AndroidDigitalCredentialCreateRequest,
    public val providerRequest: ProviderCreateCredentialRequest,
)

/**
 * Android CREATE_CREDENTIAL OpenID4VCI request after Credential Manager extraction.
 *
 * Kept on the Android adapter boundary rather than the common mobile SDK surface.
 *
 * @property protocol Digital Credentials protocol identifier selected from the advertised
 * OpenID4VCI create protocols (`openid4vci-v1` first, then historical aliases).
 * @property offerJson Credential Offer JSON object extracted from the selected protocol
 * alternative's `data` field.
 * @property verifiedOrigin Canonical caller origin derived from Credential Manager caller
 * authentication.
 */
public data class AndroidDigitalCredentialCreateRequest(
    public val protocol: String,
    public val offerJson: String,
    public val verifiedOrigin: String,
)

/**
 * Android CREATE_CREDENTIAL acknowledgement returned to Credential Manager after OpenID4VCI completes.
 *
 * @property protocol Digital Credentials protocol identifier echoed in the create response.
 * @property dataJson JSON object serialized as the response `data` field. OpenID4VCI create
 * acknowledgements currently send an empty object.
 */
public data class AndroidDigitalCredentialCreateResponse(
    public val protocol: String = MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1,
    public val dataJson: String = "{}",
) {
    /** Factory for the empty OpenID4VCI create acknowledgement. */
    public companion object {
        /**
         * Returns the empty acknowledgement Credential Manager expects after a successful
         * OpenID4VCI create.
         *
         * @param protocol Digital Credentials protocol identifier echoed in the create response.
         */
        public fun acknowledgment(
            protocol: String = MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1,
        ): AndroidDigitalCredentialCreateResponse =
            AndroidDigitalCredentialCreateResponse(protocol = protocol, dataJson = "{}")
    }
}

/** Android framework boundary for official holder CREATE_CREDENTIAL request and response handling. */
@OptIn(ExperimentalDigitalCredentialApi::class)
public object AndroidDigitalCredentialCreateProvider {
    /**
     * Locates the OpenID4VCI create request and derives the origin from authenticated caller data.
     *
     * A populated privileged origin is rejected unless the caller package and signing certificate
     * match [privilegedAppsJson].
     */
    public fun extract(
        intent: Intent,
        privilegedAppsJson: String,
    ): AndroidDigitalCredentialCreateProviderInput {
        val providerRequest = requireNotNull(PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)) {
            "Missing ProviderCreateCredentialRequest"
        }
        val callingRequest = providerRequest.callingRequest
        require(callingRequest is CreateDigitalCredentialRequest) {
            "CREATE_CREDENTIAL request must be a CreateDigitalCredentialRequest"
        }
        val callingApp = providerRequest.callingAppInfo
        val privilegedOrigin = callingApp.getOrigin(privilegedAppsJson)
        if (callingApp.isOriginPopulated()) {
            require(!privilegedOrigin.isNullOrBlank()) {
                "Privileged caller is not present in the configured browser allowlist"
            }
        }
        val assertedOrigin = privilegedOrigin
            ?: callingApp.signingInfoCompat.signingCertificateHistory.firstOrNull()?.toByteArray()?.let {
                nativeAppOrigin(it)
            }
            ?: throw IllegalArgumentException("Calling application has no signing certificate")
        val verifiedOrigin = DcApiWallet.canonicalizePlatformOrigin(assertedOrigin)
        val request = resolveCreateRequest(
            requestJson = callingRequest.requestJson,
            verifiedOrigin = verifiedOrigin,
        )
        return AndroidDigitalCredentialCreateProviderInput(
            request = request,
            providerRequest = providerRequest,
        )
    }

    /**
     * Resolves the first preferred OpenID4VCI protocol alternative from a standard create-request envelope.
     *
     * Only the Digital Credentials `requests` array shape is accepted. Selection follows
     * [OPENID4VCI_CREATE_PROTOCOLS] order so a dual-protocol request prefers `openid4vci-v1`.
     * Unsupported alternatives are skipped.
     */
    internal fun resolveCreateRequest(
        requestJson: String,
        verifiedOrigin: String,
    ): AndroidDigitalCredentialCreateRequest {
        val requestObject = Json.parseToJsonElement(requestJson).jsonObject
        val requests = requestObject["requests"] as? JsonArray
            ?: throw IllegalArgumentException("Digital credential create request must contain a requests array")
        require(requests.isNotEmpty()) { "At least one protocol request is required" }
        val protocolRequests = requests.map { it.jsonObject }
        val selected = OPENID4VCI_CREATE_PROTOCOLS.firstNotNullOfOrNull { protocol ->
            protocolRequests.firstOrNull { protocolRequest ->
                protocolRequest["protocol"]?.jsonPrimitive?.content == protocol
            }
        } ?: throw IllegalArgumentException(
            "No OpenID4VCI Digital Credentials create protocol was offered",
        )
        val data = selected["data"] as? JsonObject
            ?: throw IllegalArgumentException("Digital credential create request data must be an object")
        val protocol = requireNotNull(selected["protocol"]?.jsonPrimitive?.content) {
            "Digital credential create request protocol must be a string"
        }
        return AndroidDigitalCredentialCreateRequest(
            protocol = protocol,
            offerJson = Json.encodeToString(JsonObject.serializer(), data),
            verifiedOrigin = verifiedOrigin,
        )
    }

    /** Writes the official Credential Manager create result payload to [resultIntent]. */
    public fun setResponse(
        resultIntent: Intent,
        response: AndroidDigitalCredentialCreateResponse = AndroidDigitalCredentialCreateResponse.acknowledgment(),
    ) {
        val responseJson = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "protocol" to kotlinx.serialization.json.JsonPrimitive(response.protocol),
                    "data" to Json.parseToJsonElement(response.dataJson).jsonObject,
                )
            ),
        )
        PendingIntentHandler.setCreateCredentialResponse(
            resultIntent,
            CreateDigitalCredentialResponse(responseJson),
        )
    }

    /** Maps explicit user refusal to the platform cancellation exception. */
    public fun setCancellation(resultIntent: Intent) {
        PendingIntentHandler.setCreateCredentialException(
            resultIntent,
            CreateCredentialCancellationException(),
        )
    }

    /** Maps malformed, unsupported, or failed requests without leaking sensitive inputs. */
    public fun setFailure(resultIntent: Intent, message: String = "Digital credential issuance failed") {
        PendingIntentHandler.setCreateCredentialException(
            resultIntent,
            CreateCredentialUnknownException(message),
        )
    }

    internal fun nativeAppOrigin(signingCertificate: ByteArray): String =
        "android:apk-key-hash:${
            Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(signingCertificate),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
        }"
}
