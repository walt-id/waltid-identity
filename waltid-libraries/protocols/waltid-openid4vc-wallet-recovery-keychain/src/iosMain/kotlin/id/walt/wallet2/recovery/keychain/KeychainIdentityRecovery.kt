@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.wallet2.recovery.keychain

import id.walt.wallet2.mobile.identity.IdentityRecoveryData
import id.walt.wallet2.mobile.identity.IdentityRecoveryProvider
import id.walt.wallet2.mobile.identity.RecoveryAvailability
import id.walt.wallet2.mobile.identity.RecoveryProtection
import id.walt.wallet2.mobile.identity.RecoveryReceipt
import id.walt.wallet2.mobile.identity.RecoveryScope
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/** Only accessibility classes compatible with synchronizable Keychain items are offered. */
public enum class SynchronizableKeychainAccessibility {
    /** Recovery material is available while the device is unlocked. */
    WhenUnlocked,
    /** Also permits background access after the first unlock following a restart. */
    AfterFirstUnlock,
}

/**
 * Opt-in iOS recovery provider using synchronizable generic-password items, never Enclave keys.
 * Keychain accepts local writes even when iCloud Keychain is disabled. Public APIs do not expose
 * per-item cloud delivery or deletion completion; this provider reports local acceptance only.
 */
public class KeychainIdentityRecovery(
    /** Stable namespace shared by the app's installations; it must not be a random device identifier. */
    private val namespace: String,
    /** Optional entitled Keychain access group, used consistently for read/write/delete. */
    private val accessGroup: String? = null,
    /** Device-only accessibility classes cannot be selected for this synchronizable record. */
    private val accessibility: SynchronizableKeychainAccessibility = SynchronizableKeychainAccessibility.WhenUnlocked,
) : IdentityRecoveryProvider {
    init {
        require(namespace.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid recovery namespace" }
        require(accessGroup == null || accessGroup.isNotBlank()) { "Keychain access group cannot be blank" }
    }
    override val id: String = "keychain:$namespace"
    override val displayName: String = "iCloud Keychain recovery"
    private val service = "id.walt.wallet.identity.recovery.$namespace"
    private val mutex = Mutex()

    override suspend fun availability(): RecoveryAvailability = withContext(Dispatchers.Default) {
        query().use { query ->
            query.put(kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
            when (val status = SecItemCopyMatching(query.ref, null)) {
                errSecSuccess, errSecItemNotFound -> RecoveryAvailability.Available(
                    RecoveryProtection.OperatingSystemEndToEnd, RecoveryScope.Cloud)
                errSecInteractionNotAllowed -> RecoveryAvailability.Unavailable("Unlock the device to access Keychain recovery")
                else -> RecoveryAvailability.Unavailable("Keychain recovery is unavailable (status $status)")
            }
        }
    }

    override suspend fun list(): List<String> = mutex.withLock { withContext(Dispatchers.Default) {
        query().use { query ->
            query.put(kSecReturnAttributes, kCFBooleanTrue)
            query.put(kSecMatchLimit, kSecMatchLimitAll)
            memScoped {
                val result = alloc<CFTypeRefVar>(); result.value = null
                when (val status = SecItemCopyMatching(query.ref, result.ptr)) {
                    errSecItemNotFound -> emptyList()
                    errSecSuccess -> {
                        val array = requireNotNull(result.value) as CFArrayRef
                        try {
                            (0 until CFArrayGetCount(array)).map { index ->
                                val item = requireNotNull(CFArrayGetValueAtIndex(array, index)) as CFDictionaryRef
                                val account = requireNotNull(CFDictionaryGetValue(item, kSecAttrAccount))
                                CFBridgingRelease(CFRetain(account)) as String
                            }
                        } finally { CFRelease(array) }
                    }
                    else -> error("Keychain list failed with status $status")
                }
            }
        }
    } }

    override suspend fun store(recordId: String, record: IdentityRecoveryData): RecoveryReceipt = mutex.withLock {
        withContext(Dispatchers.Default) {
            val bytes = record.copyBytes()
            try {
                query(recordId).use { query ->
                    query.put(kSecAttrAccessible, when (accessibility) {
                        SynchronizableKeychainAccessibility.WhenUnlocked -> kSecAttrAccessibleWhenUnlocked
                        SynchronizableKeychainAccessibility.AfterFirstUnlock -> kSecAttrAccessibleAfterFirstUnlock
                    })
                    query.putRetained(kSecValueData, bytes.toNSData())
                    when (val status = SecItemAdd(query.ref, null)) {
                        errSecSuccess -> Unit
                        errSecDuplicateItem -> {
                            val existing = requireNotNull(read(recordId)) { "Existing recovery record is unavailable" }
                            try { check(existing.contentEquals(bytes)) { "A different recovery record already uses this ID" } }
                            finally { existing.fill(0) }
                        }
                        else -> error("Keychain store failed with status $status")
                    }
                }
                RecoveryReceipt.AcceptedLocally
            } finally { bytes.fill(0) }
        }
    }

    override suspend fun retrieve(recordId: String): IdentityRecoveryData? = mutex.withLock {
        withContext(Dispatchers.Default) {
            val bytes = read(recordId) ?: return@withContext null
            try { IdentityRecoveryData(bytes) } finally { bytes.fill(0) }
        }
    }

    override suspend fun delete(recordId: String): RecoveryReceipt = mutex.withLock { withContext(Dispatchers.Default) {
        query(recordId).use { query ->
            val status = SecItemDelete(query.ref)
            check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed with status $status" }
        }
        RecoveryReceipt.AcceptedLocally
    } }

    private fun read(recordId: String): ByteArray? = query(recordId).use { query ->
        query.put(kSecReturnData, kCFBooleanTrue)
        query.put(kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>(); result.value = null
            when (val status = SecItemCopyMatching(query.ref, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val data = requireNotNull(result.value) as CFDataRef
                    try {
                        val size = CFDataGetLength(data).toInt()
                        require(size in 1..IdentityRecoveryData.MAX_BYTES) { "Invalid recovery record size" }
                        requireNotNull(CFDataGetBytePtr(data)).readBytes(size)
                    } finally { CFRelease(data) }
                }
                else -> error("Keychain read failed with status $status")
            }
        }
    }

    private fun query(recordId: String? = null): Dictionary = Dictionary().apply {
        put(kSecClass, kSecClassGenericPassword)
        putRetained(kSecAttrService, service)
        put(kSecAttrSynchronizable, kCFBooleanTrue)
        recordId?.let {
            require(it.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid recovery record ID" }
            putRetained(kSecAttrAccount, it)
        }
        accessGroup?.let { putRetained(kSecAttrAccessGroup, it) }
    }

    private class Dictionary : AutoCloseable {
        val ref = requireNotNull(CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
            kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr))
        fun put(key: CFTypeRef?, value: CFTypeRef?) = CFDictionarySetValue(ref, key, value)
        fun putRetained(key: CFTypeRef?, value: Any) {
            val retained = requireNotNull(CFBridgingRetain(value))
            try { put(key, retained) } finally { CFRelease(retained) }
        }
        override fun close() = CFRelease(ref)
    }
    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
