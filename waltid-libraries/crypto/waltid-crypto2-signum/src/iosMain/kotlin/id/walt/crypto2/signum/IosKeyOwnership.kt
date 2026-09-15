@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package id.walt.crypto2.signum

import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.signum.corefoundation.waltCfEqual
import kotlinx.cinterop.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/** Persisted execution engine; opening a key never repeats the current selection algorithm. */
@Serializable
internal enum class IosKeyEngine { SIGNUM, APPLE_KEYCHAIN }

/** Creation provenance, not independent readback of Apple's opaque access-control flags. */
@Serializable
internal data class IosKeyOwnership(
    val version: Int = 1,
    val engine: IosKeyEngine,
    val policy: SignumKeyPolicy,
    val spec: KeySpec,
    val usages: Set<KeyUsage>,
    val origin: SignumKeyOrigin,
    val publicKey: id.walt.crypto2.keys.EncodedKey.SpkiDer,
    val native: IosKeyIdentity,
) {
    fun validate(alias: String, policy: SignumKeyPolicy, spec: KeySpec, usages: Set<KeyUsage>, origin: SignumKeyOrigin) {
        if (version != 1 || this.policy != policy.immutableIosPolicy() || this.spec != spec ||
            this.usages != usages || this.origin != origin || origin == SignumKeyOrigin.UNKNOWN ||
            native.persistentReference.size == 0 || native.publicKeyIdentifier.size == 0 || publicKey.data.size == 0 ||
            (origin == SignumKeyOrigin.IMPORTED && native.secureEnclave)) {
            throw SignumKeyPolicyMismatchException(alias, "requested policy differs from the owned key's creation record")
        }
    }
}

@Serializable
internal data class IosKeyIdentity(
    val persistentReference: BinaryData,
    val publicKeyIdentifier: BinaryData,
    val accessibility: String?,
    val secureEnclave: Boolean,
)

internal fun SignumKeyPolicy.immutableIosPolicy(): SignumKeyPolicy = copy(
    platform = iosSettings(),
    authentication = (authentication as? SignumAuthenticationPolicy.UserPresence)?.copy(
        prompt = "Authorize key use", cancelText = "Cancel",
    ) ?: SignumAuthenticationPolicy.None,
)

internal fun SignumKeyPolicy.iosSettings(): SignumPlatformPolicy.IosKeychain =
    platform as? SignumPlatformPolicy.IosKeychain ?: SignumPlatformPolicy.IosKeychain()

/** Private native record. A missing record never authorizes adoption of an existing alias. */
internal object IosKeyOwnershipStore {
    fun read(alias: String, policy: SignumKeyPolicy): IosKeyOwnership? = receiptQuery(alias, policy).use { query ->
        query.put(kSecReturnData, kCFBooleanTrue)
        memScoped {
            val result = alloc<CFTypeRefVar>(); result.value = null
            val status = SecItemCopyMatching(query.ref, result.ptr)
            if (status == errSecItemNotFound) return@use null
            checkKeychainStatus(status, alias)
            val data = result.value ?: throw SignumStoredKeyMetadataException("Key ownership record is empty")
            val encoded = try { (data as CFDataRef).toBytes().decodeToString() } finally { CFRelease(data) }
            try { Json.decodeFromString<IosKeyOwnership>(encoded) }
            catch (cause: Exception) { throw SignumStoredKeyMetadataException("Invalid key ownership record", cause) }
        }
    }

    fun write(alias: String, policy: SignumKeyPolicy, record: IosKeyOwnership) = receiptQuery(alias, policy).use { query ->
        query.put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        query.putRetained(kSecValueData, Json.encodeToString(record).encodeToByteArray().toNSData())
        // Never overwrite a receipt belonging to another generation of the alias.
        checkKeychainStatus(SecItemAdd(query.ref, null), alias)
    }

    fun delete(alias: String, policy: SignumKeyPolicy) = receiptQuery(alias, policy).use {
        checkKeychainStatus(SecItemDelete(it.ref), alias, missingAllowed = true)
    }

    private fun receiptQuery(alias: String, policy: SignumKeyPolicy) = KeychainDictionary().apply {
        put(kSecClass, kSecClassGenericPassword)
        putRetained(kSecAttrService, "id.walt.crypto2.key-ownership.v1")
        putRetained(kSecAttrAccount, alias)
        policy.iosSettings().accessGroup?.let { putRetained(kSecAttrAccessGroup, it) }
        put(kSecUseDataProtectionKeychain, kCFBooleanTrue)
    }
}

internal fun iosKeyExists(alias: String, policy: SignumKeyPolicy, engine: IosKeyEngine): Boolean = try {
    iosKeyIdentity(alias, policy, engine) != null
} catch (_: SignumInteractionContextUnavailableException) { true }

/** Public Keychain attributes and the persistent reference of the actual private entry. */
internal fun iosKeyIdentity(alias: String, policy: SignumKeyPolicy, engine: IosKeyEngine): IosKeyIdentity? {
    val tags = when (engine) {
        IosKeyEngine.APPLE_KEYCHAIN -> listOf<Any>("id.walt.crypto2.native:$alias".encodeToByteArray().toNSData())
        IosKeyEngine.SIGNUM -> listOfNotNull("supreme.privatekey", NSBundle.mainBundle.bundleIdentifier?.let { "supreme.privatekey-$it" })
    }
    for (tag in tags) {
        val identity = KeychainDictionary().use { query ->
            query.put(kSecClass, kSecClassKey)
            query.put(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
            query.putRetained(kSecAttrApplicationTag, tag)
            if (engine == IosKeyEngine.SIGNUM) query.putRetained(kSecAttrApplicationLabel, alias)
            policy.iosSettings().accessGroup?.let { query.putRetained(kSecAttrAccessGroup, it) }
            query.put(kSecReturnAttributes, kCFBooleanTrue)
            query.put(kSecReturnPersistentRef, kCFBooleanTrue)
            query.put(kSecUseAuthenticationUI, kSecUseAuthenticationUIFail)
            memScoped {
                val result = alloc<CFTypeRefVar>(); result.value = null
                val status = SecItemCopyMatching(query.ref, result.ptr)
                if (status == errSecItemNotFound) return@use null
                checkKeychainStatus(status, alias)
                val attributes = result.value as? CFDictionaryRef
                    ?: throw SignumKeyPolicyMismatchException(alias, "native attributes are unavailable")
                try {
                    val reference = CFDictionaryGetValue(attributes, kSecValuePersistentRef) as? CFDataRef
                        ?: throw SignumKeyPolicyMismatchException(alias, "native persistent reference is unavailable")
                    val accessibility = CFDictionaryGetValue(attributes, kSecAttrAccessible)
                        ?: throw SignumKeyPolicyMismatchException(alias, "native accessibility is unavailable")
                    val label = CFDictionaryGetValue(attributes, kSecAttrApplicationLabel)
                        ?: throw SignumKeyPolicyMismatchException(alias, "native public-key identifier is unavailable")
                    val publicKeyIdentifier = if (CFGetTypeID(label) == CFDataGetTypeID()) (label as CFDataRef).toBytes()
                        else (CFBridgingRelease(CFRetain(label)) as String).encodeToByteArray()
                    val enclave = CFDictionaryGetValue(attributes, kSecAttrTokenID)?.let {
                        waltCfEqual(it, kSecAttrTokenIDSecureEnclave)
                    } == true
                    IosKeyIdentity(BinaryData(reference.toBytes()), BinaryData(publicKeyIdentifier),
                        if (policy.authentication == SignumAuthenticationPolicy.None) CFBridgingRelease(CFRetain(accessibility)) as String else null, enclave)
                } finally { CFRelease(attributes) }
            }
        }
        if (identity != null) return identity
    }
    return null
}

internal fun IosKeyIdentity.validate(alias: String, policy: SignumKeyPolicy) {
    val expectedAccessibility = CFBridgingRelease(CFRetain(policy.iosSettings().accessibility.nativeAccessibility)) as String
    if ((accessibility != null && accessibility != expectedAccessibility) ||
        (policy.hardware == SignumHardwarePolicy.REQUIRED && !secureEnclave) ||
        (policy.hardware == SignumHardwarePolicy.DISCOURAGED && secureEnclave)) {
        throw SignumKeyPolicyMismatchException(alias, "native backing ($secureEnclave) or accessibility ($accessibility) differs from the policy (${policy.hardware}, $expectedAccessibility)")
    }
}

internal val SignumKeychainAccessibility.nativeAccessibility: CFStringRef? get() = when (this) {
    SignumKeychainAccessibility.WHEN_UNLOCKED -> kSecAttrAccessibleWhenUnlocked
    SignumKeychainAccessibility.AFTER_FIRST_UNLOCK -> kSecAttrAccessibleAfterFirstUnlock
    SignumKeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY -> kSecAttrAccessibleWhenUnlockedThisDeviceOnly
    SignumKeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
    SignumKeychainAccessibility.WHEN_PASSCODE_SET_DEVICE_ONLY -> kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
}

internal class KeychainDictionary : AutoCloseable {
    val ref = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
    fun put(key: CFTypeRef?, value: CFTypeRef?) = CFDictionarySetValue(ref, key, value)
    fun putRetained(key: CFTypeRef?, value: Any) = retained(value) { put(key, it) }
    override fun close() = CFRelease(ref)
}

internal fun <T> retained(value: Any, block: (CFTypeRef) -> T): T {
    val reference = CFBridgingRetain(value)!!
    try { return block(reference) } finally { CFRelease(reference) }
}
internal fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
internal fun CFDataRef.toBytes(): ByteArray = CFDataGetBytePtr(this)!!.readBytes(CFDataGetLength(this).toInt())
