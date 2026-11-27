package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.waltid.openid4vp.wallet.PresentationRequestValidator
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * Defers the "a key is required" failure to the point where the key is actually used.
 *
 * The key is still selected once, before anything else, so every consumer sees the same key. But a
 * request that is rejected without ever needing a key - an untrusted client identifier, a malformed
 * request - must surface that rejection, not a wallet-local missing-key error.
 */
internal fun WalletKeyStoreEntry?.requiredOnUse(): () -> WalletKeyStoreEntry = {
    this ?: error("No key available: wallet has no keyStores and no staticKey")
}

/**
 * Presentation capabilities of exactly this key material.
 *
 * Deliberately scoped to one key rather than to the union of every signing key in the wallet:
 * submission signs with a single key, so anything advertised to or accepted from a Verifier beyond
 * that key's capabilities could be accepted while previewing and then fail while signing.
 * Mirrors the capability computation in `WalletPresentFunctionality2.walletPresentHandlingWithKey`.
 */
internal fun WalletKeyStoreEntry.presentationCapabilities(): WalletPresentationFormatRegistry.RuntimeCapabilities =
    WalletPresentationFormatRegistry.capabilitiesFromKeys(
        keys = listOfNotNull(crypto2Key),
        fallbackKeyTypes = setOfNotNull(legacyKey?.keyType?.takeIf { crypto2Key == null }),
    )

/**
 * Request-level format checks see the wallet default key. mdoc device authentication uses the
 * key bound to each credential, so those COSE algorithms are added before
 * [PresentationRequestValidator] decides whether any requested format is possible.
 */
internal suspend fun WalletPresentationFormatRegistry.RuntimeCapabilities.withBoundMdocDeviceAuthentication(
    stored: List<StoredCredential>,
    resolveMdocHolderKey: suspend (credentialId: String, credential: DigitalCredential) -> Crypto2Key,
): WalletPresentationFormatRegistry.RuntimeCapabilities {
    val boundMdocAlgorithms = stored.flatMap { storedCredential ->
        if (
            WalletPresentationFormatRegistry.resolve(storedCredential.credential.format) !=
            WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC
        ) {
            return@flatMap emptyList()
        }
        storedCredential.mdocCapabilities(resolveMdocHolderKey)?.supportedMdocCoseAlgorithms.orEmpty()
    }
    val mdocAlgorithms = (supportedMdocCoseAlgorithms + boundMdocAlgorithms).distinct().sorted()
    return copy(
        supportedFormats = if (mdocAlgorithms.isEmpty()) {
            supportedFormats
        } else {
            supportedFormats + WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC
        },
        supportedMdocCoseAlgorithms = mdocAlgorithms,
    )
}

internal suspend fun List<PresentationCredentialOption>.compatibleWithVerifierFormats(
    verifierFormats: Map<String, JsonObject>?,
    capabilitiesFor: suspend (PresentationCredentialOption) -> WalletPresentationFormatRegistry.RuntimeCapabilities?,
): List<PresentationCredentialOption> = buildList {
    for (option in this@compatibleWithVerifierFormats) {
        val format = WalletPresentationFormatRegistry.resolve(option.format) ?: continue
        val capabilities = capabilitiesFor(option) ?: continue
        if (WalletPresentationFormatRegistry.supportsFormat(format, verifierFormats, capabilities)) {
            add(option)
        }
    }
}

internal suspend fun StoredCredential.mdocCapabilities(
    resolveHolderKey: suspend (String, DigitalCredential) -> Crypto2Key,
): WalletPresentationFormatRegistry.RuntimeCapabilities? = try {
    WalletPresentationFormatRegistry.capabilitiesFromKeys(listOf(resolveHolderKey(id, credential)))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
