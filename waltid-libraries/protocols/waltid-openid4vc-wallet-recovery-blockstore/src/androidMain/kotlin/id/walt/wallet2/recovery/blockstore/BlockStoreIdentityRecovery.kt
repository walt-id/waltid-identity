package id.walt.wallet2.recovery.blockstore

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.BlockstoreClient
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.android.gms.tasks.Task
import id.walt.wallet2.mobile.identity.IdentityRecoveryData
import id.walt.wallet2.mobile.identity.IdentityRecoveryProvider
import id.walt.wallet2.mobile.identity.RecoveryAvailability
import id.walt.wallet2.mobile.identity.RecoveryProtection
import id.walt.wallet2.mobile.identity.RecoveryReceipt
import id.walt.wallet2.mobile.identity.RecoveryScope
import id.walt.wallet2.mobile.identity.IdentityProviderFailure
import id.walt.wallet2.mobile.identity.IdentityProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit delivery mode. Cloud mode refuses writes when OS end-to-end encryption is unavailable. */
public enum class BlockStoreRecoveryMode {
    /** Allows OS cloud backup only after its end-to-end encryption availability check succeeds. */
    EncryptedCloud,
    /** Enables device transfer without requesting cloud backup. */
    DeviceTransfer,
}

/**
 * Opt-in Android recovery provider. The base wallet SDK has no dependency on this artifact.
 * Block Store accepts at most 16 entries per app and 4096 bytes per entry. Submission and deletion
 * report local acceptance; neither operation proves that a cloud copy has been delivered or deleted.
 * Use one writable instance per namespace. Android's service supplies no compare-and-set primitive.
 */
public class BlockStoreIdentityRecovery(
    context: Context,
    /** Stable application/user namespace, retained across reinstall and destination devices. */
    private val namespace: String,
    /** Defaults to cloud backup with mandatory OS end-to-end protection. */
    private val mode: BlockStoreRecoveryMode = BlockStoreRecoveryMode.EncryptedCloud,
) : IdentityRecoveryProvider {
    private val client = Blockstore.getClient(context.applicationContext)
    private val mutex = Mutex()
    init { require(namespace.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid recovery namespace" } }
    override val id: String = "blockstore:$namespace:${mode.name}"
    override val displayName: String = when (mode) {
        BlockStoreRecoveryMode.EncryptedCloud -> "Android encrypted cloud backup"
        BlockStoreRecoveryMode.DeviceTransfer -> "Android device transfer"
    }
    private val prefix = "$id/"

    override suspend fun availability(): RecoveryAvailability = try {
        // The service call also checks whether Play Services currently exposes Block Store.
        val endToEnd = client.isEndToEndEncryptionAvailable.awaitResult()
        when (mode) {
            BlockStoreRecoveryMode.EncryptedCloud -> if (endToEnd)
                RecoveryAvailability.Available(RecoveryProtection.OperatingSystemEndToEnd, RecoveryScope.Cloud)
            else RecoveryAvailability.Unavailable("Block Store cloud end-to-end encryption is unavailable")
            BlockStoreRecoveryMode.DeviceTransfer ->
                RecoveryAvailability.Available(RecoveryProtection.OperatingSystemProtected, RecoveryScope.DeviceTransfer)
        }
    } catch (cause: CancellationException) { throw cause }
    catch (_: Exception) { RecoveryAvailability.Unavailable("Block Store is unavailable") }

    override suspend fun list(): List<String> = mutex.withLock {
        client.retrieveBytes(RetrieveBytesRequest.Builder().setRetrieveAll(true).build()).awaitResult()
            .blockstoreDataMap.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }

    override suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt = mutex.withLock {
        if (availability() !is RecoveryAvailability.Available) throw IdentityProviderException(IdentityProviderFailure.TemporarilyUnavailable)
        val key = key(recordId)
        val bytes = record.copyBytes()
        try {
            require(bytes.size <= BlockstoreClient.MAX_SIZE) { "Recovery record exceeds the Block Store entry limit" }
            val existing = retrieveUnlocked(recordId)
            try {
                if (existing != null && !existing.contentEquals(bytes)) throw IdentityProviderException(IdentityProviderFailure.Conflict)
            } finally { existing?.fill(0) }
            val accepted = client.storeBytes(StoreBytesData.Builder().setKey(key).setBytes(bytes)
                .setShouldBackupToCloud(mode == BlockStoreRecoveryMode.EncryptedCloud).build()).awaitResult()
            check(accepted == bytes.size) { "Block Store did not accept the complete record" }
            RecoveryReceipt.AcceptedLocally
        } finally { bytes.fill(0) }
    }

    override suspend fun retrieve(recordId: String): IdentityRecoveryData? = mutex.withLock {
        val bytes = retrieveUnlocked(recordId) ?: return@withLock null
        try { IdentityRecoveryData(bytes) } finally { bytes.fill(0) }
    }

    override suspend fun delete(recordId: String): RecoveryReceipt = mutex.withLock {
        client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(key(recordId))).build()).awaitResult()
        RecoveryReceipt.AcceptedLocally
    }

    private suspend fun retrieveUnlocked(recordId: String): ByteArray? {
        val key = key(recordId)
        return client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(key)).build()).awaitResult()
            .blockstoreDataMap[key]?.bytes
    }

    private fun key(recordId: String): String {
        require(recordId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid recovery record ID" }
        return prefix + recordId
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(IdentityProviderException(IdentityProviderFailure.TemporarilyUnavailable)) }
    addOnCanceledListener { continuation.cancel() }
}
