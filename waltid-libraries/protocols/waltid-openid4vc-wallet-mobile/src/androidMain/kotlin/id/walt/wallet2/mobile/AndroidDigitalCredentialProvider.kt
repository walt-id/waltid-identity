package id.walt.wallet2.mobile

import android.content.Intent
import android.util.Base64
import androidx.credentials.DigitalCredential
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetDigitalCredentialOption
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.credentials.registry.provider.selectedCredentialSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.net.URI

/** Verified Android Credential Manager input after caller and selected-entry extraction. */
public data class AndroidDigitalCredentialProviderInput(
    public val request: MobileWalletDigitalCredentialRequest,
    public val providerRequest: ProviderGetCredentialRequest,
)

/** Android framework boundary for official holder Activity request and response handling. */
@OptIn(ExperimentalDigitalCredentialApi::class)
public object AndroidDigitalCredentialProvider {
    /**
     * Extracts exactly one Digital Credentials request and derives its origin from authenticated
     * Credential Manager caller data. A populated privileged origin is rejected unless the caller
     * package and signing certificate match [privilegedAppsJson].
     */
    public fun extract(
        intent: Intent,
        privilegedAppsJson: String,
    ): AndroidDigitalCredentialProviderInput {
        val providerRequest = requireNotNull(PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)) {
            "Missing ProviderGetCredentialRequest"
        }
        val options = providerRequest.credentialOptions.filterIsInstance<GetDigitalCredentialOption>()
        require(options.size == 1) { "Exactly one digital credential option is required" }
        val callingApp = providerRequest.callingAppInfo
        val privilegedOrigin = callingApp.getOrigin(privilegedAppsJson)
        if (callingApp.isOriginPopulated()) {
            require(!privilegedOrigin.isNullOrBlank()) {
                "Privileged caller is not present in the configured browser allowlist"
            }
        }
        val verifiedOrigin = privilegedOrigin?.canonicalWebOrigin()
            ?: callingApp.signingInfoCompat.signingCertificateHistory.firstOrNull()?.toByteArray()?.let {
                nativeAppOrigin(it)
            }
            ?: throw IllegalArgumentException("Calling application has no signing certificate")

        val request = parseProtocolRequest(
            requestJson = options.single().requestJson,
            verifiedOrigin = verifiedOrigin,
            selectedRegistryEntryIds = providerRequest.selectedCredentialSet?.credentials.orEmpty().map {
                normalizeMatcherCredentialId(it.credentialId)
            },
        )

        return AndroidDigitalCredentialProviderInput(request = request, providerRequest = providerRequest)
    }

    internal fun parseProtocolRequest(
        requestJson: String,
        verifiedOrigin: String,
        selectedRegistryEntryIds: List<String> = emptyList(),
    ): MobileWalletDigitalCredentialRequest {
        val requestObject = Json.parseToJsonElement(requestJson).jsonObject
        val requests = requestObject["requests"] as? JsonArray
        val protocolRequest = when {
            requests != null -> {
                require(requests.size == 1) { "Exactly one protocol request is required" }
                requests.single().jsonObject
            }
            requestObject["protocol"] != null -> requestObject
            else -> throw IllegalArgumentException("Digital credential request has no protocol request")
        }
        val protocol = protocolRequest["protocol"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Digital credential request protocol is required")
        val data = protocolRequest["data"] as? JsonObject
            ?: throw IllegalArgumentException("Digital credential request data must be an object")
        return MobileWalletDigitalCredentialRequest(
            protocol = protocol,
            dataJson = Json.encodeToString(JsonObject.serializer(), data),
            verifiedOrigin = verifiedOrigin,
            selectedRegistryEntryIds = selectedRegistryEntryIds,
        )
    }

    /** Writes the official Credential Manager result payload to [resultIntent]. */
    public fun setResponse(
        resultIntent: Intent,
        response: MobileWalletDigitalCredentialResponse,
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
        PendingIntentHandler.setGetCredentialResponse(
            resultIntent,
            GetCredentialResponse(DigitalCredential(responseJson)),
        )
    }

    /** Maps explicit user refusal to the platform cancellation exception. */
    public fun setCancellation(resultIntent: Intent) {
        PendingIntentHandler.setGetCredentialException(resultIntent, GetCredentialCancellationException())
    }

    /** Maps malformed, unsupported, or failed requests without leaking sensitive inputs. */
    public fun setFailure(resultIntent: Intent, message: String = "Digital credential presentation failed") {
        PendingIntentHandler.setGetCredentialException(resultIntent, GetCredentialUnknownException(message))
    }

    internal fun nativeAppOrigin(signingCertificate: ByteArray): String =
        "android:apk-key-hash:${Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(signingCertificate), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"

    internal fun String.canonicalWebOrigin(): String {
        val uri = URI(this)
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "[::1]"))) {
            "Credential Manager supplied an insecure browser origin"
        }
        require(uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Credential Manager supplied an invalid browser origin"
        }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
            "Credential Manager supplied a browser URL instead of an origin"
        }
        val port = when {
            uri.port == -1 -> ""
            uri.scheme == "https" && uri.port == 443 -> ""
            uri.scheme == "http" && uri.port == 80 -> ""
            else -> ":${uri.port}"
        }
        return "${uri.scheme}://${uri.host.lowercase()}$port"
    }

    /**
     * Matcher-backed registries return `<set-index> <protocol> <document-id>` while the AndroidX
     * OpenID registry returns the document id directly. Normalize only known protocol envelopes.
     */
    internal fun normalizeMatcherCredentialId(value: String): String {
        val parts = value.split(' ', limit = 3)
        val matcherProtocols = setOf(
            MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
            MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
            MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
            MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
        )
        return if (parts.size == 3 && parts[0].toIntOrNull() != null && parts[1] in matcherProtocols) {
            parts[2]
        } else value
    }
}
