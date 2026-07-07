package id.walt.wallet2.persistence.encryption

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * iOS SDK-managed database key provider backed by Keychain generic-password items.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public class IosDatabaseEncryptionKeyProvider : DatabaseEncryptionKeyProvider {

    /**
     * Returns the existing Keychain database key or creates and stores a new one.
     */
    public override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val keyId = keyId(walletId, databaseName)
        readKeyMaterial(walletId, keyId)?.let { material ->
            return DatabaseEncryptionKey(keyId = keyId, material = material)
        }

        val material = generateKeyMaterial(walletId)
        return DatabaseEncryptionKey(keyId = keyId, material = storeKeyMaterial(walletId, keyId, material))
    }

    /**
     * Removes the database key stored in iOS Keychain.
     */
    public override suspend fun deleteKey(walletId: String, databaseName: String) {
        val keyId = keyId(walletId, databaseName)
        val query = keychainQuery(keyId)
        try {
            val status = SecItemDelete(query.dictionary)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw WalletPersistenceException.EncryptionConfigurationFailed(
                    walletId = walletId,
                    cause = IllegalStateException("Keychain delete failed with status $status"),
                )
            }
        } finally {
            query.release()
        }
    }

    private fun readKeyMaterial(walletId: String, keyId: String): ByteArray? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val query = keychainQuery(keyId, returnData = true)
        try {
            when (val status = SecItemCopyMatching(query.dictionary, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as NSData).toByteArray()
                errSecItemNotFound -> null
                else -> throw WalletPersistenceException.DatabaseUnlockFailed(
                    walletId = walletId,
                    cause = IllegalStateException("Keychain read failed with status $status"),
                )
            }
        } finally {
            query.release()
        }
    }

    private fun storeKeyMaterial(walletId: String, keyId: String, material: ByteArray): ByteArray {
        val query = keychainQuery(keyId, valueData = material)
        try {
            when (val status = SecItemAdd(query.dictionary, null)) {
                errSecSuccess -> return material
                errSecDuplicateItem -> return readKeyMaterial(walletId, keyId)
                    ?: throw WalletPersistenceException.DatabaseUnlockFailed(
                        walletId = walletId,
                        cause = IllegalStateException("Keychain duplicate item could not be reread"),
                    )

                else -> throw WalletPersistenceException.EncryptionConfigurationFailed(
                    walletId = walletId,
                    cause = IllegalStateException("Keychain write failed with status $status"),
                )
            }
        } finally {
            query.release()
        }
    }

    private fun generateKeyMaterial(walletId: String): ByteArray {
        val material = ByteArray(DATABASE_KEY_BYTES)
        material.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, material.size.toULong(), pinned.addressOf(0))
            if (status != errSecSuccess) {
                throw WalletPersistenceException.EncryptionConfigurationFailed(
                    walletId = walletId,
                    cause = IllegalStateException("Secure random failed with status $status"),
                )
            }
        }
        return material
    }

    private fun keychainQuery(
        keyId: String,
        returnData: Boolean = false,
        valueData: ByteArray? = null,
    ): RetainedDictionary {
        val query = RetainedDictionary(capacity = 7)
        query.add(kSecClass, kSecClassGenericPassword)
        query.addRetained(kSecAttrService, KEYCHAIN_SERVICE)
        query.addRetained(kSecAttrAccount, keyId)
        query.add(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        if (returnData) {
            query.add(kSecReturnData, kCFBooleanTrue)
            query.add(kSecMatchLimit, kSecMatchLimitOne)
        }
        if (valueData != null) {
            query.addRetained(kSecValueData, valueData.toNSData())
        }
        return query
    }

    private fun keyId(walletId: String, databaseName: String): String =
        "$KEYCHAIN_SERVICE:$walletId:$databaseName"

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray =
        ByteArray(length.toInt()).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, length)
            }
        }

    private class RetainedDictionary(capacity: Long) {
        val dictionary = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            capacity,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        private val retainedValues = mutableListOf<CFTypeRef?>()

        fun add(key: CFTypeRef?, value: CFTypeRef?) {
            CFDictionaryAddValue(dictionary, key, value)
        }

        fun addRetained(key: CFTypeRef?, value: Any?) {
            val retained = CFBridgingRetain(value)
            retainedValues += retained
            CFDictionaryAddValue(dictionary, key, retained)
        }

        fun release() {
            retainedValues.forEach { CFBridgingRelease(it) }
            CFBridgingRelease(dictionary)
        }
    }

    private companion object {
        const val DATABASE_KEY_BYTES = 32
        const val KEYCHAIN_SERVICE = "id.walt.wallet.database-key"
    }
}
