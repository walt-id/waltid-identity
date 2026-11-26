package id.walt.wallet2.mobile.credentials

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.mobile.internal.encodeJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Credential entry suitable for mobile UI lists and detail display.
 *
 * The credential content is exposed as a JSON string so Kotlin, Swift, and other
 * consumers can decode it with native platform tools without depending on Kotlinx
 * JSON value types in the public mobile API.
 *
 * @property id Wallet-local credential identifier.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property addedAt ISO-8601 timestamp string for when the credential was added, when known.
 * @property metadataJson Optional arbitrary metadata stored alongside the credential as JSON.
 */
public data class WalletCredential(
    public val id: String,
    public val format: String,
    public val credentialDataJson: String,
    public val issuer: String? = null,
    public val subject: String? = null,
    public val label: String? = null,
    public val addedAt: String? = null,
    public val metadataJson: String? = null,
)

internal fun StoredCredential.toMobileWalletCredential(): WalletCredential {
    val meta = toMetadata()
    return WalletCredential(id = meta.id, format = meta.format, issuer = meta.issuer,
        subject = meta.subject, label = meta.label, addedAt = meta.addedAt?.toString(),
        credentialDataJson = credential.credentialData.encodeJsonObject(),
        metadataJson = metadata?.let { Json.encodeToString(JsonObject.serializer(), it) })
}
