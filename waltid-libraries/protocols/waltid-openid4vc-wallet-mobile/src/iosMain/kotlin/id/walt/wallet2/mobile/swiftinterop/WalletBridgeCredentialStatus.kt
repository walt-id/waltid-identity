package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.data.WalletCredentialSummary
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletCredentialRead
import id.walt.wallet2.mobile.MobileWalletCredentialStatusValue
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.walt.wallet2.status.toResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** Display fields for one inventory card crossing the Swift boundary. */
public class WalletBridgeCredentialSummary internal constructor(row: WalletCredentialSummary) {
    /** The identifier used to read or remove the stored credential. */
    public val id: String = row.id
    /** The stored credential format identifier. */
    public val format: String = row.format
    /** The issuer identifier, when available. */
    public val issuer: String? = row.issuer
    /** The subject identifier, when available. */
    public val subject: String? = row.subject
    /** The display label stored with the credential, when available. */
    public val label: String? = row.label
    /** The time the credential was added to the wallet, when available. */
    public val addedAt: String? = row.addedAt
    /** JSON containing the format-specific type fields used to identify the card. */
    public val typeMetadataJson: String = row.typeMetadataJson
    /** Optional issuer or wallet display metadata encoded as JSON. */
    public val metadataJson: String? = row.metadataJson
}

/** A typed credential plus the shared semantic status representation for the native facade. */
public data class WalletBridgeCredentialRead(
    /** Existing credential/display data, independent of status updates. */
    public val credential: MobileWalletCredential,
    /** Typed shared status JSON consumed by the native facade. */
    public val statusJson: String,
)

/** Flow failures are explicit values; undeclared exceptions never escape a native collector. */
public sealed interface WalletBridgeCredentialStatusUpdate {
    /** A local initial/update snapshot; this value does not start issuer work. */
    public data class Snapshot(
        /** Encoded snapshot including semantic presentation and due time. */
        public val snapshotJson: String,
    ) : WalletBridgeCredentialStatusUpdate
    /** A sanitized observation failure, distinct from a clear or empty status. */
    public data class Failure(
        /** Typed native error without status-list contents. */
        public val error: WalletBridgeError,
    ) : WalletBridgeCredentialStatusUpdate
}

internal val credentialStatusJson: Json = Json { encodeDefaults = true }

internal fun MobileWalletCredentialRead.toStatusBridgeRead(): WalletBridgeCredentialRead = WalletBridgeCredentialRead(
    credential, credentialStatusJson.encodeToString(MobileWalletCredentialStatusValue.serializer(),
        MobileWalletCredentialStatusValue(status, presentation.toData(), evaluatedAt)),
)

internal fun WalletCredentialStatusSnapshot.toStatusBridgeSnapshot(): String {
    val status = credentialStatusJson.encodeToJsonElement(toResponse()).jsonObject
    val notices = credentialStatusJson.encodeToJsonElement(pendingNotices)
    return JsonObject(status + ("pendingNotices" to notices)).toString()
}
