@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy
import id.walt.holderpolicies.HolderPolicyEngine
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.waltid.openid4vp.wallet.presentation.LDPPresenter
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.presentation.SdJwtVcPresenter
import id.waltid.openid4vp.wallet.presentation.W3CPresenter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object WalletPresentFunctionality2 {

    private val log = KotlinLogging.logger { }

    private val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    /**
     * @param matchedData: Credentials that were choosen by the DCQL query
     */
    private suspend fun generateVpTokenForRequest(
        authorizationRequest: AuthorizationRequest,
        matchedData: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        /** For mdocs: this is the device key */
        holderKey: Key,
        holderDid: String?
    ): String {
        val vpTokenMapContents = mutableMapOf<String, JsonArray>()

        for ((queryId, matchedCredsWithClaimsList) in matchedData) {
            log.trace { "Query ID: $queryId, matched credentials: $matchedCredsWithClaimsList" }
            val presentationsForThisQueryId = buildJsonArray {
                for (matchResult in matchedCredsWithClaimsList) {
                    val digitalCredential = (matchResult.credential as RawDcqlCredential).originalCredential as DigitalCredential

                    val presentationStringOrObject: JsonElement = when (digitalCredential.format) {
                        "jwt_vc_json" -> W3CPresenter.presentW3C(
                            digitalCredential = digitalCredential,
                            matchResult = matchResult,
                            authorizationRequest = authorizationRequest,
                            holderKey = holderKey,
                            holderDid = holderDid ?: throw IllegalArgumentException("Missing DID for presentation")
                        )

                        "ldp_vc" -> LDPPresenter.presentLdpTodo()

                        "dc+sd-jwt" -> SdJwtVcPresenter.presentSdJwtVc(
                            digitalCredential = digitalCredential,
                            matchResult = matchResult,
                            authorizationRequest = authorizationRequest,
                            holderKey = holderKey,
                            holderDid = holderDid ?: throw IllegalArgumentException("Missing DID for presentation")
                        )

                        "mso_mdoc" -> {
                            MdocPresenter.presentMdoc(
                                digitalCredential = digitalCredential,
                                matchResult = matchResult,
                                authorizationRequest = authorizationRequest,
                                holderKey = holderKey
                            )
                        }

                        else ->
                            // Fallback for other formats or if it's a simple signed string
                            JsonPrimitive(
                                digitalCredential.signed
                                    ?: error("Credential for query $queryId is not signed and no specific presentation logic found for format ${digitalCredential.format}")
                            )
                    }
                    add(presentationStringOrObject)
                }
            }
            if (presentationsForThisQueryId.isNotEmpty()) {
                vpTokenMapContents[queryId] = presentationsForThisQueryId
            }
        }

        if (vpTokenMapContents.isEmpty() && authorizationRequest.dcqlQuery?.credentials?.isNotEmpty() == true) {
            // No credentials matched any part of a non-empty DCQL query.
            // Depending on policy, this might be an error or an empty vp_token.
            // OpenID4VP spec implies vp_token is only returned if something matches.
            // "A VP Token is only returned if the corresponding Authorization Request contained a dcql_query parameter..."
            // "...There MUST NOT be any entry in the JSON-encoded object for optional Credential Queries when there are no matching Credentials..."
            // So, if all queries were effectively optional and none matched, an empty vp_token is possible.
            // If required queries didn't match, DcqlMatcher should have failed earlier.
            log.warn { "No presentations generated for any query ID. Returning empty vp_token object." }
        }

        log.trace { "Generated VP Token Map Contents: $vpTokenMapContents" }
        return Json.encodeToString(JsonObject(vpTokenMapContents))
    }

    @Serializable
    data class WalletPresentResult(
        @SerialName("get_url")
        val getUrl: String? = null,

        @SerialName("form_post_html")
        val formPostHtml: String? = null,


        @SerialName("transmission_success")
        val transmissionSuccess: Boolean? = null,
        @SerialName("verifier_response")
        val verifierResponse: JsonElement? = null,

        @SerialName("redirect_to")
        val redirectTo: String? = null
    )

    suspend fun walletPresentHandling(
        holderKey: Key,
        holderDid: String?,
        presentationRequestUrl: Url,

        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,

        holderPoliciesToRun: Flow<HolderPolicy>?,
        runPolicies: Boolean?

        // TODO: selected credentials

    ): Result<WalletPresentResult> {
        log.trace { "- Start of Wallet Present Handling -" }

        log.trace { "Wallet presentation will use key $holderKey, and did $holderDid" }

        // Resolve AuthorizationRequest:
        val authorizationRequest: AuthorizationRequest = if (presentationRequestUrl.parameters.contains("request_uri")) {
            val requestUri = presentationRequestUrl.parameters["request_uri"]!!
            log.trace { "Resolving AuthorizationRequest from URI: $requestUri" }
            val httpResponse = http.get(requestUri)

            check(httpResponse.status.isSuccess()) { "AuthorizationRequest cannot be retrieved (${httpResponse.status}): from $requestUri - ${httpResponse.bodyAsText()}" }

            val authorizationRequestContentType =
                httpResponse.contentType()
                    ?: throw IllegalArgumentException("AuthorizationRequest does not have HTTP ContentType header set: $requestUri")
            log.trace { "Retrieved response has content type: $authorizationRequestContentType" }


            val retrievedAuthorizationRequest = when {
                authorizationRequestContentType.match("application/oauth-authz-req+jwt") -> {

                    val authReqJwt = httpResponse.bodyAsText()
                    require(authReqJwt.isJwt()) { "Response for AuthorizationRequest should be JWT, but is not a valid JWT" }
                    val authReqJws = authReqJwt.decodeJws()
                    val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.content
                    log.trace { "JWT AuthorizationRequest algorithm: $jwtAlg" }

                    if (jwtAlg.equals("none", true)) {
                        Json.decodeFromJsonElement<AuthorizationRequest>(authReqJws.payload)
                    } else {
                        val authReqBody = authReqJws.payload

                        val clientId = authReqBody["client_id"]?.jsonPrimitive?.contentOrNull
                        log.trace { "AuthorizationRequest is signed, authentication with client ID: $clientId" }
                        require(clientId != null) { "Missing client_id for signed AuthorizationRequest authentication" }

                        val clientIdPrefix = ClientIdPrefixParser.parse(clientId)
                            .getOrElse { e -> throw IllegalArgumentException("Could not parse client id prefix: $clientId", e) }
                        log.trace { "Parsed client id prefix: $clientIdPrefix" }

                        val clientMetadata = authReqBody["client_metadata"]?.let {
                            ClientMetadata.fromJson(it)
                                .getOrElse { e -> throw IllegalArgumentException("Could not parse client metadata: $it", e) }
                        }

                        val redirectUri = authReqBody["redirect_uri"]?.jsonPrimitive?.contentOrNull
                        val responseUri = authReqBody["response_uri"]?.jsonPrimitive?.contentOrNull

                        val context = RequestContext(
                            clientId = clientId,
                            clientMetadata = clientMetadata,
                            requestObjectJws = authReqJwt,
                            redirectUri = redirectUri,
                            responseUri = responseUri
                        )

                        val result = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)
                        when (result) {
                            is ClientValidationResult.Failure -> throw IllegalArgumentException("Could not verify signed AuthorizationRequest with client id prefix: ${result.error::class.simpleName} - ${result.error.message}")
                            is ClientValidationResult.Success -> Json {}.decodeFromJsonElement<AuthorizationRequest>(authReqJws.payload)
                        }
                    }


                    // 3. Verifying the JWT's signature The key for verification depends on the client_id prefix used by the Verifier (e.g., from DID document if client_id is a DID, from X.509 if x509_san_dns, from OpenID Federation metadata).

                    // 4. decode the JWT payload string into AuthorizationRequest data class
                }

                authorizationRequestContentType.match(ContentType.Application.Json) -> {
                    runCatching { httpResponse.body<AuthorizationRequest>() }.recover {
                        throw IllegalArgumentException("Error parsing AuthorizationRequest retrieved from: $presentationRequestUrl")
                    }.getOrThrow()
                }

                else -> throw IllegalArgumentException("Invalid ContentType \"$authorizationRequestContentType\" for AuthorizationRequest retrieved from \"$presentationRequestUrl\", content is: ${runCatching { httpResponse.bodyAsText() }.getOrElse { "(could not read http response body)" }}")
            }
            retrievedAuthorizationRequest
        } else {
            val parsedParameters = JsonObject(presentationRequestUrl.parameters.flattenEntries().associate { (k, v) ->
                k to Json.parseToJsonElement(v)
            })
            Json.decodeFromJsonElement<AuthorizationRequest>(parsedParameters)
        }

        log.trace { "Wallet will try to present to AuthorizationRequest: $authorizationRequest" }

        require(authorizationRequest.responseType == OpenID4VPResponseType.VP_TOKEN) {
            TODO("Currently only ResponseMode 'vp_token' is supported")
            // should also support "vp_token id_token"
        }

        // Build VP Token response
        val credentials = selectCredentialsForQuery(
            authorizationRequest.dcqlQuery ?: throw IllegalArgumentException("Missing dcql_query for AuthorizationRequest"),
        )
        log.trace { "Auto-selected credential count: ${credentials.mapValues { it.value.count() }}" }
        log.trace { "Auto-selected credentials for query: $credentials" }



        if (holderPoliciesToRun != null) {
            // TODO: ----------------- Handle disclosures from DcqlMatchResult

            val relevantHolderPolicies = holderPoliciesToRun
                .filter { it.direction == null || it.direction == HolderPolicy.HolderPolicyDirection.PRESENT }
            val credentialsToEvaluate = credentials.values.flatMap { matchResults ->
                matchResults.map { matchResult ->
                    // TODO: handle it.selectedDisclosures
                    (matchResult.credential as RawDcqlCredential).originalCredential as DigitalCredential
                }
            }

            val evalResult = HolderPolicyEngine.evaluate(relevantHolderPolicies, credentialsToEvaluate.asFlow())
            when {
                runPolicies == null && evalResult == null -> {
                    // ok
                }

                runPolicies == true -> {
                    if (evalResult == HolderPolicy.HolderPolicyAction.BLOCK) {
                        throw IllegalArgumentException("Presentation execution was blocked by Holder Policy.")
                    }
                    if (evalResult == null) {
                        throw IllegalArgumentException("Presentation execution was not allowed by any Holder Policy.")
                    }
                }
            }

            //-----
        }


        val vpToken = generateVpTokenForRequest(authorizationRequest, credentials, holderKey, holderDid)

        // Send AuthorizationResponse:
        if (authorizationRequest.responseMode == null) {
            require(authorizationRequest.responseType != null) { "Missing response_type" }
            val rt = authorizationRequest.responseType!!.responseType
            if ("vp_token" in rt && "code" !in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.FRAGMENT
            } else if ("code" in rt) {
                authorizationRequest.responseMode = OpenID4VPResponseMode.QUERY
            }
        }

        log.trace { "- Wallet will now present (send) AuthorizationResponse (with mode ${authorizationRequest.responseMode}) -" }

        when (authorizationRequest.responseMode) {
            OpenID4VPResponseMode.FRAGMENT -> {
                // Construct URL with #vp_token=...&state=... and trigger browser redirect

                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'fragment'."
                }

                // Build the parameters that will go into the URL fragment.
                val fragmentParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    authorizationRequest.state?.let { append("state", it) }
                }.build()

                // Create the final redirect URL.
                // e.g., https://verifier.com/callback#vp_token=...&state=...
                val redirectUrl = "${authorizationRequest.redirectUri}#${fragmentParameters.formUrlEncode()}"

                log.trace { "Responding with fragment redirect to: $redirectUrl" }

                // We return the URL for the client to handle the redirect.
                return Result.success(
                    WalletPresentResult(
                        getUrl = redirectUrl
                    )
                )
            }

            OpenID4VPResponseMode.QUERY -> {
                // This mode requires a redirect_uri to send the user back to.
                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'query'."
                }

                // Build the parameters that will go into the URL query string.
                val queryParameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)
                    authorizationRequest.state?.let { append("state", it) }
                }.build()

                // Create the final redirect URL.
                // Note the use of '?' instead of '#'.
                // e.g., https://verifier.com/callback?vp_token=...&state=...
                val redirectUrl = URLBuilder(authorizationRequest.redirectUri!!).apply {
                    // Ktor's URLBuilder handles adding '?' or '&' correctly,
                    // even if the original redirectUri already has query parameters.
                    parameters.appendAll(queryParameters)
                }.buildString()

                log.trace { "Responding with query redirect to: $redirectUrl" }

                // We return the URL for the client to handle the redirect.
                return Result.success(
                    WalletPresentResult(
                        getUrl = redirectUrl
                    )
                )
            }

            OpenID4VPResponseMode.FORM_POST -> {
                // This mode also requires a redirect_uri to POST the form to.
                require(authorizationRequest.redirectUri != null) {
                    "Invalid AuthorizationRequest: 'redirect_uri' is required for response_mode 'form_post'."
                }

                // Build an HTML page with a self-submitting form.
                val htmlContent = buildString {
                    appendLine("<!DOCTYPE html>")
                    appendLine("<html>")
                    appendLine("<head><title>Submitting Presentation...</title></head>")
                    // The body's onload attribute triggers the form submission automatically.
                    appendLine("<body onload=\"document.forms[0].submit()\">")
                    appendLine("<noscript><p>Your browser does not support JavaScript. Please press the button below to continue.</p></noscript>")
                    // The form action is the Verifier's redirect_uri.
                    appendLine("<form method=\"POST\" action=\"${authorizationRequest.redirectUri!!.encodeURLParameter()}\">")
                    // The vp_token and state are included as hidden input fields.
                    appendLine("<input type=\"hidden\" name=\"vp_token\" value=\"${vpToken.escapeHTML()}\"/>")
                    authorizationRequest.state?.let {
                        appendLine("<input type=\"hidden\" name=\"state\" value=\"${it.escapeHTML()}\"/>")
                    }
                    appendLine("<input type=\"submit\" value=\"Continue\"/>")
                    appendLine("</form>")
                    appendLine("</body>")
                    appendLine("</html>")
                }

                log.trace { "Responding with self-submitting HTML form to post to: ${authorizationRequest.redirectUri}" }

                // For a pure API, we return the HTML for the client to render in a WebView.
                return Result.success(
                    WalletPresentResult(
                        formPostHtml = htmlContent
                    )
                )
            }

            // authorizationRequest.responseUri
            OpenID4VPResponseMode.DIRECT_POST -> {
                require(authorizationRequest.responseUri != null) { "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post'." }
                val parameters = ParametersBuilder().apply {
                    append("vp_token", vpToken)

                    if (authorizationRequest.state != null) {
                        append("state", authorizationRequest.state!!)
                    }
                }.build()

                log.trace { "Submitting direct_post form to Verifier: ${authorizationRequest.responseUri}" }
                val response = http.submitForm(authorizationRequest.responseUri!!, parameters)
                log.trace { "Verifier direct_post response: $response" }

                val responseBody = response.bodyAsText()
                log.trace { "Verifier direct_post response body: $responseBody" }

                val responseBodyJson = runCatching { Json.decodeFromString<JsonObject>(responseBody) }

                return Result.success(
                    WalletPresentResult(
                        transmissionSuccess = response.status.isSuccess(),
                        verifierResponse = Json.parseToJsonElement(responseBody),
                        redirectTo = responseBodyJson.getOrThrow()["redirect_uri"]?.jsonPrimitive?.content
                    )
                )
            }

            OpenID4VPResponseMode.DIRECT_POST_JWT -> {
                // Encrypt the vp_token and state into a JWE, then POST response=<JWE_string>.
                require(authorizationRequest.responseUri != null) {
                    "Invalid AuthorizationRequest: 'response_uri' is required for response_mode 'direct_post.jwt'."
                }

                // 1. Get Encryption Metadata
                val clientMetadata = authorizationRequest.clientMetadata
                    ?: throw IllegalArgumentException("client_metadata is required for direct_post.jwt to obtain encryption keys")

                // 2. Select Verifier's Public Key
                // We prefer a key explicitly marked for encryption ('use': 'enc'), otherwise fall back to the first available key.
                val verifierJwkData = clientMetadata.jwks?.keys?.firstOrNull { it["use"]?.jsonPrimitive?.content == "enc" }
                    ?: clientMetadata.jwks?.keys?.firstOrNull()
                    ?: throw IllegalArgumentException("No suitable encryption key found in client_metadata jwks")

                // Import into JWKKey
                val verifierKey = JWKKey.importJWK(verifierJwkData.toString()).getOrThrow()

                // 3. Select Encryption Algorithm (enc)
                // Spec says default is A128GCM if not specified
                val encAlg = clientMetadata.encryptedResponseEncValuesSupported?.firstOrNull() ?: "A128GCM"

                // 4. Construct Payload
                // 'vpToken' is currently a JSON String. We parse it to JsonElement to embed it correctly in the JSON Object.
                val vpTokenElement = Json.parseToJsonElement(vpToken)

                val payloadJson = buildJsonObject {
                    put("vp_token", vpTokenElement)
                    authorizationRequest.state?.let { put("state", it) }
                }

                // 5. Encrypt
                // Uses the Verifier's public key to encrypt the payload
                val jweString = verifierKey.encryptJwe(payloadJson.toString().encodeToByteArray(), encAlg)

                // 6. Send Response
                // The body contains a single 'response' parameter with the JWE
                val parameters = ParametersBuilder().apply {
                    append("response", jweString)
                }.build()

                log.trace { "Submitting direct_post.jwt (encrypted) to Verifier: ${authorizationRequest.responseUri}" }
                val response = http.submitForm(authorizationRequest.responseUri!!, parameters)

                log.trace { "Verifier direct_post.jwt response status: ${response.status}" }

                // 7. Process Response (Same as direct_post)
                val responseBody = response.bodyAsText()
                val responseBodyJson = runCatching { Json.decodeFromString<JsonObject>(responseBody) }

                return Result.success(
                    WalletPresentResult(
                        transmissionSuccess = response.status.isSuccess(),
                        verifierResponse = Json.parseToJsonElement(responseBody),
                        redirectTo = responseBodyJson.getOrThrow()["redirect_uri"]?.jsonPrimitive?.content
                    )
                )

            }

            // DC API
            OpenID4VPResponseMode.DC_API -> TODO("DC API is not yet supported")
            OpenID4VPResponseMode.DC_API_JWT -> TODO("DC API is not yet supported")
            null -> throw IllegalArgumentException("Missing response mode from AuthorizationRequest")
        }
    }

    /**
     * Creates a Key Binding JWT for SD-JWT presentations.
     */
    internal suspend fun createKeyBindingJwt(
        disclosed: String,
        nonce: String,
        audience: String?,
        selectedDisclosures: List<SdJwtSelectiveDisclosure>,
        holderKey: Key
    ): String {
        selectedDisclosures.map { it.asEncoded() }
        log.trace { "Creating KB+JWT for disclosures: $selectedDisclosures" }
        // The spec for _sd_hash in KB-JWT sometimes implies hashing the concatenated disclosures
        // as they would appear in the final presentation string (with ~).
        // Let's assume for now Verifier will re-calculate based on the presented disclosures.
        // A common interpretation for `sd_hash` in KB-JWT is a hash of the digests from the `_sd` array
        // that correspond to the selectedDisclosures. This requires linking disclosures back to their original digests.

        // Simpler approach (hashing concatenated presented disclosures):
        // This binds the KB-JWT to the exact set and order of presented disclosures.
        val stringToHash = if (selectedDisclosures.isNotEmpty()) {
            "$disclosed~" //selectedDisclosures.joinToString(separator = "~") { it.asEncoded() }
        } else {
            disclosed // If there are no disclosures, what should sd_hash be?
            // Typically, a KB-JWT implies there are disclosures.
            // If it's possible to have a KB-JWT without disclosures (e.g. just binding to the core SD-JWT),
            // then the sd_hash might be calculated differently or be absent.
            // For now, let's assume selectedDisclosures is non-empty if we are creating a KB-JWT.
            // If selectedDisclosures can be empty, define how sd_hash is computed then?
            // Often, an empty string is hashed, or the field is omitted if allowed by profile.
        }

        log.trace { "Wallet presentation: Calculating hash for SD-JWT kb from: $stringToHash" }
        val sdHash = calculateSha256Base64Url(stringToHash)

        val jwsHeaders = buildJsonObject {
            //put("alg", JsonPrimitive(holderKey.algorithm)) // e.g., "ES256"
            put("typ", JsonPrimitive("kb+jwt"))
            // Add "kid" if holderKey has a key ID and it's useful for the verifier
            // holderKey.kid?.let { put("kid", JsonPrimitive(it)) }
        } // The header also needs to be base64url encoded as part of JWS construction

        val kbJwtPayload = buildJsonObject {
            put("aud", JsonPrimitive(audience))
            put("nonce", JsonPrimitive(nonce))
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
            // Add exp if needed
            put("sd_hash", JsonPrimitive(sdHash)) // binding to the selected disclosures
        }
        return holderKey.signJws(plaintext = kbJwtPayload.toString().encodeToByteArray(), headers = jwsHeaders)
    }

}
