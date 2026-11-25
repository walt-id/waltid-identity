package id.walt.issuer2.application

import id.walt.openid4vci.proofs.attestation.KeyAttestationKeyReferenceResolver
import id.walt.commons.config.ConfigManager
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.Issuer2ManagementController
import id.walt.issuer2.controller.OpenId4VciController
import id.walt.issuer2.notifications.IssuanceNotificationService
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.repository.IssuanceSessionRepository
import id.walt.issuer2.repository.CredentialStatusConsumptionRepository
import id.walt.issuer2.repository.configuredCredentialStatusStorage
import id.walt.issuer2.repository.ConfiguredCredentialStatusConsumptionRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPARRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredRefreshTokenRepository
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.IssuerTypeMetadataCatalogue
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningKeyReferenceResolver
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningCertificateReferenceResolver
import id.walt.issuer2.service.openid4vci.CredentialProofKeyAcceptance
import id.walt.issuer2.service.openid4vci.CredentialProofKeyCommitment
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService

class Issuer2Module @JvmOverloads constructor(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    profilesConfig: Issuer2ProfilesConfig,
    credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
    credentialProofKeyCommitment: CredentialProofKeyCommitment? = null,
    issuanceSessionRepository: IssuanceSessionRepository = ConfiguredIssuanceSessionRepository(),
    preAuthorizedCodeRepository: PreAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository(),
    keyAttestationKeyResolver: KeyAttestationKeyReferenceResolver? = null,
    metadataSigningKeyResolver: MetadataSigningKeyReferenceResolver? = null,
    metadataSigningCertificateResolver: MetadataSigningCertificateReferenceResolver? = null,
    credentialStatusConsumptionRepository: CredentialStatusConsumptionRepository? = null,
) : AutoCloseable {
    private val typeMetadata = IssuerTypeMetadataCatalogue(serviceConfig, metadataConfig)
    private val ownedStatusStorage = lazy(::configuredCredentialStatusStorage)
    private val authorizationCodeRepository = ConfiguredAuthorizationCodeRepository()
    private val refreshTokenRepository = ConfiguredRefreshTokenRepository()
    internal val statusConsumption: CredentialStatusConsumptionRepository
    val credentialOfferService: CredentialOfferService
    val managementController: Issuer2ManagementController
    val openId4VciController: OpenId4VciController

    init {
        try {
            statusConsumption = credentialStatusConsumptionRepository
                ?: ConfiguredCredentialStatusConsumptionRepository(ownedStatusStorage.value)
            val parRepository = ConfiguredPARRepository()
            val notificationService = IssuanceNotificationService()

            val openId4VciModule = OpenId4VciModule.create(
                config = serviceConfig,
                authorizationCodeRepository = authorizationCodeRepository,
                preAuthorizedCodeRepository = preAuthorizedCodeRepository,
                parRepository = parRepository,
                refreshTokenRepository = refreshTokenRepository,
                keyAttestationKeyResolver = keyAttestationKeyResolver,
                metadataResolver = typeMetadata,
            )

            val credentialProfileService = CredentialProfileService(
                profilesConfig = profilesConfig,
                metadataConfig = metadataConfig,
                preallocatedStatusEnabled = statusConsumption.durable,
            )

            val issuanceSessionService = IssuanceSessionService(
                repository = issuanceSessionRepository,
            )

            val metadataService = MetadataService(
                pkceServiceConfig = openId4VciModule.pkceServiceConfig,
                serviceConfig = serviceConfig,
                metadataConfig = metadataConfig,
                profileService = credentialProfileService,
                sessionService = issuanceSessionService,
                preAuthorizedGrantAnonymousAccessSupported =
                    openId4VciModule.preAuthorizedCodeIssuer.anonymousAccessSupported,
                crypto2TokenSigningKey = openId4VciModule.crypto2TokenSigningKey,
                metadataSigningKeyResolver = metadataSigningKeyResolver,
                metadataSigningCertificateResolver = metadataSigningCertificateResolver,
                typeMetadata = typeMetadata,
            )

            credentialOfferService = CredentialOfferService(
                profileService = credentialProfileService,
                sessionService = issuanceSessionService,
                preAuthorizedCodeIssuer = openId4VciModule.preAuthorizedCodeIssuer,
                config = serviceConfig,
                notificationService = notificationService,
                typeMetadata = typeMetadata,
            )

            val protocolService = OpenId4VciProtocolService(
                oauth2Provider = openId4VciModule.oauth2Provider,
                sessionService = issuanceSessionService,
                profileService = credentialProfileService,
                metadataService = metadataService,
                notificationService = notificationService,
                credentialProofKeyAcceptance = credentialProofKeyAcceptance,
                credentialProofKeyCommitment = credentialProofKeyCommitment,
                credentialNonceService = openId4VciModule.credentialNonceService,
                keyAttestation = openId4VciModule.keyAttestation,
                attestationChallengeService = openId4VciModule.attestationChallengeService,
                deferredConfig = serviceConfig.deferredIssuance,
                credentialStatusConsumption = statusConsumption,
            )

            managementController = Issuer2ManagementController(
                profileService = credentialProfileService,
                sessionService = issuanceSessionService,
                offerService = credentialOfferService,
            )

            openId4VciController = OpenId4VciController(
                metadataService = metadataService,
                protocolService = protocolService,
                offerService = credentialOfferService,
                notificationService = notificationService,
            )
        } catch (failure: Throwable) {
            runCatching { close() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    override fun close() {
        try {
            typeMetadata.close()
        } finally {
            if (ownedStatusStorage.isInitialized()) ownedStatusStorage.value.close()
        }
    }

    companion object {
        @JvmOverloads
        fun load(
            credentialProofKeyAcceptance: CredentialProofKeyAcceptance? = null,
            metadataSigningKeyResolver: MetadataSigningKeyReferenceResolver? = null,
            metadataSigningCertificateResolver: MetadataSigningCertificateReferenceResolver? = null,
        ): Issuer2Module =
            Issuer2Module(
                serviceConfig = ConfigManager.getConfig(),
                metadataConfig = ConfigManager.getConfig(),
                profilesConfig = ConfigManager.getConfig(),
                credentialProofKeyAcceptance = credentialProofKeyAcceptance,
                metadataSigningKeyResolver = metadataSigningKeyResolver,
                metadataSigningCertificateResolver = metadataSigningCertificateResolver,
            )
    }
}
