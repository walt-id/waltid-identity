package id.walt.crypto2.signum

import kotlinx.serialization.Serializable

/** Platform-specific immutable key settings. Unsupported settings fail before key activation. */
@Serializable
sealed interface SignumPlatformPolicy {
    /** Retains the backend's existing platform defaults. */
    @Serializable
    data object Default : SignumPlatformPolicy

    /** Android controls apply to native generation and import unless marked generation-only. */
    @Serializable
    data class AndroidKeystore(
        val strongBox: SignumHardwarePolicy = SignumHardwarePolicy.PREFERRED,
        val unlockedDeviceRequired: Boolean = false,
        val userConfirmationRequired: Boolean = false,
        val userPresenceRequired: Boolean = false,
        val maxUsageCount: Int? = null,
        val validFromEpochMillis: Long? = null,
        val validUntilEpochMillis: Long? = null,
        /** Generation-only alias of an existing attestation key. */
        val attestKeyAlias: String? = null,
    ) : SignumPlatformPolicy {
        init {
            require(maxUsageCount == null || maxUsageCount > 0)
            require(validFromEpochMillis == null || validUntilEpochMillis == null ||
                validFromEpochMillis < validUntilEpochMillis)
            require(attestKeyAlias == null || attestKeyAlias.isNotBlank())
        }
    }

    /** iOS signing-key storage is always local; seed synchronization is configured separately. */
    @Serializable
    data class IosKeychain(
        val accessibility: SignumKeychainAccessibility = SignumKeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY,
        val accessGroup: String? = null,
    ) : SignumPlatformPolicy {
        init { require(accessGroup == null || accessGroup.isNotBlank()) }
    }
}

/** Native Keychain accessibility; device-only options cannot be used by synchronizable seed items. */
@Serializable
enum class SignumKeychainAccessibility {
    WHEN_UNLOCKED, AFTER_FIRST_UNLOCK, WHEN_UNLOCKED_DEVICE_ONLY,
    AFTER_FIRST_UNLOCK_DEVICE_ONLY, WHEN_PASSCODE_SET_DEVICE_ONLY,
}
