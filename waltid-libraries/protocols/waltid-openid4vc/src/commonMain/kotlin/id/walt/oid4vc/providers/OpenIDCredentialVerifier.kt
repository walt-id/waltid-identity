package id.walt.oid4vc.providers

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.oid4vc.data.ClientIdScheme
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.interfaces.ISessionCache
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.ShortIdUtils
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class OpenIDCredentialVerifier(val config: CredentialVerifierConfig) :
    ISessionCache<PresentationSession> {

    /**
     * Override this method to cache presentation definition and append it to authorization request by reference
     * @return URI by which presentation definition can be resolved, or null, if full presentation definition object should be appended to authorization request
     */
    protected open fun preparePresentationDefinitionUri(
        presentationDefinition: PresentationDefinition,
        sessionID: String
    ): String? = null

    protected open fun prepareResponseOrRedirectUri(sessionID: String, responseMode: ResponseMode): String =
        when (responseMode) {
            ResponseMode.query, ResponseMode.fragment, ResponseMode.form_post -> config.redirectUri
            else -> config.responseUrl ?: config.redirectUri
        }

    open fun initializeAuthorization(
        presentationDefinition: PresentationDefinition,
        responseMode: ResponseMode = ResponseMode.fragment,
        responseType: ResponseType? = ResponseType.VpToken,
        scope: Set<String> = setOf(),
        expiresIn: Duration = 60.seconds,
        sessionId: String? = null, // A calling party may provide a unique session Id
        ephemeralEncKey: Key? = null,
        clientIdScheme: ClientIdScheme = config.defaultClientIdScheme,
        openId4VPProfile: OpenId4VPProfile = OpenId4VPProfile.DEFAULT,
        walletInitiatedAuthState: String? = null,
        trustedRootCAs: List<String>? = null,
        sessionTtl: Duration? = null // Custom TTL duration for the session
    ): PresentationSession {
        val session = PresentationSession(
            id = sessionId ?: ShortIdUtils.randomSessionId(),
            authorizationRequest = null,
            expirationTimestamp = Clock.System.now().plus(expiresIn),
            presentationDefinition = presentationDefinition,
            walletInitiatedAuthState = walletInitiatedAuthState,
            ephemeralEncKey = ephemeralEncKey,
            openId4VPProfile = openId4VPProfile,
            trustedRootCAs = trustedRootCAs
        ).also {
            putSession(it.id, it, sessionTtl ?: expiresIn)
        }
        val presentationDefinitionUri = when (openId4VPProfile) {
            OpenId4VPProfile.ISO_18013_7_MDOC, OpenId4VPProfile.HAIP -> null
            else -> preparePresentationDefinitionUri(presentationDefinition, session.id)
        }
        val authReq = AuthorizationRequest(
            // here add VpToken if response type is null
            responseType = setOf(responseType!!),
            clientId = when (openId4VPProfile) {
                OpenId4VPProfile.DEFAULT -> config.redirectUri
                OpenId4VPProfile.ISO_18013_7_MDOC -> config.redirectUri
                OpenId4VPProfile.EBSIV3 -> config.redirectUri.replace("/openid4vc/verify", "")
                else -> config.clientIdMap[clientIdScheme] ?: config.defaultClientId
            },
            responseMode = responseMode,
            redirectUri = when (openId4VPProfile) {
                OpenId4VPProfile.EBSIV3 -> prepareResponseOrRedirectUri(session.id, responseMode)
                else -> when (responseMode) {
                    ResponseMode.query, ResponseMode.fragment, ResponseMode.form_post -> prepareResponseOrRedirectUri(
                        session.id,
                        responseMode
                    )

                    else -> null
                }
            },
            responseUri = when (openId4VPProfile) {
                OpenId4VPProfile.EBSIV3 -> null
                else -> when (responseMode) {
                    ResponseMode.direct_post, ResponseMode.direct_post_jwt -> prepareResponseOrRedirectUri(
                        session.id,
                        responseMode
                    )

                    else -> null
                }
            },
            presentationDefinitionUri = presentationDefinitionUri,
            presentationDefinition = when (openId4VPProfile) {
                OpenId4VPProfile.EBSIV3 -> presentationDefinition // some wallets support presentation_definition only, even ebsiconformancetest wallet
                else -> when (presentationDefinitionUri) {
                    null -> presentationDefinition
                    else -> null
                }
            },
            scope = when (openId4VPProfile) {
                OpenId4VPProfile.EBSIV3 -> setOf("openid")
                else -> scope
            },
            state = session.id,
            clientIdScheme = clientIdScheme,
            nonce = randomUUIDString()
        )
        return session.copy(authorizationRequest = authReq).also {
            putSession(session.id, it, sessionTtl ?: expiresIn)
        }
    }

    open fun verify(tokenResponse: TokenResponse, session: PresentationSession): PresentationSession {
        // https://json-schema.org/specification
        // https://github.com/OptimumCode/json-schema-validator
        // Calculate the remaining time to live based on the session's expiration timestamp
        val remainingTtl = session.expirationTimestamp.let {
            val now = Clock.System.now()
            if (it > now) {
                it - now  // Calculate duration between now and expiration
            } else {
                null  // Already expired
            }
        }

        return session.copy(
            tokenResponse = tokenResponse,
            verificationResult = doVerify(
                tokenResponse = tokenResponse,
                session = session
            )
        ).also {
            putSession(
                id = it.id,
                session = it,
                ttl = remainingTtl
            )
        }
    }

    protected abstract fun doVerify(tokenResponse: TokenResponse, session: PresentationSession): Boolean
}
