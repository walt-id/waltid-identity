package id.walt.openid4vp.verifier.handlers.vpresponse

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.verifier.data.SessionEvent
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.Verifier2Response
import id.walt.openid4vp.verifier.utils.JsonUtils.parseAsJsonObject
import id.walt.openid4vp.verifier.verification2.PresentationVerificationEngine
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object Verifier2VPDirectPostHandler {

    private val log = KotlinLogging.logger {}

    suspend fun parseResponseBody(
        responseMode: OpenID4VPResponseMode?,

        responseData: DirectPostResponse,

        ephemeralDecryptionKey: DirectSerializedKey?
    ): Pair<String, String?> = when (responseData) {

        is DcApiJsonDirectPostResponse -> {
            require(responseMode in OpenID4VPResponseMode.DC_API_RESPONSES) { "Used body response, but responseMode is not for DC API" }
            val bodyJson = responseData.jsonBody
            //val protocol = bodyJson["protocol"].jsonPrimitive.content

            /*when (protocol) {
                "openid4vp-v1-signed"
                else -> NotImplementedError("Protocol \"$protocol\" is not supported.")
            }*/

            val bodyData = bodyJson["data"] ?: bodyJson["credential"]?.jsonObject["data"]
            ?: throw IllegalArgumentException("Missing $.data/$.credential.data in posted JSON body of DC API response")

            val vpToken = bodyData.jsonObject["vp_token"]?.jsonObject?.toString()
            val response = bodyData.jsonObject["response"]?.jsonPrimitive?.content

            if (vpToken == null && response == null) {
                throw IllegalArgumentException("Missing $.data.vp_token or response in posted JSON body of DC API response")
            }

            if (vpToken != null) {
                vpToken to null
            } else if (response != null) {
                val (vpToken, state) = parseResponseBody(
                    responseMode = responseMode,
                    responseData = EncryptedResponseStringDirectPostResponse(response),
                    ephemeralDecryptionKey = ephemeralDecryptionKey
                )
                vpToken to state
            } else {
                throw IllegalArgumentException("Missing any response content")
            }
        }

        is EncryptedResponseStringDirectPostResponse -> {
            require(responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) {
                "Called encrypted flow, but responseMode is not for encrypted response"
            }

            log.trace { "Decrypting encrypted token..." }
            requireNotNull(ephemeralDecryptionKey) { "Missing decryption key for encrypted response flow" }
            val decryptedPayloadString = (ephemeralDecryptionKey.key as JWKKey).decryptJwe(responseData.responseParameter)
                .decodeToString()
            val jsonPayload = Json.parseToJsonElement(decryptedPayloadString).jsonObject
            val vpToken = jsonPayload["vp_token"].toString() // Extract the inner vp_token object
            val state = jsonPayload["state"]?.jsonPrimitive?.content

            vpToken to state
        }

        is CleartextDirectPostResponse -> {
            require(responseMode in OpenID4VPResponseMode.CLEARTEXT_NORMAL_RESPONSES) {
                "Called cleartext flow, but responseMode is for different (e.g. encrypted, DC API) response"
            }

            responseData.vpToken to responseData.state
        }
    }

    suspend fun RoutingCall.parseHttpRequestToDirectPostResponse(): DirectPostResponse {

        val providedContentType = this.request.contentType()

        return when {
            providedContentType.match(ContentType.Application.Json) -> {
                val bodyText = this.receiveText()
                log.trace { "Verification session data - body: $bodyText" }
                val bodyJsonObject = bodyText.parseAsJsonObject("Could not parse provided body text as JSON object for DC API flow")

                DcApiJsonDirectPostResponse(bodyJsonObject)
            }

            else -> {
                val urlParameters = this.receiveParameters()
                val responseString = urlParameters["response"]
                val vpTokenString = urlParameters["vp_token"]
                val receivedState = urlParameters["state"]

                log.trace { "Verification session data: state = $receivedState, vp_token = $vpTokenString, response = $responseString" }

                when {
                    responseString != null -> EncryptedResponseStringDirectPostResponse(
                        responseParameter = responseString
                    )

                    vpTokenString != null -> CleartextDirectPostResponse(
                        vpToken = vpTokenString,
                        state = receivedState ?: Verifier2Response.Verifier2Error.MISSING_STATE_PARAMETER.throwAsError()
                    )

                    else -> throw IllegalArgumentException("No presentation data was included in request")
                }
            }
        }
    }

    suspend fun RoutingCall.respondHandleDirectPostResponse(
        verificationSession: Verification2Session?,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit,
    ) {
        val call = this

        if (verificationSession == null) {
            Verifier2Response.Verifier2Error.UNKNOWN_VERIFICATION_SESSION.throwAsError()
        }

        val result = handleDirectPost(
            verificationSession = verificationSession,
            responseData = call.parseHttpRequestToDirectPostResponse(),
            updateSessionCallback = updateSessionCallback,
            failSessionCallback = failSessionCallback
        )

        call.respond(
            result
        )
    }

    /**
     * Sealed (= limited option) interface to represent the different forms that
     * a direct post response can come in
     */
    sealed interface DirectPostResponse

    /** Custom DC API JSON Object structure */
    data class DcApiJsonDirectPostResponse(val jsonBody: JsonObject) : DirectPostResponse

    /** Encrypted response (Data is in JWT String in URL parameter 'response') */
    data class EncryptedResponseStringDirectPostResponse(val responseParameter: String) : DirectPostResponse

    /** Cleartext response (Data is directly passed as strings in URL parameter 'vp_token' and 'state') */
    data class CleartextDirectPostResponse(val vpToken: String, val state: String) : DirectPostResponse

    /**
     * Here the receiving of credentials through the Verifiers endpoints
     * (e.g. direct_post endpoint) is handled
     */
    suspend fun handleDirectPost(
        verificationSession: Verification2Session,
        responseData: DirectPostResponse,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit,
    ): Map<String, String> {
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        suspend fun Verification2Session.failSession(event: SessionEvent) =
            failSessionCallback.invoke(this, event, updateSessionCallback)

        log.debug { "Handling direct post for received data: $responseData" }

        val session = verificationSession
        val responseMode = session.authorizationRequest.responseMode

        val (vpTokenString, receivedState) = parseResponseBody(
            responseMode = responseMode,
            responseData = responseData,
            ephemeralDecryptionKey = session.ephemeralDecryptionKey
        )

        if (receivedState != session.authorizationRequest.state) {
            Verifier2Response.Verifier2Error.INVALID_STATE_PARAMETER.throwAsError()
        }

        // Parse vp_token
        val vpTokenContents = parseVpToken(vpTokenString)
        log.debug { "Parsed vp_token for state $receivedState: $vpTokenContents" }

        session.updateSession(SessionEvent.attempted_presentation) {
            attempted = true
            status = Verification2Session.VerificationSessionStatus.PROCESSING_FLOW
            presentedRawData = Verification2Session.PresentedRawData(vpTokenContents, receivedState)
        }

        // Process

        // queryId: from original dcql_query
        // presented credential/presentation


        PresentationVerificationEngine.executeAllVerification(vpTokenContents, session, updateSessionCallback, failSessionCallback)


        val optionalSuccessRedirectUrl = session.redirects?.successRedirectUri
        val willRedirect = optionalSuccessRedirectUrl != null

        return if (willRedirect) {
            mapOf("redirect_uri" to optionalSuccessRedirectUrl)

            // TODO: error redirect url !!!

        } else {
            mapOf(
                "status" to "received",
                "message" to "Presentation received and is being processed."
            )
        }
    }

    fun parseVpToken(vpTokenString: String): ParsedVpToken = try {
        Json.Default.decodeFromString(vpTokenString)
    } catch (e: Exception) {
        log.info { "Failed to parse vp_token string: $vpTokenString. Error: ${e.message}" }
        Verifier2Response.Verifier2Error.MALFORMED_VP_TOKEN.throwAsError()
    }


}
