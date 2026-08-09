package id.walt.wallet2.mobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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
 * Desired IdentityDocumentServices registration state of the one wallet that owns this App Group.
 *
 * Deliberately a single wallet rather than a union of several: Apple hands the provider extension
 * only the parsed request, the requesting origin, `sendResponse` and `cancel`. The registration's
 * `documentIdentifier` is *not* passed to `ISO18013MobileDocumentRequestContext`, so an extension
 * serving a request cannot tell which wallet the request was registered for. A projection covering
 * several wallets would therefore describe registrations the extension could never fulfil. The
 * wallet that refreshed last is the active one, and its projection replaces whatever was there.
 *
 * [walletId] exists for the same reason: the extension has to open the host's database, and
 * `wallet_${walletId}` is what decides which database that is. Reading it from here rather than
 * assuming `"default"` is what keeps a host started with a non-default wallet id presentable.
 *
 * @property walletId Wallet whose credentials these registrations resolve to.
 * @property registryId Logical registry identifier the wallet published under, for diagnostics.
 * @property registrations Desired registrations, one per presentable mdoc credential.
 */
@Serializable
public data class IosIdentityDocumentProjectionState(
    public val walletId: String,
    public val registryId: String,
    public val registrations: List<IosIdentityDocumentProjectionRecord>,
)

/**
 * Outcome of reading the shared projection, with "absent" and "unreadable" kept apart.
 *
 * Reconciliation removes registrations, so collapsing these two into an empty state is not a
 * simplification but a data-loss bug: an unwritten or corrupt container would read as "this wallet
 * wants nothing registered" and every managed registration would be unregistered, silently removing
 * documents the wallet can still present. Only [Published] is authoritative.
 */
public sealed interface IosIdentityDocumentProjectionResult {
    /** No wallet has published a projection into this App Group yet. Apple's store must be left alone. */
    public data object Missing : IosIdentityDocumentProjectionResult

    /**
     * A projection exists but could not be decoded, so the wallet's intent is unknown.
     *
     * @property reason Decoder message, for the log line that is the only trace of this on a device.
     */
    public data class Malformed(public val reason: String) : IosIdentityDocumentProjectionResult

    /**
     * The active wallet's authoritative desired state.
     *
     * An empty [IosIdentityDocumentProjectionState.registrations] is a statement, not an absence:
     * the wallet holds no presentable mdoc credential, so its managed registrations must go.
     *
     * @property state Desired registration state as the wallet published it.
     */
    public data class Published(public val state: IosIdentityDocumentProjectionState) : IosIdentityDocumentProjectionResult
}

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
 * @param appGroupIdentifier App Group holding the projection; null disables Apple registration.
 * @param walletId Wallet this registry projects. Published with the projection so the provider
 * extension opens this wallet's database instead of guessing one.
 * @property capabilities Current iOS platform and registration availability.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosIdentityDocumentRegistry(
    private val appGroupIdentifier: String?,
    private val walletId: String,
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
     * The published state *replaces* whatever the App Group held, including a projection written by
     * another wallet: see [IosIdentityDocumentProjectionState] for why only one wallet at a time can
     * be served by a provider extension.
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

    private fun writeProjection(
        group: String,
        registryId: String,
        projection: List<IosIdentityDocumentProjectionRecord>,
    ) {
        // A blind overwrite, not a read-modify-write: this wallet's state is the whole desired state,
        // so there is nothing to merge and no lost update to guard against. An empty projection is
        // written rather than removed, because "this wallet holds no mdoc credential" must reach the
        // reconciler as an instruction to unregister and not as an unreadable container.
        NSUserDefaults(suiteName = group).setObject(
            projectionJson.encodeToString(
                IosIdentityDocumentProjectionState.serializer(),
                IosIdentityDocumentProjectionState(
                    walletId = walletId,
                    registryId = registryId,
                    registrations = projection,
                ),
            ),
            forKey = PROJECTION_STATE_KEY,
        )
    }

    /** Shared-defaults contract between the wallet core, the iOS host app, and its provider extension. */
    public companion object {
        /** Shared-defaults key holding the serialized [IosIdentityDocumentProjectionState]. */
        public const val PROJECTION_STATE_KEY: String = "id.walt.wallet.identity-document-projection"
        internal const val REGISTRATION_STATUS_KEY: String = "id.walt.wallet.identity-document-registration-status"

        private val projectionJson = Json { ignoreUnknownKeys = true }

        /**
         * Reads the active wallet's desired state from [appGroupIdentifier].
         *
         * Returns a result rather than a list so the caller can distinguish the three cases that
         * demand different behaviour. Both callers run where a thrown error would be swallowed - the
         * host app's scene-activation task and Apple's `performRegistrationUpdates()` - so the
         * distinction has to be carried in the return value to be actionable at all.
         */
        public fun readDesiredRegistrations(appGroupIdentifier: String): IosIdentityDocumentProjectionResult =
            readProjectionState(NSUserDefaults(suiteName = appGroupIdentifier))

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

        private fun readProjectionState(defaults: NSUserDefaults): IosIdentityDocumentProjectionResult {
            val stored = defaults.stringForKey(PROJECTION_STATE_KEY)
                ?: return IosIdentityDocumentProjectionResult.Missing
            return runCatching {
                projectionJson.decodeFromString(IosIdentityDocumentProjectionState.serializer(), stored)
            }.fold(
                onSuccess = { IosIdentityDocumentProjectionResult.Published(it) },
                onFailure = { failure ->
                    IosIdentityDocumentProjectionResult.Malformed(
                        failure.message ?: "The identity document projection could not be decoded",
                    )
                },
            )
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
