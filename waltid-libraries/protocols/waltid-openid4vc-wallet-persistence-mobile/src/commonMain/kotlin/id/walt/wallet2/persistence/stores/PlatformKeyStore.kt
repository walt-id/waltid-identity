package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/** SQLDelight-backed mobile key store with one authoritative policy record per key. */
public class PlatformKeyStore(
    private val keyProvider: PlatformKeyProvider,
    private val queries: WalletPersistenceQueries,
) : MobileWalletKeyStore {

    /** Loads and validates the key record before returning key material. */
    override suspend fun getKey(keyId: String): Key? {
        val row = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val record = row.toValidatedRecord()
        val key = if (record.isPlatformBacked) {
            keyProvider.load(record)
        } else {
            keyProvider.loadSoftwareKey(
                record.keyId,
                record.keyType,
                requireNotNull(row.key_material).encodeToByteArray(),
            ) ?: invalidMetadata("Software key '${record.keyId}' could not be loaded from its stored material")
        }
        if (key == null && record.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                message = "The protected key '$keyId' is unavailable",
            )
        }
        if (key == null) return null
        val loadedKeyId = runCatching { key.getKeyId() }.getOrElse {
            invalidMetadata("Loaded key '${record.keyId}' did not expose a usable identifier", it)
        }
        if (loadedKeyId != record.keyId || key.keyType != record.keyType) {
            invalidMetadata("Loaded key '${record.keyId}' does not match its stored metadata")
        }
        return key
    }

    /** Streams validated immutable key records from the local database. */
    override suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord> = flow {
        queries.selectAll().executeAsList().forEach { row ->
            emit(row.toValidatedRecord())
        }
    }

    /** Persists a complete key record atomically; incomplete metadata is rejected fail-closed. */
    override suspend fun addKey(key: Key, record: MobileWalletKeyRecord): String {
        val actualKeyId = key.getKeyId()
        if (record.keyId.isBlank() || record.keyId != actualKeyId) {
            invalidMetadata("Key record identifier does not match the key")
        }
        if (record.keyType != key.keyType) {
            invalidMetadata("Key record type does not match the key")
        }
        if (record.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None) {
            if (!record.isPlatformBacked) invalidMetadata("Protected keys must be platform-backed")
        }
        val keyMaterial: String? = if (record.isPlatformBacked) {
            null
        } else {
            keyProvider.exportSoftwareKeyMaterial(key).decodeToString().also {
                if (it.isBlank()) invalidMetadata("Software key '${record.keyId}' has empty serialized material")
            }
        }
        queries.insert(
            key_id = record.keyId,
            key_type = record.keyType.name,
            created_at = Clock.System.now().toEpochMilliseconds(),
            is_platform_backed = if (record.isPlatformBacked) 1L else 0L,
            key_material = keyMaterial,
            authorization_policy = record.keyUseAuthorizationPolicy.name,
        )
        return record.keyId
    }

    override suspend fun removeKey(keyId: String): Boolean {
        val record = recordFor(keyId) ?: return false
        keyProvider.delete(record)
        queries.deleteByKeyId(keyId)
        return true
    }

    private suspend fun recordFor(keyId: String): MobileWalletKeyRecord? =
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.toValidatedRecord()

    private fun id.walt.wallet2.persistence.db.Key_references.toValidatedRecord(): MobileWalletKeyRecord {
        if (key_id.isBlank()) invalidMetadata("Stored key identifier is blank")
        if (is_platform_backed != 0L && is_platform_backed != 1L) {
            invalidMetadata("Stored platform-backing flag for key '$key_id' is invalid")
        }
        val policy = runCatching { KeyUseAuthorizationPolicy.valueOf(authorization_policy) }
            .getOrElse {
                invalidMetadata("Stored authorization policy for key '$key_id' is invalid", it)
            }
        val keyType = runCatching { KeyType.valueOf(key_type) }
            .getOrElse {
                invalidMetadata("Stored key type for key '$key_id' is invalid", it)
            }
        val platformBacked = is_platform_backed == 1L
        if (platformBacked && key_material != null) {
            invalidMetadata("Platform-backed key '$key_id' must not contain software material")
        }
        if (!platformBacked && key_material.isNullOrBlank()) {
            invalidMetadata("Software key '$key_id' must contain serialized material")
        }
        if (policy != KeyUseAuthorizationPolicy.None && (!platformBacked || key_material != null)) {
            invalidMetadata("Protected key '$key_id' has inconsistent stored metadata")
        }
        return MobileWalletKeyRecord(
            keyId = key_id,
            keyType = keyType,
            keyUseAuthorizationPolicy = policy,
            isPlatformBacked = platformBacked,
        )
    }

    private fun invalidMetadata(message: String, cause: Throwable? = null): Nothing =
        throw KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
            message = message,
            cause = cause,
        )
}
