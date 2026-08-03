package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyInfo
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

    override suspend fun getKey(keyId: String): Key? {
        val row = queries.selectByKeyId(keyId).executeAsOneOrNull() ?: return null
        val record = row.toRecord()
        val key = if (record.isPlatformBacked) {
            keyProvider.load(record)
        } else {
            row.key_material?.let { keyProvider.loadSoftwareKey(record.keyId, record.keyType, it.encodeToByteArray()) }
        }
        return key ?: if (record.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            null
        } else {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.ProtectedKeyUnavailable,
                message = "The protected key '$keyId' is unavailable",
            )
        }
    }

    override suspend fun listKeys(): Flow<WalletKeyInfo> = flow {
        listKeyRecords().collect { record ->
            emit(WalletKeyInfo(keyId = record.keyId, keyType = record.keyType.name))
        }
    }

    override suspend fun listKeyRecords(): Flow<MobileWalletKeyRecord> = flow {
        queries.selectAll().executeAsList().forEach { row ->
            emit(row.toRecord())
        }
    }

    override suspend fun addKey(key: Key): String = addKey(
        key,
        MobileWalletKeyRecord(
            keyId = key.getKeyId(),
            keyType = key.keyType,
            isPlatformBacked = true,
        ),
    )

    override suspend fun addKey(key: Key, record: MobileWalletKeyRecord): String {
        require(record.keyId == key.getKeyId()) { "Key record identifier does not match the key" }
        require(record.keyType == key.keyType) { "Key record type does not match the key" }
        if (record.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None) {
            require(record.isPlatformBacked) { "Protected keys must be platform-backed" }
        }
        val keyMaterial: String? = if (record.isPlatformBacked) {
            null
        } else {
            keyProvider.exportSoftwareKeyMaterial(key).decodeToString()
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
        queries.selectByKeyId(keyId).executeAsOneOrNull()?.toRecord()

    private fun id.walt.wallet2.persistence.db.Key_references.toRecord(): MobileWalletKeyRecord {
        val policy = runCatching { KeyUseAuthorizationPolicy.valueOf(authorization_policy) }
            .getOrElse {
                throw KeyUseAuthorizationException(
                    failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                    message = "Stored authorization policy for key '$key_id' is invalid",
                    cause = it,
                )
            }
        val keyType = runCatching { KeyType.valueOf(key_type) }
            .getOrElse {
                throw KeyUseAuthorizationException(
                    failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                    message = "Stored key type for key '$key_id' is invalid",
                    cause = it,
                )
            }
        if (policy != KeyUseAuthorizationPolicy.None && (is_platform_backed != 1L || key_material != null)) {
            throw KeyUseAuthorizationException(
                failure = KeyUseAuthorizationFailure.InvalidStoredKeyMetadata,
                message = "Protected key '$key_id' has inconsistent stored metadata",
            )
        }
        return MobileWalletKeyRecord(
            keyId = key_id,
            keyType = keyType,
            keyUseAuthorizationPolicy = policy,
            isPlatformBacked = is_platform_backed == 1L,
        )
    }
}
