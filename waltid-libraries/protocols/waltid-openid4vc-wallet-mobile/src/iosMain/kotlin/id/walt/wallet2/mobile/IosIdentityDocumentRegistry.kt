package id.walt.wallet2.mobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults

/**
 * One mdoc credential projected into Apple's IdentityDocumentServices registration model.
 *
 * Apple registers document *identity* and *type*, never the credential, so this deliberately
 * carries no claim values, no portrait, no issuer-signed payload, and no key material: the shared
 * App Group container is readable by every process in the group and survives app deletion.
 *
 * @property documentIdentifier `MobileDocumentRegistration.documentIdentifier` for this credential.
 * Always the wallet's stable [MobileWalletCredentialRegistryRecord.registryEntryId] so that a
 * refresh of an unchanged wallet is a no-op for Apple's store.
 * @property credentialId Wallet-local credential this registration resolves back to.
 * @property documentType ISO mdoc doctype registered with Apple, for example `org.iso.18013.5.1.mDL`.
 */
@Serializable
public data class IosIdentityDocumentProjectionRecord(
    public val documentIdentifier: String,
    public val credentialId: String,
    public val documentType: String,
)

/**
 * Desired IdentityDocumentServices registration state for every wallet sharing one App Group.
 *
 * Keyed by logical registry identifier so two wallets in the same container cannot overwrite each
 * other's projection, and so Swift reconciliation can treat the union of all registries as the set
 * of registrations this integration owns.
 *
 * @property registries Desired registrations per logical registry identifier.
 */
@Serializable
public data class IosIdentityDocumentProjectionState(
    public val registries: Map<String, List<IosIdentityDocumentProjectionRecord>> = emptyMap(),
)

/**
 * App-to-extension desired-state bridge for Apple's IdentityDocumentServices registration store.
 *
 * This publishes what the wallet *wants* registered; only Swift can talk to
 * `IdentityDocumentProviderRegistrationStore`, so the host app and the provider extension read this
 * state and reconcile Apple's actual registrations against it. Consequently the desired state is
 * written regardless of the reported authorization status: when the user later authorizes the
 * provider, `performRegistrationUpdates()` has to be able to rebuild Apple's registry without any
 * credential being reissued.
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

    /**
     * Publishes one desired Apple registration per presentable mdoc credential in [records].
     *
     * [MobileWalletCredentialRegistrationResult.registeredEntryCount] counts desired registrations
     * written for [registryId], not registrations Apple has accepted: applying them is Swift's job.
     * [MobileWalletCredentialRegistrationResult.available] therefore still reports whether Apple can
     * currently be updated at all, which is what an application surfaces to the user.
     */
    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        val group = appGroupIdentifier
            ?: return MobileWalletCredentialRegistrationResult(false, 0, "An App Group is required")
        // Only mdoc reaches Apple: IdentityDocumentServices registers ISO mobile documents, and an
        // SD-JWT VC has no doctype to register under.
        val projection = records
            .filter { it.format == MobileWalletDigitalCredentialFormat.MDOC }
            .distinctBy { it.registryEntryId }
            .map {
                IosIdentityDocumentProjectionRecord(
                    documentIdentifier = it.registryEntryId,
                    credentialId = it.credentialId,
                    documentType = it.type,
                )
            }

        val persisted = runCatching { writeProjection(group, registryId, projection) }
        persisted.exceptionOrNull()?.let { failure ->
            return MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = failure.message ?: "Identity document projection could not be persisted",
            )
        }

        return MobileWalletCredentialRegistrationResult(
            available = capabilities.registrationAvailable,
            registeredEntryCount = projection.size,
            reason = unavailableReason,
        )
    }

    private suspend fun writeProjection(
        group: String,
        registryId: String,
        projection: List<IosIdentityDocumentProjectionRecord>,
    ) {
        // Read-modify-write of one shared object: two wallet instances in this process refreshing
        // concurrently would otherwise drop one another's registry.
        stateMutex.withLock {
            val defaults = NSUserDefaults(suiteName = group)
            val current = readProjectionState(defaults)
            val registries = current.registries.toMutableMap()
            if (projection.isEmpty()) registries.remove(registryId) else registries[registryId] = projection
            if (registries.isEmpty()) {
                // Nothing left to project - after deleteWallet, for instance. Dropping the key
                // instead of storing an empty object keeps "no state" and "empty state" identical
                // for the reconciler, which must then remove every registration it owns.
                defaults.removeObjectForKey(PROJECTION_STATE_KEY)
            } else {
                defaults.setObject(
                    projectionJson.encodeToString(
                        IosIdentityDocumentProjectionState.serializer(),
                        IosIdentityDocumentProjectionState(registries),
                    ),
                    forKey = PROJECTION_STATE_KEY,
                )
            }
        }
    }

    /** Shared-defaults contract between the wallet core, the iOS host app, and its provider extension. */
    public companion object {
        /** Shared-defaults key holding the serialized [IosIdentityDocumentProjectionState]. */
        public const val PROJECTION_STATE_KEY: String = "id.walt.wallet.identity-document-projection"
        internal const val REGISTRATION_STATUS_KEY: String = "id.walt.wallet.identity-document-registration-status"

        private val stateMutex = Mutex()
        private val projectionJson = Json { ignoreUnknownKeys = true }

        /**
         * Returns the desired registrations of every wallet sharing [appGroupIdentifier].
         *
         * The reconciler needs the union, not one registry: registrations this integration owns are
         * exactly the ones it must be allowed to remove, and anything else in Apple's store belongs
         * to another provider integration and must be left alone.
         *
         * Unreadable or malformed state reads as empty rather than throwing, because the caller runs
         * inside Apple's `performRegistrationUpdates()` and the wallet store remains authoritative.
         */
        public fun readDesiredRegistrations(appGroupIdentifier: String): List<IosIdentityDocumentProjectionRecord> =
            readProjectionState(NSUserDefaults(suiteName = appGroupIdentifier))
                .registries
                .values
                .flatten()
                .distinctBy { it.documentIdentifier }

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

        private fun readProjectionState(defaults: NSUserDefaults): IosIdentityDocumentProjectionState =
            defaults.stringForKey(PROJECTION_STATE_KEY)
                ?.let {
                    runCatching {
                        projectionJson.decodeFromString(IosIdentityDocumentProjectionState.serializer(), it)
                    }.getOrNull()
                }
                ?: IosIdentityDocumentProjectionState()
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
