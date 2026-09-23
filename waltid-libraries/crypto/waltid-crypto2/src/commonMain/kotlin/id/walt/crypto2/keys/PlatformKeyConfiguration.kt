package id.walt.crypto2.keys

import kotlinx.serialization.Serializable

/** Platform-specific immutable key settings. Unsupported settings fail before key activation. */
@Serializable
sealed interface PlatformKeyConfiguration {
    /** Uses the platform adapter’s documented defaults. */
    @Serializable
    data object Default : PlatformKeyConfiguration

    /** Android controls apply to native generation and import unless marked generation-only. */
    @Serializable
    data class AndroidKeystore(
        val strongBox: HardwarePreference = HardwarePreference.PREFERRED,
        val unlockedDeviceRequired: Boolean = false,
        val userConfirmationRequired: Boolean = false,
        val userPresenceRequired: Boolean = false,
        val maxUsageCount: Int? = null,
        val validFromEpochMillis: Long? = null,
        val validUntilEpochMillis: Long? = null,
        /** Generation-only alias of an existing attestation key. */
        val attestKeyAlias: String? = null,
    ) : PlatformKeyConfiguration {
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
        val accessibility: KeychainAccessibility = KeychainAccessibility.WHEN_UNLOCKED_DEVICE_ONLY,
        val accessGroup: String? = null,
    ) : PlatformKeyConfiguration {
        init { require(accessGroup == null || accessGroup.isNotBlank()) }
    }
}

/** Native Keychain accessibility; device-only options cannot be used by synchronizable seed items. */
@Serializable
enum class KeychainAccessibility {
    WHEN_UNLOCKED, AFTER_FIRST_UNLOCK, WHEN_UNLOCKED_DEVICE_ONLY,
    AFTER_FIRST_UNLOCK_DEVICE_ONLY, WHEN_PASSCODE_SET_DEVICE_ONLY,
}

/** Whether the requested native hardware backing is mandatory, preferred or excluded. */
@Serializable
public enum class HardwarePreference { REQUIRED, PREFERRED, DISCOURAGED }
