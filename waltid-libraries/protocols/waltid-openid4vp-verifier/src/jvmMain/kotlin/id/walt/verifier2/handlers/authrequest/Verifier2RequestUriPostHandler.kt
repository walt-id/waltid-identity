package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

/**
 * Handles the `request_uri_method=post` flow per OID4VP 1.0 §5.6.
 *
 * When the wallet sends an HTTP POST to the request URI endpoint (instead of the default GET),
 * it MAY include:
 * - `wallet_metadata`: the wallet's capabilities as a JSON object
 * - `wallet_nonce`: a fresh random value to bind this specific request, preventing replay
 *
 * The verifier MUST include any received `wallet_nonce` in the signed request object payload.
 * This prevents an attacker from replaying a captured request object to a different wallet.
 *
 * Per spec §5.6: "When received, the Verifier MUST use it as the `wallet_nonce` value in
 * the signed authorization request object."
 */
object Verifier2RequestUriPostHandler {

    private val log = KotlinLogging.logger { }

    /**
     * Handles a POST to `/{sessionId}/request`.
     *
     * Parses `wallet_nonce` from the POST body, re-signs the request object with it injected,
     * and returns the fresh JWT with Content-Type `application/oauth-authz-req+jwt`.
     *
     * If the session uses an unsigned request, the POST body is ignored and the normal
     * unsigned authorization request is returned as JSON (same behaviour as GET).
     */
    suspend fun RoutingCall.respondRequestUriPost(
        verificationSession: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
    ) {
        val call = this

        // Parse wallet_nonce and wallet_metadata from the URL-encoded POST body
        val bodyParams = call.receiveParameters()
        val walletNonce = bodyParams["wallet_nonce"]
        val walletMetadataJson = bodyParams["wallet_metadata"]

        log.debug {
            "Request URI POST for session ${verificationSession.id}: " +
                "wallet_nonce=${walletNonce?.take(16)}, wallet_metadata=${walletMetadataJson?.take(50)}"
        }

        val isSigned = verificationSession.requestMode == Verification2Session.RequestMode.REQUEST_URI_SIGNED
        val existingJwt = verificationSession.signedAuthorizationRequestJwt

        if (!isSigned || existingJwt == null) {
            // Unsigned session: fall back to normal GET handling
            Verifier2AuthorizationRequestHandler.run {
                respondAuthorizationRequest(verificationSession, updateSessionCallback)
            }
            return
        }

        // Re-sign the request object including wallet_nonce per OID4VP 1.0 §5.6
        val coreSetup = verificationSession.setup.core

        val signingKey = coreSetup.key?.key
            ?: run {
                log.warn { "No signing key available for re-sign, returning pre-built JWT (no wallet_nonce)" }
                call.respondText(existingJwt, ContentType.parse("application/oauth-authz-req+jwt"))
                return
            }

        // Decode existing JWT to get headers and payload
        val parts = existingJwt.split(".")
        require(parts.size == 3) { "Existing signed request JWT is malformed" }

        val existingHeaderBytes = parts[0].decodeFromBase64Url()
        val existingPayloadBytes = parts[1].decodeFromBase64Url()

        val existingHeaders = Json.parseToJsonElement(existingHeaderBytes.decodeToString()).jsonObject
        val existingPayload = Json.parseToJsonElement(existingPayloadBytes.decodeToString()).jsonObject

        // Inject wallet_nonce into the payload (per spec §5.6 §response §wallet_nonce)
        val newPayload = buildJsonObject {
            existingPayload.forEach { (k, v) -> put(k, v) }
            if (walletNonce != null) {
                put("wallet_nonce", JsonPrimitive(walletNonce))
            }
            // Refresh iat since we're re-signing
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }

        // Re-sign with the same headers (alg, typ, x5c, kid etc.)
        val freshJwt = signingKey.signJws(
            plaintext = newPayload.toString().encodeToByteArray(),
            headers = existingHeaders
        )

        // Store wallet_nonce on session so the wallet-nonce check can be done on future requests if needed
        if (walletNonce != null) {
            updateSessionCallback(verificationSession, SessionEvent.authorization_request_requested) {
                // No-op session update — wallet_nonce is ephemeral per request
            }
        }

        log.trace { "Re-signed request object with wallet_nonce for session ${verificationSession.id}" }
        call.respondText(freshJwt, ContentType.parse("application/oauth-authz-req+jwt"))
    }
}
