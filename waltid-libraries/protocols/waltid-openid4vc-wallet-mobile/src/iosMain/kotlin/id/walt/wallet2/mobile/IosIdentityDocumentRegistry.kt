package id.walt.wallet2.mobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults

/**
 * App-to-extension metadata bridge for Apple's native IdentityDocumentServices registration owner.
 * The Swift app reads the shared document-type list and updates MobileDocumentRegistration objects.
 *
 * @property capabilities Current iOS platform and registration availability.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosIdentityDocumentRegistry(
    private val appGroupIdentifier: String?,
) : MobileWalletCredentialRegistry {
    private val ios26Available: Boolean
        get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

    private val registrationStatus: IosIdentityDocumentRegistrationStatus?
        get() = appGroupIdentifier
            ?.let { NSUserDefaults(suiteName = it) }
            ?.stringForKey(REGISTRATION_STATUS_KEY)
            ?.let(IosIdentityDocumentRegistrationStatus::fromStoredValue)

    private val unavailableReason: String?
        get() = when {
            !ios26Available -> "IdentityDocumentServices requires iOS/iPadOS 26"
            appGroupIdentifier == null -> "An App Group is required"
            registrationStatus == null -> "IdentityDocumentServices runtime status has not been reported"
            registrationStatus == IosIdentityDocumentRegistrationStatus.NOT_SUPPORTED -> "IdentityDocumentServices is not supported on this device"
            registrationStatus == IosIdentityDocumentRegistrationStatus.NOT_AUTHORIZED -> "IdentityDocumentServices registration is not authorized"
            registrationStatus == IosIdentityDocumentRegistrationStatus.NOT_DETERMINED -> "IdentityDocumentServices registration authorization is not determined"
            else -> null
        }

    override val capabilities: MobileWalletDigitalCredentialCapabilities
        get() {
            val status = registrationStatus
            val platformAvailable = ios26Available && status != null && status != IosIdentityDocumentRegistrationStatus.NOT_SUPPORTED
            val registrationAvailable = platformAvailable &&
                appGroupIdentifier != null &&
                status == IosIdentityDocumentRegistrationStatus.AUTHORIZED
            return MobileWalletDigitalCredentialCapabilities(
                platform = "iOS IdentityDocumentServices",
                platformAvailable = platformAvailable,
                minimumOsVersion = "iOS/iPadOS 26",
                registrationAvailable = registrationAvailable,
                capabilities = listOf(
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                        credentialFormats = listOf(MobileWalletDigitalCredentialFormat.MDOC),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.READER_AUTHENTICATED),
                        responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.HPKE),
                        supported = registrationAvailable,
                        unsupportedReason = unavailableReason,
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
                        credentialFormats = emptyList(),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.UNSIGNED),
                        responseProtection = emptyList(),
                        supported = false,
                        unsupportedReason = "IdentityDocumentServices exposes ISO 18013-7 mobile-document requests, not OpenID4VP",
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
                        credentialFormats = emptyList(),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.SIGNED),
                        responseProtection = emptyList(),
                        supported = false,
                        unsupportedReason = "IdentityDocumentServices does not expose OpenID4VP to third-party wallets",
                    ),
                    MobileWalletDigitalCredentialCapability(
                        protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_MULTISIGNED,
                        credentialFormats = emptyList(),
                        requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.MULTISIGNED),
                        responseProtection = emptyList(),
                        supported = false,
                        unsupportedReason = "IdentityDocumentServices does not expose OpenID4VP to third-party wallets",
                    ),
                ),
            )
        }

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        val group = appGroupIdentifier
            ?: return MobileWalletCredentialRegistrationResult(false, 0, "An App Group is required")
        if (!ios26Available) {
            return MobileWalletCredentialRegistrationResult(false, 0, "IdentityDocumentServices requires iOS/iPadOS 26")
        }
        // Checked before publishing: an unauthorized registration must not leave document types in
        // the shared container while reporting that nothing was registered.
        if (registrationStatus != IosIdentityDocumentRegistrationStatus.AUTHORIZED) {
            return MobileWalletCredentialRegistrationResult(false, 0, unavailableReason)
        }
        val defaults = NSUserDefaults(suiteName = group)
        val documentTypes = records
            .filter { it.format == MobileWalletDigitalCredentialFormat.MDOC }
            .map { it.type }
            .distinct()
            .sorted()
        defaults.setObject(documentTypes, forKey = DOCUMENT_TYPES_KEY)
        defaults.setObject(registryId, forKey = REGISTRY_ID_KEY)
        return MobileWalletCredentialRegistrationResult(true, documentTypes.size)
    }

    /** Shared-defaults keys consumed by the iOS app and provider extension. */
    public companion object {
        /** Shared-defaults key containing the currently registered mdoc document types. */
        public const val DOCUMENT_TYPES_KEY: String = "id.walt.wallet.identity-document-types"
        /** Shared-defaults key containing the current logical registry identifier. */
        public const val REGISTRY_ID_KEY: String = "id.walt.wallet.identity-document-registry-id"
        internal const val REGISTRATION_STATUS_KEY: String = "id.walt.wallet.identity-document-registration-status"

        /**
         * Records the runtime registration authorization Swift observed, for [capabilities] to read.
         *
         * Only the Swift app can query `IdentityDocumentProviderRegistrationStore`, so it reports the
         * status here rather than writing the shared container itself. Keeping the write on this side
         * means the defaults key and the stored spelling of each status exist in one place.
         */
        public fun reportRegistrationStatus(
            appGroupIdentifier: String,
            status: IosIdentityDocumentRegistrationStatus,
        ) {
            NSUserDefaults(suiteName = appGroupIdentifier)
                .setObject(status.storedValue, forKey = REGISTRATION_STATUS_KEY)
        }
    }
}

/**
 * Runtime authorization state of Apple's IdentityDocumentServices provider registration.
 *
 * Mirrors `IdentityDocumentProviderRegistrationStore.Status`, which only Swift can read. Each
 * [storedValue] is the stable identifier written to the shared App Group container, so it is a
 * cross-language wire value and must not be derived from the enum-entry name.
 *
 * @property storedValue Stable identifier persisted in the shared container.
 */
public enum class IosIdentityDocumentRegistrationStatus(public val storedValue: String) {
    /** The user authorized this wallet as an identity-document provider. */
    AUTHORIZED("authorized"),
    /** The user has not yet been asked. */
    NOT_DETERMINED("notDetermined"),
    /** The user declined, or authorization was revoked. */
    NOT_AUTHORIZED("notAuthorized"),
    /** This device cannot act as an identity-document provider. */
    NOT_SUPPORTED("notSupported"),
    ;

    internal companion object {
        fun fromStoredValue(value: String): IosIdentityDocumentRegistrationStatus? =
            entries.firstOrNull { it.storedValue == value }
    }
}
