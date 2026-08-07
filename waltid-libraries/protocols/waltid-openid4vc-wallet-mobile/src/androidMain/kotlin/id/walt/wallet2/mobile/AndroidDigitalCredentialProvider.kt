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
import id.waltid.openid4vp.wallet.DcApiWallet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Verified Android Credential Manager input after caller and selected-entry extraction.
 *
 * @property request Platform-neutral request derived from Credential Manager data.
 * @property providerRequest Original Credential Manager request needed to complete the response.
 */
public data class AndroidDigitalCredentialProviderInput(
    public val request: MobileWalletDigitalCredentialRequest,
    public val providerRequest: ProviderGetCredentialRequest,
)

/**
 * One platform matcher selection, decomposed into the protocol request it belongs to.
 *
 * @property requestIndex Leading index of the `requests` entry the matcher attributed the selection
 *   to, or null when the platform returned an opaque identifier.
 * @property protocol Protocol the matcher attributed the selection to, or null when opaque.
 * @property registryEntryId Registry entry identifier the wallet registered.
 */
internal data class MatcherCredentialSelection(
    val requestIndex: Int?,
    val protocol: String?,
    val registryEntryId: String,
)

/** Android framework boundary for official holder Activity request and response handling. */
@OptIn(ExperimentalDigitalCredentialApi::class)
public object AndroidDigitalCredentialProvider {
    /**
     * Protocols this wallet serves, most preferred first.
     *
     * The signed variants are deliberately absent rather than ranked last: an envelope offering only
     * signed protocols must be rejected outright, not served through a partial path.
     */
    private val servedProtocols = listOf(
        MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
        MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
    )

    /**
     * Every protocol identifier a matcher may name, including the unsupported ones, so that a
     * `<index> <protocol> <id>` envelope is recognised as such even when the protocol is refused.
     */
    private val matcherProtocols = setOf(
        MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
        MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
        MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
        MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
    )

    /**
     * Selects one supported Digital Credentials protocol request and derives its origin from
     * authenticated Credential Manager caller data. A populated privileged origin is rejected unless
     * the caller package and signing certificate match [privilegedAppsJson].
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
        // Canonicalization belongs to DcApiWallet, which is also what the mdoc session transcript is
        // built from. Normalizing here as well would let the two drift; this only decides *which*
        // origin flavour the platform asserted.
        val assertedOrigin = privilegedOrigin
            ?: callingApp.signingInfoCompat.signingCertificateHistory.firstOrNull()?.toByteArray()?.let {
                nativeAppOrigin(it)
            }
            ?: throw IllegalArgumentException("Calling application has no signing certificate")
        val verifiedOrigin = DcApiWallet.canonicalizePlatformOrigin(assertedOrigin)

        val request = parseProtocolRequest(
            requestJson = options.single().requestJson,
            verifiedOrigin = verifiedOrigin,
            selections = providerRequest.selectedCredentialSet?.credentials.orEmpty().map {
                parseMatcherCredentialId(it.credentialId)
            },
        )

        return AndroidDigitalCredentialProviderInput(request = request, providerRequest = providerRequest)
    }

    /**
     * Picks the one protocol request this wallet will answer out of a Digital Credentials envelope.
     *
     * A verifier may offer the same presentation over several alternative protocols
     * (OpenID4VP 1.0 Appendix A `requests`); the wallet answers exactly one of them. Preference order
     * is [servedProtocols], so the choice does not depend on the order the verifier happened to list.
     *
     * The matcher's selections are filtered to the chosen entry rather than passed through wholesale.
     * A `<index> <protocol> <id>` selection names the `requests` entry it belongs to, and an entry
     * selected for a protocol we did not choose is not a valid selection for the one we did - keeping
     * it would resolve a registry entry against the wrong protocol request.
     */
    internal fun parseProtocolRequest(
        requestJson: String,
        verifiedOrigin: String,
        selections: List<MatcherCredentialSelection> = emptyList(),
    ): MobileWalletDigitalCredentialRequest {
        val requestObject = Json.parseToJsonElement(requestJson).jsonObject
        val requests = requestObject["requests"] as? JsonArray
        val protocolRequests = when {
            requests != null -> {
                require(requests.isNotEmpty()) { "At least one protocol request is required" }
                requests.map { it.jsonObject }
            }
            requestObject["protocol"] != null -> listOf(requestObject)
            else -> throw IllegalArgumentException("Digital credential request has no protocol request")
        }
        val offered = protocolRequests.mapIndexed { index, protocolRequest ->
            index to (
                protocolRequest["protocol"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Digital credential request protocol is required")
                )
        }
        val (chosenIndex, protocol) = servedProtocols.firstNotNullOfOrNull { served ->
            offered.firstOrNull { it.second == served }
        } ?: throw IllegalArgumentException(
            "No offered Digital Credentials protocol is supported: ${offered.map { it.second }}",
        )
        val data = protocolRequests[chosenIndex]["data"] as? JsonObject
            ?: throw IllegalArgumentException("Digital credential request data must be an object")
        return MobileWalletDigitalCredentialRequest(
            protocol = protocol,
            dataJson = Json.encodeToString(JsonObject.serializer(), data),
            verifiedOrigin = verifiedOrigin,
            selectedRegistryEntryIds = selections
                .filter { it.matches(chosenIndex, protocol) }
                .map { it.registryEntryId },
        )
    }

    /**
     * Writes the official Credential Manager result payload to [resultIntent].
     *
     * [providerRequest] is the request this response answers, taken from
     * [AndroidDigitalCredentialProviderInput]. Credential Manager needs it to hand back a response
     * larger than the intent extra limit, which an mdoc carrying a portrait can reach.
     */
    public fun setResponse(
        resultIntent: Intent,
        response: MobileWalletDigitalCredentialResponse,
        providerRequest: ProviderGetCredentialRequest,
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
            providerRequest,
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

    /**
     * Matcher-backed registries return `<request-index> <protocol> <document-id>` while the AndroidX
     * OpenID registry returns the document id directly. The index and protocol identify which
     * `requests` entry the matcher matched, so they are retained rather than stripped; an opaque
     * identifier yields nulls, which [MatcherCredentialSelection.matches] treats as unattributed.
     */
    internal fun parseMatcherCredentialId(value: String): MatcherCredentialSelection {
        val parts = value.split(' ', limit = 3)
        val requestIndex = parts.getOrNull(0)?.toIntOrNull()
        return if (parts.size == 3 && requestIndex != null && parts[1] in matcherProtocols) {
            MatcherCredentialSelection(requestIndex, parts[1], parts[2])
        } else {
            MatcherCredentialSelection(requestIndex = null, protocol = null, registryEntryId = value)
        }
    }

    /**
     * Whether this selection applies to the chosen protocol request.
     *
     * An unattributed selection applies, because the AndroidX OpenID registry returns bare entry ids
     * and dropping those would discard every selection it makes. An attributed one must name both the
     * chosen protocol and the chosen `requests` entry, since the index the matcher emits is the index
     * into the same array this function chose from.
     */
    private fun MatcherCredentialSelection.matches(chosenIndex: Int, chosenProtocol: String): Boolean =
        protocol == null || (protocol == chosenProtocol && requestIndex == chosenIndex)
}
