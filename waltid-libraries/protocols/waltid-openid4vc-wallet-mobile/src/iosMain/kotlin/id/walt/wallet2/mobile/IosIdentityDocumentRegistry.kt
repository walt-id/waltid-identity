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
 * Carries document *identity* and *type* only - no claim values, no portrait, no issuer-signed
 * payload, no key material - because the shared App Group container is readable by every process in
 * the group and survives app deletion.
 *
 * @property documentIdentifier `MobileDocumentRegistration.documentIdentifier` for this credential.
 * Always the wallet's stable [MobileWalletCredentialRegistryRecord.registryEntryId].
 * @property documentType ISO mdoc doctype registered with Apple, for example `org.iso.18013.5.1.mDL`.
 */
@Serializable
public data class IosIdentityDocumentProjectionRecord(
    public val documentIdentifier: String,
    public val documentType: String,
)

/**
 * Desired IdentityDocumentServices registration state of the one wallet that owns this App Group.
 *
 * One wallet rather than a union of several: Apple hands the provider extension the parsed request,
 * the requesting origin, `sendResponse` and `cancel`, but not the matched registration's
 * `documentIdentifier`, so an extension cannot tell which wallet a request was registered for. The
 * wallet that refreshed last is the active one, and [walletId] names the `wallet_${walletId}` database
 * the extension must open.
 *
 * @property walletId Wallet whose credentials these registrations resolve to.
 * @property registrations Desired registrations, one per presentable mdoc credential.
 */
@Serializable
public data class IosIdentityDocumentProjectionState(
    public val walletId: String,
    public val registrations: List<IosIdentityDocumentProjectionRecord>,
)

/**
 * Outcome of reading the shared projection, with "absent" and "unreadable" kept apart.
 *
 * Reconciliation removes registrations, so only [Published] is authoritative: collapsing the other two
 * into an empty state would unregister every managed document whenever the container is unwritten or
 * corrupt.
 */
public sealed interface IosIdentityDocumentProjectionResult {
    /** No wallet has published a projection into this App Group yet. Apple's store must be left alone. */
    public data object Missing : IosIdentityDocumentProjectionResult

    /**
     * A projection exists but could not be decoded, so the wallet's intent is unknown.
     *
     * @property reason Decoder message.
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
 * state and reconcile Apple's actual registrations against it. The desired state is written regardless
 * of the reported authorization status, so authorizing the provider later lets
 * `performRegistrationUpdates()` rebuild Apple's registry without reissuing any credential.
 *
 * @param appGroupIdentifier App Group holding the projection; null disables Apple registration.
 * @param walletId Wallet this registry projects, published with the projection.
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
     * The published state *replaces* whatever the App Group held, including another wallet's
     * projection - see [IosIdentityDocumentProjectionState].
     *
     * [MobileWalletCredentialRegistrationResult.registeredEntryCount] counts desired registrations
     * written, not registrations Apple has accepted, because applying them is Swift's job.
     *
     * [registryId] is unused: Apple's store is keyed by document identifier alone.
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
                    documentType = it.type,
                )
            }

        val persisted = runCatching { writeProjection(group, projection) }
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
        projection: List<IosIdentityDocumentProjectionRecord>,
    ) {
        // An empty projection is written rather than removed, because "holds no mdoc credential" must
        // reach the reconciler as an instruction and not as an absent container.
        NSUserDefaults(suiteName = group).setObject(
            projectionJson.encodeToString(
                IosIdentityDocumentProjectionState.serializer(),
                IosIdentityDocumentProjectionState(
                    walletId = walletId,
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
         * Returns a result rather than a list because both callers - the host app's scene-activation
         * task and Apple's `performRegistrationUpdates()` - run where a thrown error is swallowed, so
         * the missing/malformed/published distinction has to survive in the return value.
         */
        public fun readDesiredRegistrations(appGroupIdentifier: String): IosIdentityDocumentProjectionResult =
            readProjectionState(NSUserDefaults(suiteName = appGroupIdentifier))

        /**
         * Records the runtime registration authorization Swift observed, for [capabilities] to read.
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
