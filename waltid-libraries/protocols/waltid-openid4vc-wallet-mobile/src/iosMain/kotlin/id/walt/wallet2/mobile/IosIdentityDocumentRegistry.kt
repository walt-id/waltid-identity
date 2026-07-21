package id.walt.wallet2.mobile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults

/**
 * App-to-extension metadata bridge for Apple's native IdentityDocumentServices registration owner.
 * The Swift app reads the shared document-type list and updates MobileDocumentRegistration objects.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosIdentityDocumentRegistry(
    private val appGroupIdentifier: String?,
) : MobileWalletCredentialRegistry {
    private val ios26Available: Boolean
        get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }

    override val capabilities: MobileWalletDigitalCredentialCapabilities
        get() = MobileWalletDigitalCredentialCapabilities(
            platform = "iOS IdentityDocumentServices",
            platformAvailable = ios26Available,
            minimumOsVersion = "iOS/iPadOS 26",
            registrationAvailable = ios26Available && appGroupIdentifier != null,
            capabilities = listOf(
                MobileWalletDigitalCredentialCapability(
                    protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
                    credentialFormats = listOf(MobileWalletDigitalCredentialFormat.MDOC),
                    requestProtection = listOf(MobileWalletDigitalCredentialRequestProtection.READER_AUTHENTICATED),
                    responseProtection = listOf(MobileWalletDigitalCredentialResponseProtection.HPKE),
                    supported = ios26Available,
                    unsupportedReason = if (ios26Available) null else "IdentityDocumentServices requires iOS/iPadOS 26",
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

    override suspend fun replace(
        registryId: String,
        records: List<MobileWalletCredentialRegistryRecord>,
    ): MobileWalletCredentialRegistrationResult {
        val group = appGroupIdentifier
            ?: return MobileWalletCredentialRegistrationResult(false, 0, "An App Group is required")
        if (!ios26Available) {
            return MobileWalletCredentialRegistrationResult(false, 0, "IdentityDocumentServices requires iOS/iPadOS 26")
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

    public companion object {
        public const val DOCUMENT_TYPES_KEY: String = "id.walt.wallet.identity-document-types"
        public const val REGISTRY_ID_KEY: String = "id.walt.wallet.identity-document-registry-id"
    }
}
