package id.walt.issuer2.service

import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.controller.dto.CredentialOfferCreateRequest
import id.walt.issuer2.controller.dto.CredentialOfferCreateResponse
import id.walt.issuer2.domain.IssuanceSession
import id.walt.issuer2.utils.JsonObjectPathMapper
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssueRequest
import id.walt.openid4vci.preauthorized.PreAuthorizedCodeIssuer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CredentialOfferService(
    private val profileService: CredentialProfileService,
    private val sessionService: IssuanceSessionService,
    private val preAuthorizedCodeIssuer: PreAuthorizedCodeIssuer,
    private val config: Issuer2ServiceConfig,
) {
    suspend fun createCredentialOffer(request: CredentialOfferCreateRequest): CredentialOfferCreateResponse {
        val profile = profileService.resolveProfile(request.profileId)
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val expiresAt = expirationTimestamp(request.expiresInSeconds)
        val overrides = request.runtimeOverrides
        val issuerDid = overrides?.issuerDid ?: profile.issuerDid
        val webhookUrl = overrides?.webhookUrl ?: profile.webhookUrl
        val credentialData = profile.credentialData.mergeCredentialDataOverride(overrides?.credentialData)
        val idTokenClaimsMapping = overrides?.idTokenClaimsMapping ?: profile.idTokenClaimsMapping

        val issuerStateMode = when (request.authMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> null
            AuthenticationMethod.AUTHORIZED -> request.issuerStateMode ?: IssuerStateMode.OMIT
        }

        var resolvedTxCodeValue: String? = null
        val credentialOffer = when (request.authMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> {
                val oauthSession = DefaultSession(subject = sessionId)
                    .withExpiresAt(TokenType.ACCESS_TOKEN, expiresAt)
                val preAuthorizedCode = preAuthorizedCodeIssuer.issue(
                    PreAuthorizedCodeIssueRequest(
                        txCode = request.txCode,
                        txCodeValue = request.txCodeValue,
                        session = oauthSession,
                        scopes = emptySet(),
                        audience = emptySet(),
                    )
                )
                resolvedTxCodeValue = preAuthorizedCode.txCodeValue
                CredentialOffer.withPreAuthorizedCodeGrant(
                    credentialIssuer = issuerBaseUrl(),
                    credentialConfigurationIds = listOf(profile.credentialConfigurationId),
                    preAuthorizedCode = preAuthorizedCode.code,
                    txCode = request.txCode,
                )
            }

            AuthenticationMethod.AUTHORIZED ->
                CredentialOffer.withAuthorizationCodeGrant(
                    credentialIssuer = issuerBaseUrl(),
                    credentialConfigurationIds = listOf(profile.credentialConfigurationId),
                    issuerState = sessionId.takeIf { issuerStateMode == IssuerStateMode.INCLUDE },
                )
        }

        idTokenClaimsMapping?.let { mapping ->
            JsonObjectPathMapper.validateJsonObjectContainsPaths(
                jsonObject = credentialData,
                jsonPathList = mapping.values.toList(),
            )
        }

        val session = IssuanceSession(
            sessionId = sessionId,
            profileId = profile.profileId,
            authenticationMethod = request.authMethod,
            credentialConfigurationId = profile.credentialConfigurationId,
            credentialData = credentialData,
            mapping = overrides?.mapping ?: profile.mapping,
            selectiveDisclosure = overrides?.selectiveDisclosure ?: profile.selectiveDisclosure,
            idTokenClaimsMapping = idTokenClaimsMapping,
            mDocNameSpacesDataMappingConfig =
                overrides?.mDocNameSpacesDataMappingConfig ?: profile.mDocNameSpacesDataMappingConfig,
            x5Chain = overrides?.x5Chain ?: profile.x5Chain,
            issuerDid = issuerDid,
            credentialOffer = credentialOffer,
            expiresAt = expiresAt,
            webhookUrl = webhookUrl,
        )
        sessionService.createSession(session)

        val offerRequest = when (request.valueMode) {
            CredentialOfferValueMode.BY_VALUE -> CredentialOfferRequest(credentialOffer = credentialOffer)
            CredentialOfferValueMode.BY_REFERENCE -> CredentialOfferRequest(
                credentialOfferUri = "${issuerBaseUrl()}/credential-offer?id=$sessionId",
            )
        }

        return CredentialOfferCreateResponse(
            offerId = sessionId,
            profileId = profile.profileId,
            authMethod = request.authMethod,
            issuerStateMode = issuerStateMode,
            expiresAt = expiresAt.toEpochMilliseconds(),
            txCodeValue = resolvedTxCodeValue,
            credentialOffer = offerRequest.toUrl(),
        )
    }

    suspend fun getCredentialOffer(sessionId: String): CredentialOffer? =
        sessionService.getSessionOrNull(sessionId)?.credentialOffer

    private fun issuerBaseUrl(): String = config.baseUrl.trimEnd('/') + "/openid4vci"

    private fun expirationTimestamp(expiresInSeconds: Long): Instant =
        when (expiresInSeconds) {
            -1L -> Instant.DISTANT_FUTURE
            else -> Clock.System.now().plus(expiresInSeconds.seconds)
        }

    private fun JsonObject.mergeCredentialDataOverride(override: JsonObject?): JsonObject =
        override?.let { mergeCredentialDataPatch(it) } ?: this

    private fun JsonObject.mergeCredentialDataPatch(patch: JsonObject): JsonObject =
        JsonObject(
            toMutableMap().apply {
                patch.forEach { (key, patchValue) ->
                    this[key] = mergeCredentialDataValue(this[key], patchValue)
                }
            }
        )

    private fun mergeCredentialDataValue(
        configuredValue: JsonElement?,
        patchValue: JsonElement,
    ): JsonElement =
        if (configuredValue is JsonObject && patchValue is JsonObject) {
            configuredValue.mergeCredentialDataPatch(patchValue)
        } else {
            patchValue
        }
}