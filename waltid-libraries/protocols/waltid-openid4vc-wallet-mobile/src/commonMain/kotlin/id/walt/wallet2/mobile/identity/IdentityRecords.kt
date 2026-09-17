package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

internal val identitySpec = KeySpec.Ec(EcCurve.P256)
internal val identityUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
internal val recordJson = Json { encodeDefaults = true }
internal val recoveryBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** Private, encrypted-database journal. Never exported as a backup or returned to app UI. */
@Serializable
internal data class IdentityRecord(
    val version: Int = 1,
    val id: String,
    val keyId: String,
    val nativeAlias: String = keyId,
    val phase: IdentityPhase,
    val storage: SigningIdentityKeyStorage,
    val requirements: WalletKeyRequirements,
    val policy: SigningIdentityKeyPolicy,
    val identity: SigningIdentity? = null,
    val recovery: RecoveryRecord? = null,
    val backup: IdentityBackupReference? = null,
    val recoveryAvailability: RecoveryAvailability.Available? = null,
    val recoveryConfirmation: RecoveryConfirmation = RecoveryConfirmation.LocalAcceptance,
    val pendingReason: SigningIdentityFailure = SigningIdentityFailure.ProviderUnavailable,
    val previous: IdentityRecord? = null,
    val previousKey: id.walt.crypto2.keys.StoredKey.Managed? = null,
) {
    init { validate() }

    fun validate() {
        require(version == 1 && id.isNotBlank() && keyId.isNotBlank() && nativeAlias.isNotBlank()) { "Invalid identity journal header" }
        if (phase != IdentityPhase.Preparing) requireNotNull(identity) { "Established journal phase requires identity metadata" }
        if (phase == IdentityPhase.AwaitingBackup) {
            requireNotNull(backup) { "Pending backup requires a destination" }
            requireNotNull(recovery) { "Pending backup requires its original record" }
        }
        identity?.let { require(it.id == id && it.keyId == keyId && it.storage == storage) { "Journal identity mismatch" } }
        recovery?.let {
            require(it.identityId == id && it.keyId == keyId) { "Journal recovery mismatch" }
            identity?.let { identity -> require(it.did == identity.did && it.publicJwk == identity.publicJwk) { "Journal key binding mismatch" } }
        }
        backup?.let { require(it.recordId == id) { "Journal backup identifier mismatch" } }
        require((previous == null) == (previousKey == null)) { "Rollback requires both the previous identity and descriptor" }
        previous?.let {
            require(phase == IdentityPhase.Preparing && it.phase == IdentityPhase.Active && it.previous == null && it.previousKey == null) {
                "Rollback predecessor must be a single active record"
            }
            require(it.id == id && it.keyId == keyId && previousKey?.id?.value == keyId) { "Rollback identity mismatch" }
        }
    }

    fun prepared(identity: SigningIdentity, recovery: RecoveryRecord? = this.recovery): IdentityRecord {
        check(phase == IdentityPhase.Preparing)
        return copy(identity = identity, recovery = recovery)
    }

    fun awaitingBackup(reason: SigningIdentityFailure): IdentityRecord {
        check(phase == IdentityPhase.Preparing || phase == IdentityPhase.AwaitingBackup)
        return copy(phase = IdentityPhase.AwaitingBackup, pendingReason = reason)
    }

    fun activated(recovery: RecoveryRecord?, confirmation: RecoveryConfirmation): IdentityRecord =
        copy(phase = IdentityPhase.Active, recovery = recovery, recoveryConfirmation = confirmation, previous = null, previousKey = null)
}

@Serializable
internal enum class IdentityPhase { Preparing, AwaitingBackup, Active }

/** Portable record contains only identity recovery material, never wallet data or database keys. */
@Serializable
internal data class RecoveryRecord(
    val format: String = "id.walt.identity-recovery",
    val version: Int = 1,
    val identityId: String,
    val keyId: String,
    val did: String,
    val publicJwk: String,
    val secret: RecoverySecret,
    val constraints: RecoveryConstraints,
) {
    override fun toString(): String = "RecoveryRecord(redacted)"

    fun encode(): ByteArray = recordJson.encodeToString(this).encodeToByteArray().also {
        require(it.size in 1..IdentityRecoveryData.MAX_BYTES)
    }

    suspend fun privateKey(): EncodedKey.Jwk {
        require(format == "id.walt.identity-recovery") { "Unsupported recovery format" }
        require(version == 1) { "Unsupported recovery version" }
        require(identityId.length in 1..128 && keyId.length in 1..256)
        require(did.length in 1..2048 && (did.startsWith("did:jwk:") || did.startsWith("did:key:")))
        val material = when (val source = secret) {
            is RecoverySecret.Derived -> {
                val seed = recoveryBase64.decode(source.seed)
                try { LegacyRecoveryDerivation.derive(seed, source.domain, source.index) }
                finally { seed.fill(0) }
            }
            is RecoverySecret.Exported -> EncodedKey.Jwk(BinaryData(source.jwk.encodeToByteArray()), true)
        }
        val expected = EncodedKey.Jwk(BinaryData(publicJwk.encodeToByteArray()), false).toSpkiDer(identitySpec)
        require(material.toSpkiDer(identitySpec) == expected) { "Recovery public key does not match its secret" }
        return material
    }
}

@Serializable
internal sealed interface RecoverySecret {
    @Serializable
    @kotlinx.serialization.SerialName("derived")
    data class Derived(val seed: String, val domain: String, val index: Int = 0) : RecoverySecret {
        override fun toString(): String = "Derived(redacted)"
    }
    @Serializable
    @kotlinx.serialization.SerialName("exported")
    data class Exported(val jwk: String) : RecoverySecret {
        override fun toString(): String = "Exported(redacted)"
    }
}

/** Portable minimums, independent of the original device's alias, access group or attestation. */
@Serializable
internal data class RecoveryConstraints(
    val storage: SigningIdentityKeyStorage,
    val authorization: id.walt.crypto2.keys.KeyUseAuthorizationPolicy,
    val confirmation: RecoveryConfirmation,
) {
    fun permits(storage: SigningIdentityKeyStorage, authorization: id.walt.crypto2.keys.KeyUseAuthorizationPolicy): Boolean =
        authorization == this.authorization && when (this.storage) {
            SigningIdentityKeyStorage.HardwareBacked -> storage == SigningIdentityKeyStorage.HardwareBacked
            SigningIdentityKeyStorage.NativeStorage -> storage != SigningIdentityKeyStorage.EncryptedDatabase
            SigningIdentityKeyStorage.EncryptedDatabase -> true
        }
}
