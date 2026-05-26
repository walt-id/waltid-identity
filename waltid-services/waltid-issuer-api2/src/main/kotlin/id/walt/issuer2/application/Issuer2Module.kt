package id.walt.issuer2.application

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.config.CredentialProfileConfigProvider
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.Issuer2ManagementController
import id.walt.issuer2.controller.OpenId4VciController
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredPreAuthorizedCodeRepository
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.issuer2.service.IssuerKeyResolver
import id.walt.issuer2.service.openid4vci.CredentialOfferService
import id.walt.issuer2.service.openid4vci.MetadataService
import id.walt.issuer2.service.openid4vci.OpenId4VciProtocolService

class Issuer2Module(
    serviceConfig: Issuer2ServiceConfig,
    metadataConfig: Issuer2MetadataConfig,
    profilesConfig: Issuer2ProfilesConfig,
) {
    private val profileConfigProvider = CredentialProfileConfigProvider(profilesConfig)
    private val issuerKeyResolver = IssuerKeyResolver(serviceConfig)
    private val issuanceSessionRepository = ConfiguredIssuanceSessionRepository()
    private val authorizationCodeRepository = ConfiguredAuthorizationCodeRepository()
    private val preAuthorizedCodeRepository = ConfiguredPreAuthorizedCodeRepository()
    private val openId4VciModule = OpenId4VciModule.create(
        config = serviceConfig,
        authorizationCodeRepository = authorizationCodeRepository,
        preAuthorizedCodeRepository = preAuthorizedCodeRepository,
    )

    private val credentialProfileService = CredentialProfileService(
        profileConfigProvider = profileConfigProvider,
        metadataConfig = metadataConfig,
    )
    private val issuanceSessionService = IssuanceSessionService(
        repository = issuanceSessionRepository,
    )
    private val metadataService = MetadataService(
        serviceConfig = serviceConfig,
        metadataConfig = metadataConfig,
        issuerKeyResolver = issuerKeyResolver,
    )
    )
    private val credentialOfferService = CredentialOfferService(
        profileService = credentialProfileService,
        sessionService = issuanceSessionService,
        preAuthorizedCodeIssuer = openId4VciModule.preAuthorizedCodeIssuer,
        config = serviceConfig,
    )
    private val protocolService = OpenId4VciProtocolService(
        oauth2Provider = openId4VciModule.oauth2Provider,
        sessionService = issuanceSessionService,
        metadataService = metadataService,
    )

    val managementController = Issuer2ManagementController(
        profileService = credentialProfileService,
        sessionService = issuanceSessionService,
        offerService = credentialOfferService,
    )
    val openId4VciController = OpenId4VciController(
        metadataService = metadataService,
        protocolService = protocolService,
        offerService = credentialOfferService,
    )

    companion object {
        fun load(): Issuer2Module =
            Issuer2Module(
                serviceConfig = ConfigManager.getConfig(),
                metadataConfig = ConfigManager.getConfig(),
                profilesConfig = ConfigManager.getConfig(),
            )
    }
}
