package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.dto.CreateCredentialOfferRequest
import id.walt.issuer2.controller.dto.CreateCredentialOfferResponse
import id.walt.issuer2.controller.dto.CredentialOfferDeliveryMode
import id.walt.issuer2.domain.AuthenticationMethod
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.service.CredentialProfileService
import id.walt.issuer2.service.IssuanceSessionService
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class CredentialOfferService(
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
    private val config: Issuer2ServiceConfig,
) {
    suspend fun createCredentialOffer(request: CreateCredentialOfferRequest): CreateCredentialOfferResponse {
        val profile = profileService.resolveProfile(request.profileId)
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val expiresAt = Clock.System.now().plus(request.expiresInSeconds.seconds)
        val overrides = request.runtimeOverrides

        val credentialData = overrides?.credentialData ?: profile.credentialData
        val mapping = overrides?.mapping ?: profile.mapping
        val issuerKeyId = overrides?.issuerKeyId ?: profile.issuerKeyId
        val issuerDid = overrides?.issuerDid ?: profile.issuerDid
        val webhookUrl = overrides?.webhookUrl ?: profile.webhookUrl

        val credentialOffer = when (request.authenticationMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> {
                val oauthSession = DefaultSession(subject = overrides?.subjectId ?: sessionId)
                    .withExpiresAt(TokenType.ACCESS_TOKEN, expiresAt)
                val preAuthorizedCode = preAuthorizedCodeIssuer.issue(
                    PreAuthorizedCodeIssueRequest(
                        session = oauthSession,
                        scopes = emptySet(),
                        audience = emptySet(),
                        issuanceSessionId = sessionId,
                    )
                )
                CredentialOffer.withPreAuthorizedCodeGrant(
                    credentialIssuer = issuerBaseUrl(),
                    credentialConfigurationIds = listOf(profile.credentialConfigurationId),
                    preAuthorizedCode = preAuthorizedCode.code,
                )
            }

            AuthenticationMethod.AUTHORIZATION_CODE ->
                CredentialOffer.withAuthorizationCodeGrant(
                    credentialIssuer = issuerBaseUrl(),
                    credentialConfigurationIds = listOf(profile.credentialConfigurationId),
                    issuerState = sessionId.takeIf { request.includeIssuerState },
                )
        }

        val session = IssuanceSession(
            sessionId = sessionId,
            profileId = profile.profileId,
            profileVersion = profile.version,
            authenticationMethod = request.authenticationMethod,
            credentialConfigurationId = profile.credentialConfigurationId,
            credentialData = credentialData,
            mapping = mapping,
            idTokenClaimsMapping = overrides?.idTokenClaimsMapping ?: profile.idTokenClaimsMapping,
            mdocNamespacesDataMapping =
                overrides?.mdocNamespacesDataMapping ?: profile.mdocNamespacesDataMapping,
            issuerKeyId = issuerKeyId,
            issuerDid = issuerDid,
            credentialOffer = credentialOffer,
            expiresAt = expiresAt,
            webhookUrl = webhookUrl,
        )
        sessionService.createSession(session)

        val offerRequest = when (request.deliveryMode) {
            CredentialOfferDeliveryMode.BY_VALUE -> CredentialOfferRequest(credentialOffer = credentialOffer)
            CredentialOfferDeliveryMode.BY_REFERENCE -> CredentialOfferRequest(
                credentialOfferUri = "${issuerBaseUrl()}/credential-offer?id=$sessionId",
            )
        }

        return CreateCredentialOfferResponse(
            sessionId = sessionId,
            profileId = profile.profileId,
            profileVersion = profile.version,
            authenticationMethod = request.authenticationMethod,
            expiresAt = expiresAt.toEpochMilliseconds(),
            credentialOfferUri = offerRequest.toUrl(),
        )
    }

    suspend fun getCredentialOffer(sessionId: String): CredentialOffer? =
        sessionService.getSessionOrNull(sessionId)?.credentialOffer

    private fun issuerBaseUrl(): String = config.baseUrl.trimEnd('/') + "/openid4vci"
}