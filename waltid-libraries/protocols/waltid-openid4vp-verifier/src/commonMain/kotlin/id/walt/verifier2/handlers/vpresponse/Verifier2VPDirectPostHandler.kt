@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.vpresponse

import id.walt.cose.coseCompliantCbor
import id.walt.dcql.DcqlCredential
import id.walt.dcql.models.TrustedAuthoritiesQuery
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.iso18013.annexc.AnnexCResponseVerifier
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.sha256
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.FAILED
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus.SUCCESSFUL
import id.walt.verifier2.data.Verifier2Response
import id.walt.verifier2.utils.JsonUtils.parseAsJsonObject
import id.walt.verifier2.verification2.PresentationVerificationEngine
import id.walt.verifier2.verification2.SelfIssuedIdTokenValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

object Verifier2VPDirectPostHandler {

    private val log = KotlinLogging.logger {}
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    /**
     * Optional callback for `trusted_authorities` verification per OID4VP §6.1.1.
     * Set this from JVM code to enable AKI-based trust chain checking, e.g.:
     *   `Verifier2VPDirectPostHandler.trustedAuthoritiesChecker = DcqlTrustedAuthoritiesChecker.checker`
     * Defaults to null — trusted_authorities constraints are not enforced unless set.
     */
    var trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null

    suspend fun parseResponseBody(
        responseMode: OpenID4VPResponseMode?,
        responseData: DirectPostResponse,
        session: Verification2Session,
        ephemeralDecryptionKey: DirectSerializedKey?,
        crypto2EphemeralDecryptionKey: String? = session.crypto2EphemeralDecryptionKey,
    ): Pair<String, String?> = when (responseData) {
        is ErrorResponseDirectPost -> error("Wallet error responses must be handled before parsing vp_token data")

        is DcApiJsonDirectPostResponse -> {
            val setup = session.setup
            if (setup is DcApiAnnexCFlowSetup) {
                // Annex C handling

                log.debug { "ANNEX C HANDLING: $responseData" }

                val encryptionInfoB64 = session.data?.jsonObject["data"]?.jsonObject["encryptionInfo"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing encryption info data")

                val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, setup.origin)
                val transcriptHashHex = hpkeInfo.sha256().toHexString()
                log.debug { "Transcript hash: $transcriptHashHex" }

                val encryptedResponseB64 = (responseData.jsonBody["response"]
                    ?: responseData.jsonBody["data"]?.jsonObject?.get("response"))?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Missing 'response' attribute in Annex C JSON response from wallet")
                val plaintext = decryptAnnexCResponse(
                    encryptedResponseB64 = encryptedResponseB64,
                    encryptionInfoB64 = encryptionInfoB64,
                    origin = setup.origin,
                    legacyKey = ephemeralDecryptionKey,
                    crypto2StoredKey = crypto2EphemeralDecryptionKey,
                )

                val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(plaintext)
                require(deviceResponse.version == "1.0") { "Unsupported DeviceResponse version: ${deviceResponse.version}" }
                require(deviceResponse.status == 0u) { "DeviceResponse reported failure status: ${deviceResponse.status}" }
                requireNotNull(deviceResponse.documents) { "Missing 'documents' in DeviceResponse!" }

                val virtualVpToken = deviceResponse.documents!!
                    .groupBy { it.docType }
                    .mapValues { (_, docs) -> docs.map { doc -> coseCompliantCbor.encodeToHexString(doc) } }

                //println("Device response: $deviceResponse")


                Json.encodeToString(virtualVpToken) to null
            } else {
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
                        session = session,
                        ephemeralDecryptionKey = ephemeralDecryptionKey,
                        crypto2EphemeralDecryptionKey = crypto2EphemeralDecryptionKey,
                    )
                    vpToken to state
                } else {
                    throw IllegalArgumentException("Missing any response content")
                }
            }

        }

        is EncryptedResponseStringDirectPostResponse -> {

            require(responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) {
                "Called encrypted flow, but responseMode is not for encrypted response"
            }

            log.trace { "Decrypting encrypted token..." }
            val decryptedPayloadString = decryptDirectPostJwe(
                responseData.responseParameter,
                ephemeralDecryptionKey,
                crypto2EphemeralDecryptionKey,
            ).decodeToString()
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

    internal suspend fun decryptAnnexCResponse(
        encryptedResponseB64: String,
        encryptionInfoB64: String,
        origin: String,
        legacyKey: DirectSerializedKey?,
        crypto2StoredKey: String?,
    ): ByteArray {
        val crypto2Key = crypto2StoredKey?.let { crypto2Runtime.restore(StoredKeyCodec.decodeFromString(it)) }
        return if (crypto2Key != null) {
            AnnexCResponseVerifier.decryptToDeviceResponse(
                encryptedResponseB64 = encryptedResponseB64,
                encryptionInfoB64 = encryptionInfoB64,
                origin = origin,
                recipientPrivateKey = crypto2Key,
            )
        } else {
            AnnexCResponseVerifier.decryptToDeviceResponse(
                encryptedResponseB64 = encryptedResponseB64,
                encryptionInfoB64 = encryptionInfoB64,
                origin = origin,
                recipientPrivateKey = legacyKey?.key as? JWKKey
                    ?: error("Missing ephemeral decryption key for Annex C"),
            )
        }
    }

    /**
     * Extracts the `id_token` from a [DirectPostResponse] for `vp_token id_token` flows.
     * Returns null if no `id_token` is present (e.g., plain `vp_token` flow).
     */
    suspend fun extractIdToken(
        responseData: DirectPostResponse,
        session: Verification2Session,
        ephemeralDecryptionKey: DirectSerializedKey?,
        crypto2EphemeralDecryptionKey: String? = session.crypto2EphemeralDecryptionKey,
    ): String? = when (responseData) {
        is CleartextDirectPostResponse -> responseData.idToken
        is EncryptedResponseStringDirectPostResponse -> {
            // id_token may also appear in the encrypted JWE payload
            try {
                val decryptedPayloadString = decryptDirectPostJwe(
                    responseData.responseParameter,
                    ephemeralDecryptionKey,
                    crypto2EphemeralDecryptionKey,
                ).decodeToString()
                Json.parseToJsonElement(decryptedPayloadString).jsonObject["id_token"]?.jsonPrimitive?.content
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                null
            }
        }
        else -> null
    }

    suspend fun RoutingCall.parseHttpRequestToDirectPostResponse(): DirectPostResponse {

        val providedContentType = this.request.contentType()

        return when {
            providedContentType.match(ContentType.Application.Json) -> {
                val bodyText = this.receiveText()
                log.trace { "Verification session data - body: $bodyText" }
                val bodyJsonObject = bodyText.parseAsJsonObject("Could not parse provided body text as JSON object for DC API flow")

                val errorCode = bodyJsonObject["error"]?.jsonPrimitive?.content
                if (errorCode != null) {
                    ErrorResponseDirectPost(
                        error = errorCode,
                        errorDescription = bodyJsonObject["error_description"]?.jsonPrimitive?.content,
                        state = bodyJsonObject["state"]?.jsonPrimitive?.content,
                    )
                } else {
                    DcApiJsonDirectPostResponse(bodyJsonObject)
                }
            }

            else -> {
                val urlParameters = this.receiveParameters()
                val responseString = urlParameters["response"]
                val vpTokenString = urlParameters["vp_token"]
                val idTokenString = urlParameters["id_token"]
                val receivedState = urlParameters["state"]
                val errorCode = urlParameters["error"]

                log.trace { "Verification session data: state = $receivedState, vp_token = $vpTokenString, id_token = ${idTokenString?.take(20)}, response = $responseString, error = $errorCode" }

                when {
                    errorCode != null -> ErrorResponseDirectPost(
                        error = errorCode,
                        errorDescription = urlParameters["error_description"],
                        state = receivedState,
                    )

                    responseString != null -> EncryptedResponseStringDirectPostResponse(
                        responseParameter = responseString
                    )

                    vpTokenString != null -> CleartextDirectPostResponse(
                        vpToken = vpTokenString,
                        state = receivedState ?: Verifier2Response.Verifier2Error.MISSING_STATE_PARAMETER.throwAsError(),
                        idToken = idTokenString,
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
        policyContext: PolicyExecutionContext = PolicyExecutionContext.Empty,
        beforeRespond: suspend (rejected: Boolean) -> Unit = {},
        ephemeralDecryptionKey: DirectSerializedKey? = verificationSession?.ephemeralDecryptionKey,
        crypto2EphemeralDecryptionKey: String? = verificationSession?.crypto2EphemeralDecryptionKey,
    ) {
        val call = this

        if (verificationSession == null) {
            Verifier2Response.Verifier2Error.UNKNOWN_VERIFICATION_SESSION.throwAsError()
        }

        verificationSession.expirationDate?.let { expirationDate ->
            if (expirationDate < Clock.System.now()) {
                Verifier2Response.Verifier2Error.EXPIRED_VERIFICATION_SESSION.throwAsError()
            }
        }

        try {
            val result = handleDirectPost(
                verificationSession = verificationSession,
                responseData = call.parseHttpRequestToDirectPostResponse(),
                updateSessionCallback = updateSessionCallback,
                failSessionCallback = failSessionCallback,
                policyContext = policyContext,
                ephemeralDecryptionKey = ephemeralDecryptionKey,
                crypto2EphemeralDecryptionKey = crypto2EphemeralDecryptionKey,
            )

            beforeRespond(false)
            call.respond(result)
        } catch (e: PresentationRejectionException) {
            // OID4VP 1.0 §8.2 / §response_mode_post: the verifier signals rejection with a 4xx
            // response so the wallet knows the presentation was not accepted.
            // Per §1298, the response body MAY also include a redirect_uri for the error page
            // so the wallet can redirect the user to an appropriate error page on the verifier's site.
            log.debug { "Presentation rejected, responding 400: ${e.message}" }
            val errorBody = buildMap {
                put("error", "invalid_request")
                put("error_description", e.message ?: "Presentation rejected")
                verificationSession.redirects?.errorRedirectUri?.let { put("redirect_uri", it.toString()) }
            }
            beforeRespond(true)
            call.respond(HttpStatusCode.BadRequest, errorBody)
        }
    }

    /** Thrown by [handleDirectPost] when the verifier rejects the presentation. */
    class PresentationRejectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
    data class CleartextDirectPostResponse(
        val vpToken: String,
        val state: String,
        /** Present when response_type=vp_token id_token (SIOPv2 combined flow) */
        val idToken: String? = null,
    ) : DirectPostResponse

    /**
     * OpenID4VP 1.0 §8.5 error response. Wallet rejects the presentation request (e.g. user
     * decline → `access_denied`). Body may arrive url-encoded or as JSON and always carries at
     * least an `error` code; `error_description` is optional. `state` is required whenever the
     * Authorization Request included `state` — [handleDirectPost] rejects mismatched / absent
     * state with `INVALID_STATE_PARAMETER` in that case.
     */
    data class ErrorResponseDirectPost(
        val error: String,
        val errorDescription: String?,
        val state: String?,
    ) : DirectPostResponse

    /**
     * Here the receiving of credentials through the Verifiers endpoints
     * (e.g. direct_post endpoint) is handled
     */
    suspend fun handleDirectPost(
        verificationSession: Verification2Session,
        responseData: DirectPostResponse,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        failSessionCallback: suspend (session: Verification2Session, event: SessionEvent, updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit) -> Unit,
        policyContext: PolicyExecutionContext = PolicyExecutionContext.Empty,
        ephemeralDecryptionKey: DirectSerializedKey? = verificationSession.ephemeralDecryptionKey,
        crypto2EphemeralDecryptionKey: String? = verificationSession.crypto2EphemeralDecryptionKey,
        verificationTime: Instant = Clock.System.now(),
    ): Map<String, String> {
        suspend fun Verification2Session.updateSession(event: SessionEvent, block: Verification2Session.() -> Unit) =
            updateSessionCallback.invoke(this, event, block)

        suspend fun Verification2Session.failSession(event: SessionEvent) =
            failSessionCallback.invoke(this, event, updateSessionCallback)

        log.debug { "Handling direct post for received data: $responseData" }

        val session = verificationSession
        val responseMode = session.authorizationRequest.responseMode
        val isAnnexC = verificationSession.setup is DcApiAnnexCFlowSetup

        if (responseData is ErrorResponseDirectPost) {
            return handleWalletErrorResponse(session, responseData, updateSessionCallback)
        }

        val (vpTokenString, receivedState) = parseResponseBody(
            responseMode = responseMode,
            responseData = responseData,
            session = session,
            ephemeralDecryptionKey = ephemeralDecryptionKey,
            crypto2EphemeralDecryptionKey = crypto2EphemeralDecryptionKey,
        )

        if (receivedState != session.authorizationRequest.state) {
            Verifier2Response.Verifier2Error.INVALID_STATE_PARAMETER.throwAsError()
        }

        // For vp_token id_token (SIOPv2 combined flow): validate the Self-Issued ID Token.
        // Per SIOPv2 §7 and OID4VP §"Combining this specification with SIOPv2".
        if (session.authorizationRequest.responseType == id.walt.verifier.openid.models.openid.OpenID4VPResponseType.VP_TOKEN_ID_TOKEN) {
            val idToken = extractIdToken(
                responseData,
                session,
                ephemeralDecryptionKey,
                crypto2EphemeralDecryptionKey,
            )
                ?: throw PresentationRejectionException("id_token is required for response_type=vp_token id_token but was absent")
            try {
                val allowedAlgorithms = session.authorizationRequest.clientMetadata
                    ?.idTokenSignedResponseAlg
                    ?.let(JwsAlgorithm::parse)
                    ?: JwsAlgorithm.RS256
                SelfIssuedIdTokenValidator.validate(
                    idToken = idToken,
                    expectedNonce = session.authorizationRequest.nonce!!,
                    expectedAudience = session.authorizationRequest.clientId!!,
                    allowedAlgorithms = setOf(allowedAlgorithms),
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                val reason = "Self-Issued ID Token validation failed: ${cause.message}"
                session.updateSession(SessionEvent.presentation_validation_available) {
                    failure = SessionFailure.PresentationValidation(reason, emptyMap())
                }
                session.failSession(SessionEvent.presentation_validation_failed)
                throw PresentationRejectionException(reason, cause)
            }
            log.debug { "Self-Issued ID Token validated successfully for session ${session.id}" }
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


        PresentationVerificationEngine.executeAllVerification(
            vpTokenContents,
            session,
            updateSessionCallback,
            failSessionCallback,
            policyContext,
            verificationTime,
            trustedAuthoritiesChecker
        )


        val optionalSuccessRedirectUrl = session.redirects?.successRedirectUri

        return if (optionalSuccessRedirectUrl != null) {
            // Per OID4VP 1.0 §1758 (session fixation prevention): append a fresh cryptographic
            // random response_code to the redirect URI. The wallet redirects the user browser to
            // this URI. The frontend then presents response_code + transaction-id to the Response
            // Endpoint to fetch the VP Token — only the browser that received the redirect can do this.
            val responseCode = generateResponseCode()

            // Store response_code on session so the frontend can validate it
            updateSessionCallback(session, SessionEvent.credential_policy_results_available) {
                this.responseCode = responseCode
            }

            val redirectUriWithCode = URLBuilder(optionalSuccessRedirectUrl)
                .apply { parameters.append("response_code", responseCode) }
                .buildString()

            log.trace { "Success redirect with response_code for session ${session.id}" }
            // Per OID4VP 1.0 §1298, the response body MUST contain redirect_uri when present.
            mapOf("redirect_uri" to redirectUriWithCode)
        } else {
            mapOf(
                "status" to "received",
                "message" to "Presentation received and is being processed."
            )
        }
    }

    internal suspend fun decryptDirectPostJwe(
        compactJwe: String,
        legacyKey: DirectSerializedKey?,
        crypto2StoredKey: String?,
    ): ByteArray {
        val crypto2Key = crypto2StoredKey?.let { crypto2Runtime.restore(StoredKeyCodec.decodeFromString(it)) }
        val expectedKeyId = crypto2Key?.id?.value
            ?: (legacyKey?.key as? JWKKey)?.getKeyId()
            ?: error("Missing decryption key for encrypted response flow")
        validateJweProtectedHeader(compactJwe, expectedKeyId)
        return if (crypto2Key != null) {
            CompactJwe.decrypt(
                compactJwe = compactJwe,
                recipientKey = crypto2Key,
                allowedContentEncryptions = setOf(JweContentEncryption.A128GCM, JweContentEncryption.A256GCM),
            ).plaintext
        } else {
            (legacyKey!!.key as JWKKey).decryptJwe(compactJwe)
        }
    }

    private fun validateJweProtectedHeader(compactJwe: String, expectedKeyId: String) {
        val encodedHeader = compactJwe.substringBefore('.', missingDelimiterValue = "")
        require(encodedHeader.isNotEmpty()) { "JWE protected header cannot be empty" }
        val header = Json.parseToJsonElement(
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(encodedHeader)
                .decodeToString(),
        ).jsonObject
        require(header["alg"]?.jsonPrimitive?.content == "ECDH-ES") { "Unsupported JWE key-management algorithm" }
        require(JweContentEncryption.parse(requireNotNull(header["enc"]?.jsonPrimitive?.content)) in setOf(
            JweContentEncryption.A128GCM,
            JweContentEncryption.A256GCM,
        )) { "JWE content-encryption algorithm is not allowed" }
        require(header["kid"]?.jsonPrimitive?.content == expectedKeyId) { "JWE kid does not match response decryption key" }
    }

    /**
     * Generates a fresh response_code per OID4VP 1.0 §1758.
     * Uses multiplatform UUID (backed by SecureRandom on JVM) for cryptographic randomness.
     */
        private fun generateResponseCode(): String = Uuid.random().toHexString()

    fun parseVpToken(vpTokenString: String): ParsedVpToken = try {
        Json.Default.decodeFromString(vpTokenString)
    } catch (e: Exception) {
        log.info { "Failed to parse vp_token string: $vpTokenString. Error: ${e.message}" }
        Verifier2Response.Verifier2Error.MALFORMED_VP_TOKEN.throwAsError()
    }

    /**
     * Handles an OID4VP 1.0 §8.5 wallet error response. Validates `state` echo, marks the
     * session as `FAILED` with a structured [SessionFailure.WalletErrorResponse], and emits
     * [SessionEvent.wallet_error_response_received].
     *
     * Idempotent: if the session is already in a terminal state, the response is acknowledged
     * without overwriting any existing outcome.
     */
    private suspend fun handleWalletErrorResponse(
        session: Verification2Session,
        responseData: ErrorResponseDirectPost,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
    ): Map<String, String> {
        log.info { "Wallet returned OID4VP §8.5 error for session ${session.id}: error=${responseData.error}" }

        session.authorizationRequest.state
            ?.takeIf { it != responseData.state }
            ?.let { Verifier2Response.Verifier2Error.INVALID_STATE_PARAMETER.throwAsError() }

        if (session.status == SUCCESSFUL || session.status == FAILED) {
            log.info { "Session ${session.id} already terminal (${session.status}); ignoring wallet error." }
            return mapOf(
                "status" to "acknowledged",
                "message" to "Session already terminal; wallet error response ignored.",
            )
        }

        updateSessionCallback(session, SessionEvent.wallet_error_response_received) {
            attempted = true
            status = FAILED
            statusReason = "Wallet returned OID4VP error: ${responseData.error}"
            failure = SessionFailure.WalletErrorResponse(
                reason = "Wallet returned OID4VP error response per §8.5",
                error = responseData.error,
                errorDescription = responseData.errorDescription,
                state = responseData.state,
            )
        }

        return session.redirects?.errorRedirectUri
            ?.let { mapOf("redirect_uri" to it.toString()) }
            ?: mapOf(
                "status" to "acknowledged",
                "message" to "Wallet error response recorded.",
            )
    }

}
