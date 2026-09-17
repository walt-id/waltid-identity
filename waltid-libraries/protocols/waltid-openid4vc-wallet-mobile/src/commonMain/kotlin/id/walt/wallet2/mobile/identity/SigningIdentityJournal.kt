package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import kotlin.time.Clock

/** Owns encrypted journal encoding and atomic database transitions; performs no native or DID operations. */
internal class SigningIdentityJournal(private val walletId: String, private val queries: WalletPersistenceQueries) {
    fun read(): IdentityRecord? = queries.selectIdentityRecord(walletId).executeAsOneOrNull()?.let { row ->
        recordJson.decodeFromString<IdentityRecord>(row.payload).also { require(it.phase.name == row.phase) { "Journal phase mismatch" } }
    }

    fun write(record: IdentityRecord) {
        record.validate()
        queries.putIdentityRecord(walletId, record.phase.name, recordJson.encodeToString(record))
    }

    fun reserve(record: IdentityRecord) = queries.transaction {
        require(record.phase == IdentityPhase.Preparing)
        check(read() == record.previous) { "Identity state changed while reserving the operation" }
        val stored = queries.selectByKeyId(record.keyId).executeAsOneOrNull()?.let {
            StoredKeyCodec.decodeFromString(it.stored_key).also { key -> require(key.id.value == it.key_id) }
        }
        check(stored == record.previousKey) { "Recovery key ID already exists or changed" }
        write(record)
    }

    fun activate(record: IdentityRecord, recovery: RecoveryRecord?, confirmation: RecoveryConfirmation): SigningIdentity {
        val active = record.activated(recovery, confirmation)
        val identity = requireNotNull(active.identity)
        queries.transaction {
            write(active)
            queries.setActiveIdentity(walletId, identity.id, identity.keyId, identity.did)
        }
        return identity
    }

    /** Called after native cleanup. A failed repair restores the original descriptor and active binding together. */
    fun rollback(record: IdentityRecord) = queries.transaction {
        check(read() == record) { "Identity state changed during rollback" }
        if (record.previous == null) queries.deleteIdentityRecord(walletId)
        else {
            val key = requireNotNull(record.previousKey)
            queries.insert(key.id.value, Clock.System.now().toEpochMilliseconds(), StoredKeyCodec.encodeToString(key))
            write(record.previous)
            val identity = requireNotNull(record.previous.identity)
            queries.setActiveIdentity(walletId, identity.id, identity.keyId, identity.did)
        }
    }
}
