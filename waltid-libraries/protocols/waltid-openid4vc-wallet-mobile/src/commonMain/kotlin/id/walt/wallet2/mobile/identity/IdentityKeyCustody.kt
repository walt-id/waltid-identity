package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.EncodedKey
import kotlinx.serialization.Serializable

/** An explicitly trusted destination that receives a copy of the private signing key.
 * This is key custody, not portable identity recovery or remote signing. */
public interface IdentityKeyCustodian {
    /** Stable integration identifier, unique within one wallet configuration. */
    public val id: String
    /** Display name shown before the host selects this destination. */
    public val displayName: String
    /** Imports the key idempotently under the identity's stable key ID. A different existing key
     * must not be overwritten. Never log the private JWK or include it in an exception. */
    public suspend fun importKey(identity: WalletIdentity, privateKey: EncodedKey.Jwk): IdentityCustodyReceipt
}

/** Destination evidence, verified against the original public key before recording custody.
 * @property keyReference Destination's stable key resource reference.
 * @property publicJwk Public key returned by the destination, without private members. */
public data class IdentityCustodyReceipt(public val keyReference: String, public val publicJwk: String) {
    init { require(keyReference.isNotBlank() && keyReference.length <= 2048) }
}

/** A public reference to an additional private-key custodian; not a recovery record.
 * @property custodianId Stable registered custodian identifier.
 * @property keyReference Destination key resource reference. */
@Serializable
public data class IdentityCustodyReference(public val custodianId: String, public val keyReference: String)

/** SDK-issued choice whose export policy and local key are rechecked before transfer.
 * @property identityId Identity whose original key will be copied.
 * @property custodianName Display name of the trusted destination. */
public class IdentityCustodyOption internal constructor(
    internal val owner: Any,
    public val identityId: String,
    public val custodianName: String,
    internal val custodianId: String,
)

/** Key-custody outcome. The local signing key is retained and recovery status is unaffected. */
public sealed interface IdentityCustodyResult {
    /** The destination reported the original public key after import.
     * @property reference Stable destination key reference. */
    public data class Imported(public val reference: IdentityCustodyReference) : IdentityCustodyResult
    /** No verified custody reference was recorded. Retry is idempotent; remote keys are not deleted.
     * @property reason Stable failure category. */
    public data class Failed(public val reason: IdentityFailure) : IdentityCustodyResult
}
