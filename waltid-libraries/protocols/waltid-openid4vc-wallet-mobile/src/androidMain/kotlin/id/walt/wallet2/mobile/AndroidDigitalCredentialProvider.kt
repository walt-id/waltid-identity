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
 * One selection Credential Manager attributed to a protocol request, as the matcher emitted it.
 *
 * @property registryEntryId Registry entry identifier the wallet registered.
 * @property requestIndex `requests` index the matcher attributed the selection to, or null when the
 *   matcher supplied no index.
 * @property protocol Protocol the matcher attributed the selection to, or null when it supplied none.
 */
internal data class MatcherCredentialSelection(
    val registryEntryId: String,
    val requestIndex: Int? = null,
    val protocol: String? = null,
)

/**
 * Which protocol request a whole [androidx.credentials.registry.provider.SelectedCredentialSet]
 * belongs to, and the entries selected within it.
 *
 * @property requestIndex Resolved `requests` index, or null when the matcher attributed none.
 * @property protocol Resolved protocol, or null when the matcher attributed none.
 * @property registryEntryIds Registry entry identifiers selected for that request, in matcher order.
 */
internal data class MatcherSelectedRequest(
    val requestIndex: Int?,
    val protocol: String?,
    val registryEntryIds: List<String>,
)

/** Android framework boundary for official holder Activity request and response handling. */
@OptIn(ExperimentalDigitalCredentialApi::class)
public object AndroidDigitalCredentialProvider {
    /**
     * Protocols this wallet can answer.
     *
     * A capability filter, not a preference order: it only rejects a selection the wallet cannot
     * fulfill, and never redirects one protocol to another.
     */
    private val supportedProtocols = setOf(
        MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
        MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
        MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
    )

    /**
     * `credentialSetId` values the OpenID4VP matcher emits: `req:<n>;null` for a plain DCQL match and
     * `req:<n>;set:<id>;option:<id>` when the match came from a `credential_sets` option. `<n>` is the
     * index into the request envelope's `requests` array.
     */
    private val openId4VpCredentialSetId = Regex("""^req:(\d+);(?:null|set:[^;]*;option:.*)$""")

    /**
     * Locates the protocol request Credential Manager's selection belongs to and derives the origin
     * from authenticated caller data. A populated privileged origin is rejected unless the caller
     * package and signing certificate match [privilegedAppsJson].
     *
     * A selected credential set is mandatory: continuing without one would produce an empty
     * `selectedRegistryEntryIds`, which [MobileWallet.previewDigitalCredentialPresentation] reads as
     * "match the whole wallet".
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
        // Only decides which origin flavour the platform asserted. Canonicalization stays in
        // DcApiWallet, which the mdoc session transcript is also built from.
        val assertedOrigin = privilegedOrigin
            ?: callingApp.signingInfoCompat.signingCertificateHistory.firstOrNull()?.toByteArray()?.let {
                nativeAppOrigin(it)
            }
            ?: throw IllegalArgumentException("Calling application has no signing certificate")
        val verifiedOrigin = DcApiWallet.canonicalizePlatformOrigin(assertedOrigin)

        val selectedSet = requireNotNull(providerRequest.selectedCredentialSet) {
            "Registry-based Digital Credentials request has no selected credential set"
        }
        val request = resolveSelectedProtocolRequest(
            requestJson = options.single().requestJson,
            verifiedOrigin = verifiedOrigin,
            selection = parseMatcherSelection(
                credentialSetId = selectedSet.credentialSetId,
                credentials = selectedSet.credentials.map { it.credentialId to it.metadata },
            ),
        )

        return AndroidDigitalCredentialProviderInput(request = request, providerRequest = providerRequest)
    }

    /**
     * Resolves the request selected by Credential Manager.
     *
     * Ambiguous or inconsistent matcher attribution fails closed; the wallet never substitutes another
     * protocol alternative. Two inferences are permitted, neither of them a wallet preference: a
     * single-alternative envelope, where index 0 is not a choice; and a protocol the matcher named that
     * exactly one alternative offers, which identifies it uniquely. Multipaz's Annex C matcher
     * attributes by protocol rather than by index, so without the second an Annex C alternative could
     * never be selected from a multi-protocol envelope.
     */
    internal fun resolveSelectedProtocolRequest(
        requestJson: String,
        verifiedOrigin: String,
        selection: MatcherSelectedRequest,
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
        val offeredProtocols = protocolRequests.map { protocolRequest ->
            protocolRequest["protocol"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Digital credential request protocol is required")
        }

        val selectedIndex = selection.requestIndex
            ?: selection.protocol?.let { attributedProtocol ->
                val candidates = offeredProtocols.withIndex()
                    .filter { (_, offered) -> offered == attributedProtocol }
                    .map { (index, _) -> index }
                require(candidates.isNotEmpty()) {
                    "Credential Manager attributed the selection to '$attributedProtocol', which the " +
                        "verifier did not offer"
                }
                require(candidates.size == 1) {
                    "Credential Manager attributed the selection to '$attributedProtocol', which the " +
                        "verifier offered ${candidates.size} times, so the request is ambiguous"
                }
                candidates.single()
            }
            ?: if (protocolRequests.size == 1) {
                0
            } else {
                throw IllegalArgumentException(
                    "Credential Manager selection does not name which of ${protocolRequests.size} " +
                        "protocol requests it was made against",
                )
            }
        require(selectedIndex in protocolRequests.indices) {
            "Credential Manager selected protocol request $selectedIndex, but the verifier offered " +
                "${protocolRequests.size}"
        }
        val protocol = offeredProtocols[selectedIndex]
        selection.protocol?.let { attributedProtocol ->
            require(attributedProtocol == protocol) {
                "Credential Manager attributed the selection to '$attributedProtocol' but protocol " +
                    "request $selectedIndex is '$protocol'"
            }
        }
        require(protocol in supportedProtocols) {
            "The selected Digital Credentials protocol is not supported: $protocol"
        }
        val data = protocolRequests[selectedIndex]["data"] as? JsonObject
            ?: throw IllegalArgumentException("Digital credential request data must be an object")
        require(selection.registryEntryIds.isNotEmpty()) {
            "Credential Manager reported a selection with no registry entries"
        }
        return MobileWalletDigitalCredentialRequest(
            protocol = protocol,
            dataJson = Json.encodeToString(JsonObject.serializer(), data),
            verifiedOrigin = verifiedOrigin,
            selectedRegistryEntryIds = selection.registryEntryIds,
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
     * Reduces one selected credential set to the single protocol request it belongs to.
     *
     * Every selected credential must agree on that request; a disagreement is an error rather than
     * something to resolve by majority or by taking the first. [credentialSetId] corroborates the
     * per-credential attribution when the OpenID4VP matcher supplies it, and is not the sole source.
     *
     * @param credentialSetId `credentialSetId` reported by Credential Manager.
     * @param credentials Selected `credentialId` to `metadata` pairs, in platform order. `metadata` is
     *   absent for a matcher that emits none, which is the Annex C case.
     */
    internal fun parseMatcherSelection(
        credentialSetId: String,
        credentials: List<Pair<String, String?>>,
    ): MatcherSelectedRequest {
        require(credentials.isNotEmpty()) { "Credential Manager reported an empty selected credential set" }
        val parsed = credentials.map { (credentialId, metadata) ->
            parseMatcherCredentialId(credentialId = credentialId, metadata = metadata)
        }
        val attributedIndexes = parsed.mapNotNull { it.requestIndex }.distinct()
        require(attributedIndexes.size <= 1) {
            "Credential Manager selected credentials from more than one protocol request: $attributedIndexes"
        }
        val attributedProtocols = parsed.mapNotNull { it.protocol }.distinct()
        require(attributedProtocols.size <= 1) {
            "Credential Manager selected credentials for more than one protocol: $attributedProtocols"
        }
        val setIdIndex = openId4VpCredentialSetId.matchEntire(credentialSetId)
            ?.groupValues?.get(1)?.toIntOrNull()
        val requestIndex = attributedIndexes.singleOrNull()
        if (setIdIndex != null && requestIndex != null) {
            require(setIdIndex == requestIndex) {
                "Credential Manager credential set '$credentialSetId' disagrees with its credentials, " +
                    "which name protocol request $requestIndex"
            }
        }
        return MatcherSelectedRequest(
            requestIndex = requestIndex ?: setIdIndex,
            protocol = attributedProtocols.singleOrNull(),
            registryEntryIds = parsed.map { it.registryEntryId },
        )
    }

    /**
     * Reads one selected credential the way the matcher that produced it writes them.
     *
     * The two matchers this wallet registers attribute differently:
     * - The AndroidX OpenID4VP matcher puts the registry entry id in `credentialId` and a JSON object
     *   in `metadata` whose `dc_request_index` names the `requests` entry it matched.
     * - Multipaz's `identitycredentialmatcher.wasm`, which serves the Annex C registry, has no
     *   metadata channel and instead emits `"<combination-index> <protocol> <document-id>"`. The
     *   leading integer is a combination counter, **not** a `requests` index, so only the protocol and
     *   document id are read from it. Such a selection therefore carries no request index, and
     *   [resolveSelectedProtocolRequest] attributes it by protocol instead.
     *
     * A `credentialId` in neither shape is passed through with no attribution, which
     * [resolveSelectedProtocolRequest] accepts only for a single-alternative envelope.
     */
    internal fun parseMatcherCredentialId(credentialId: String, metadata: String?): MatcherCredentialSelection {
        val requestIndex = metadata?.takeIf { it.isNotBlank() }?.let { rawMetadata ->
            val parsedMetadata = runCatching {
                Json.parseToJsonElement(rawMetadata).jsonObject
            }.getOrElse {
                throw IllegalArgumentException("Credential Manager selection metadata is not a JSON object")
            }
            parsedMetadata["dc_request_index"]?.jsonPrimitive?.content?.let { index ->
                index.toIntOrNull()
                    ?: throw IllegalArgumentException("Credential Manager selection names a non-numeric protocol request")
            }
        }
        val annexCParts = credentialId.split(' ', limit = 3)
        return if (annexCParts.size == 3 && annexCParts[0].toIntOrNull() != null &&
            annexCParts[1] == MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C
        ) {
            MatcherCredentialSelection(
                registryEntryId = annexCParts[2],
                requestIndex = requestIndex,
                protocol = annexCParts[1],
            )
        } else {
            MatcherCredentialSelection(registryEntryId = credentialId, requestIndex = requestIndex)
        }
    }
}
