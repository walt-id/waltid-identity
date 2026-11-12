package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletCredentialRead
import id.walt.wallet2.mobile.MobileWalletCredentialStatusValue
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import kotlinx.serialization.json.Json

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
        MobileWalletCredentialStatusValue(status, presentation, evaluatedAt)),
)

internal fun WalletCredentialStatusSnapshot.toStatusBridgeSnapshot(): String =
    credentialStatusJson.encodeToString(WalletCredentialStatusSnapshot.serializer(), this)
