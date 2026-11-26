package id.walt.wallet2.mobile.swiftinterop

import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusPresentationData
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.credentials.CredentialWithStatus
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.walt.wallet2.status.toResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** A typed credential plus the shared semantic status representation for the native facade. */
public data class WalletBridgeCredentialRead(
    /** Existing credential/display data, independent of status updates. */
    public val credential: WalletCredential,
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

internal fun CredentialWithStatus.toStatusBridgeRead(): WalletBridgeCredentialRead = WalletBridgeCredentialRead(
    credential, credentialStatusJson.encodeToString(CredentialStatusPayload.serializer(),
        CredentialStatusPayload(status, presentation.toData(), evaluatedAt.toString())),
)

internal fun WalletCredentialStatusSnapshot.toStatusBridgeSnapshot(): String {
    val status = credentialStatusJson.encodeToJsonElement(toResponse()).jsonObject
    val notices = credentialStatusJson.encodeToJsonElement(pendingNotices)
    return JsonObject(status + ("pendingNotices" to notices)).toString()
}

@Serializable
@SerialName("id.walt.wallet2.mobile.credentials.MobileWalletCredentialStatusValue")
private data class CredentialStatusPayload(
    val status: CredentialStatus,
    val presentation: CredentialStatusPresentationData,
    val evaluatedAt: String,
)
