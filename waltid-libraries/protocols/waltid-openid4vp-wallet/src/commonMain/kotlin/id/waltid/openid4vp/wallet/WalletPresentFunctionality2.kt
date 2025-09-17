package id.waltid.openid4vp.wallet

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseAlgorithm
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.ShaUtils.calculateSha256Base64Url
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy
import id.walt.holderpolicies.HolderPolicyEngine
import id.walt.isocred.DeviceAuth
import id.walt.isocred.DeviceAuthentication
import id.walt.isocred.DeviceNameSpaces
import id.walt.isocred.DeviceResponse
import id.walt.isocred.DeviceSigned
import id.walt.isocred.Document
import id.walt.isocred.SessionTranscript
import id.walt.isocred.handover.OpenID4VPHandover
import id.walt.isocred.handover.OpenID4VPHandoverInfo
import id.walt.isocred.sha256
import id.walt.isocred.wrapInCborTag
import id.walt.mdoc.utils.ByteStringWrapper
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.w3c.PresentationBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.escapeHTML
import io.ktor.util.flattenEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

object WalletPresentFunctionality2 {

    private val log = KotlinLogging.logger {  }

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
        holderDid: String
    ): String {
        val vpTokenMapContents = mutableMapOf<String, JsonArray>()

        for ((queryId, matchedCredsWithClaimsList) in matchedData) {
            log.trace { "Query ID: $queryId, matched credentials: $matchedCredsWithClaimsList" }
            val presentationsForThisQueryId = buildJsonArray {
                for (matchedResult in matchedCredsWithClaimsList) {
                    val digitalCredential = (matchedResult.credential as RawDcqlCredential).originalCredential as DigitalCredential
                    val selectedClaimsMap = matchedResult.selectedDisclosures // This is Map<String, Any>?

                    val presentationStringOrObject: JsonElement = when { // Assuming format is on DigitalCredential
                        digitalCredential.format == "jwt_vc_json" -> {
                            if (digitalCredential is SelectivelyDisclosableVerifiableCredential && digitalCredential.disclosables != null && digitalCredential.disclosables?.isNotEmpty() == true) {
                                // This W3C JWT VC uses SD-JWT mechanism internally
                                val disclosuresToPresent =
                                    selectedClaimsMap?.values?.mapNotNull {
                                        when (it) {
                                            is SdJwtSelectiveDisclosure -> it
                                            is DcqlDisclosure -> digitalCredential.disclosures?.find { sdJwtDisclosure -> sdJwtDisclosure.name == it.name }
                                            else -> null
                                        }
                                    } ?: emptyList()
                                log.debug { "Handling W3C JWT VC (${digitalCredential} with claims $disclosuresToPresent) with SD mechanism for query $queryId" }

                                // Construct the Key Binding JWT for SD-JWT mechanism
                                val kbJwtString = createKeyBindingJwt(
                                    nonce = authorizationRequest.nonce!!,
                                    audience = authorizationRequest.clientId,
                                    selectedDisclosures = disclosuresToPresent, // Pass the actual disclosures for sd_hash
                                    holderKey = holderKey
                                )
                                // Use the disclose method, appending the KB-JWT
                                val sdPresentationString =
                                    digitalCredential.disclose(digitalCredential, disclosuresToPresent) + "~" + kbJwtString
                                JsonPrimitive(sdPresentationString)
                            } else {
                                // Standard W3C VP JWT wrapping this non-SD W3C VC JWT
                                log.debug { "Handling standard W3C JWT VC (${digitalCredential}) for query $queryId" }
                                val w3cPresentationJwt = PresentationBuilder().apply {
                                    this.did = holderDid
                                    this.nonce = authorizationRequest.nonce!!
                                    this.audience = authorizationRequest.clientId
                                    addCredential(
                                        JsonPrimitive(
                                            digitalCredential.signed ?: error("Signed W3C VC JWT missing for $digitalCredential")
                                        )
                                    )
                                    //addVerifiableCredentialJwt()
                                }.buildAndSign(holderKey)
                                JsonPrimitive(w3cPresentationJwt)
                            }
                        }

                        digitalCredential.format == "ldp_vc" -> {
                            TODO("Data Integrity Proof signed credentials are not supported yet")
                            // Construct a W3C VP LDP (JSON-LD with Data Integrity proof)
                            // This is more complex as it involves JSON-LD processing and DI proofs.
                            // The 'originalCredential.credentialData' would be the LDP VC.
                            // The proof needs 'challenge' (nonce) and 'domain' (client_id).
                            /*val ldpPresentationObject = buildLdpPresentation(
                                originalCredential.credentialData,
                                holderDid,
                                authorizationRequest.nonce!!,
                                authorizationRequest.clientId,
                                holderKey
                            )
                            ldpPresentationObject*/ // This would be a JsonObject
                        }

                        digitalCredential.format == "dc+sd-jwt" -> {
                            val sdJwtCredential = digitalCredential as? SelectivelyDisclosableVerifiableCredential
                                ?: error("Mismatch: Expected SelectivelyDisclosableVerifiableCredential for DC_SD_JWT format for ${digitalCredential}")

                            log.trace { "Selected claims: ${selectedClaimsMap?.values?.map { it.toString() + " (${it::class.simpleName})" }}" }
                            val disclosuresToPresent =
                                selectedClaimsMap?.values?.mapNotNull {
                                    when (it) {
                                        is SdJwtSelectiveDisclosure -> it
                                        is DcqlDisclosure -> {
                                            sdJwtCredential.disclosures?.find { sdJwtDisclosure -> sdJwtDisclosure.name == it.name }
                                        }

                                        else -> null
                                    }
                                } ?: emptyList()

                            log.debug { "Handling IETF SD-JWT VC (${digitalCredential} with disclosures $disclosuresToPresent) for query $queryId" }

                            // Construct the Key Binding JWT
                            val kbJwtString = createKeyBindingJwt(
                                nonce = authorizationRequest.nonce!!,
                                audience = authorizationRequest.clientId,
                                selectedDisclosures = disclosuresToPresent,
                                holderKey = holderKey
                            )

                            // Use the disclose method from the interface, then append the KB-JWT
                            val finalPresentationString =
                                sdJwtCredential.disclose(digitalCredential, disclosuresToPresent) + "~" + kbJwtString
                            log.trace { "Final presentation string for dc+sd-jwt is: $finalPresentationString" }
                            JsonPrimitive(finalPresentationString)
                        }

                        digitalCredential.format == "mso_mdoc" -> {
                            // Construct DeviceResponse CBOR, then base64url encode it.
                            // This needs access to mdoc specific data and device keys.
                            // The 'selectedClaimsMap' would guide which data elements to include.
                            /*val mdocPresentationString = buildMdocDeviceResponse(
                                digitalCredential, // Contains the mdoc data
                                selectedClaimsMap, // Guides which elements to include
                                authorizationRequest.nonce!!,
                                authorizationRequest.clientId, // Or derived elements for SessionTranscript
                                holderKey // Or specific device key for mdoc
                            )
                            JsonPrimitive(mdocPresentationString)*/

                            log.debug { "Handling mso_mdoc credential for query $queryId" }

                            val mdocsCredential = digitalCredential as MdocsCredential
                            val responseUri = authorizationRequest.responseUri
                                ?: throw IllegalArgumentException("response_uri is required for mso_mdoc presentation")

                            val document = mdocsCredential.parseToDocument()
                            val issuerSigned = document.issuerSigned

                            // 1. Build OpenID4VPHandover (OID4VP Appendix B.2.6.1) without ISO-specific wallet nonce
                            val handoverInfo = OpenID4VPHandoverInfo(
                                clientId = authorizationRequest.clientId,
                                nonce = authorizationRequest.nonce!!,
                                jwkThumbprint = null, // Not using JWE in DIRECT_POST
                                responseUri = responseUri
                            )
                            val handoverInfoHash = coseCompliantCbor.encodeToByteArray(handoverInfo).sha256()
                            val handover = OpenID4VPHandover(infoHash = handoverInfoHash)
                            val sessionTranscript = SessionTranscript.forOpenId(handover)

                            // 2. Determine which namespaces and elements to disclose based on the DCQL match
                            //                            // This is a simplified example; a full implementation would iterate through selectedClaimsMap
                            val disclosedDeviceNamespaces = DeviceNameSpaces(emptyMap()) // Assuming selective disclosure for device data

                            // 3. Create the DeviceAuthentication structure to be signed
                            val deviceAuthentication = DeviceAuthentication(
                                type = "DeviceAuthentication",
                                sessionTranscript = sessionTranscript,
                                docType = mdocsCredential.docType,
                                namespaces = ByteStringWrapper(disclosedDeviceNamespaces)
                            )

                            // 4. Sign using the new detached payload function
                            val detachedPayload = coseCompliantCbor.encodeToByteArray(deviceAuthentication).wrapInCborTag(24)
                            val deviceSignature = CoseSign1.createAndSignDetached(
                                protectedHeaders = CoseHeaders(algorithm = holderKey.keyType.toCoseAlgorithm()),
                                detachedPayload = detachedPayload,
                                signer = holderKey.toCoseSigner()
                            )
                            val deviceAuth = DeviceAuth(deviceSignature = deviceSignature)


                            //--- SD START
                            issuerSigned

                            //--- SD END

                            // 5. Assemble the final DeviceResponse
                            val deviceResponse = DeviceResponse(
                                version = "1.0",
                                documents = arrayOf(
                                    Document(
                                        docType = mdocsCredential.docType,
                                        issuerSigned = issuerSigned,
                                        deviceSigned = DeviceSigned(ByteStringWrapper(disclosedDeviceNamespaces), deviceAuth)
                                    )
                                ),
                                status = 0u
                            )

                            // 6. CBOR-encode and base64url-encode the response string
                            val deviceResponseBytes = coseCompliantCbor.encodeToByteArray(deviceResponse)
                            JsonPrimitive(deviceResponseBytes.encodeToBase64Url())
                        }

                        else -> {
                            // Fallback for other formats or if it's a simple signed string
                            JsonPrimitive(
                                digitalCredential.signed
                                    ?: error("Credential for query $queryId is not signed and no specific presentation logic found for format ${digitalCredential.format}")
                            )
                        }
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
            log.warn("No presentations generated for any query ID. Returning empty vp_token object.")
        }

        log.trace { "Generated VP Token Map Contents: $vpTokenMapContents" }
        return Json.encodeToString(JsonObject(vpTokenMapContents))
    }

    suspend fun walletPresentHandling(
        holderKey: Key,
        holderDid: String,
        presentationRequestUrl: Url,

        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,

        holderPoliciesToRun: Flow<HolderPolicy>?,
        runPolicies: Boolean?,

        // TODO: selected credentials

        /**
         *  TEMPORARY: Fallback for application/oauth-authz-req+jwt
         *  Use: `OldWalletPresentFunctionality.oldWalletPresentHandling(walletService, presentationRequestUrl, request)` for this
         */
        temporaryFallbackCallback: (suspend (Url) -> Result<JsonElement>)? = null
    ): Result<JsonElement> {
        log.trace { "- Start of Wallet Present Handling -" }

        log.trace { "Wallet presentation will use key $holderKey, and did $holderDid" }

        // Resolve AuthorizationRequest:
        val authorizationRequest: AuthorizationRequest = if (presentationRequestUrl.parameters.contains("request_uri")) {
            val requestUri = presentationRequestUrl.parameters["request_uri"]!!
            val httpResponse = http.get(requestUri)

            check(httpResponse.status.isSuccess()) { "AuthorizationRequest cannot be retrieved (${httpResponse.status}): from $requestUri" }

            val authorizationRequestContentType =
                httpResponse.contentType()
                    ?: throw IllegalArgumentException("AuthorizationRequest does not have HTTP ContentType header set: $requestUri")

            when {
                authorizationRequestContentType.match("application/oauth-authz-req+jwt") -> {
                    // Fallback for E2E test
                    if (temporaryFallbackCallback != null) {
                        return temporaryFallbackCallback(presentationRequestUrl)
                    }
                    TODO("Handle signed AuthorizationRequest (JWT)")

                    // 1. Fetching the JWT string from
                    // httpResponse.bodyAsText().

                    // 2. Parse the JWT

                    // 3. Verifying the JWT's signature The key for verification depends on the client_id prefix used by the Verifier (e.g., from DID document if client_id is a DID, from X.509 if x509_san_dns, from OpenID Federation metadata).

                    // 4. decode the JWT payload string into AuthorizationRequest data class
                }

                authorizationRequestContentType.match(ContentType.Application.Json) -> {
                    runCatching { httpResponse.body<AuthorizationRequest>() }.recover {
                        throw IllegalArgumentException("Error parsing AuthorizationRequest retrieved from: $presentationRequestUrl")
                    }.getOrThrow()
                }

                else -> throw IllegalArgumentException("Invalid ContentType \"$authorizationRequestContentType\" for AuthorizationRequest retrieved from: $presentationRequestUrl")
            }
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
                val credentialsToEvaluate = credentials.values.flatMap {
                    it.map {
                        // TODO: handle it.selectedDisclosures
                        (it.credential as RawDcqlCredential).originalCredential as DigitalCredential
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
                return Result.success(buildJsonObject {
                    put("get_url", JsonPrimitive(redirectUrl))
                })
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
                return Result.success(buildJsonObject {
                    put("get_url", JsonPrimitive(redirectUrl))
                })
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
                return Result.success(buildJsonObject {
                    put("form_post_html", JsonPrimitive(htmlContent))
                })
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

                val responseBody = response.bodyAsText()

                val responseBodyJson = runCatching { Json.decodeFromString<JsonObject>(responseBody) }

                return Result.success(buildJsonObject {
                    put("transmission_success", JsonPrimitive(response.status.isSuccess()))
                    put(
                        "verifier_response", Json.parseToJsonElement(responseBody)
                    )
                    if (responseBodyJson.getOrNull()?.containsKey("redirect_uri") == true) {
                        put("redirect_to", responseBodyJson.getOrThrow()["redirect_uri"] ?: JsonNull)
                    }
                })
            }

            OpenID4VPResponseMode.DIRECT_POST_JWT -> {
                // Encrypt the vp_token and state into a JWE, then POST response=<JWE_string>.
                TODO()
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
    private suspend fun createKeyBindingJwt(
        nonce: String,
        audience: String,
        selectedDisclosures: List<SdJwtSelectiveDisclosure>,
        holderKey: Key
    ): String {
        val disclosureStringsForHash = selectedDisclosures.map { it.asEncoded() }
        log.trace { "Creating KB+JWT for disclosures: $selectedDisclosures" }
        // The spec for _sd_hash in KB-JWT sometimes implies hashing the concatenated disclosures
        // as they would appear in the final presentation string (with ~).
        // Let's assume for now Verifier will re-calculate based on the presented disclosures.
        // A common interpretation for `sd_hash` in KB-JWT is a hash of the digests from the `_sd` array
        // that correspond to the selectedDisclosures. This requires linking disclosures back to their original digests.

        // Simpler approach (hashing concatenated presented disclosures):
        // This binds the KB-JWT to the exact set and order of presented disclosures.
        val stringToHash = if (selectedDisclosures.isNotEmpty()) {
            selectedDisclosures.joinToString(separator = "~") { it.asEncoded() }
        } else {
            "" // If there are no disclosures, what should sd_hash be?
            // Typically, a KB-JWT implies there are disclosures.
            // If it's possible to have a KB-JWT without disclosures (e.g. just binding to the core SD-JWT),
            // then the sd_hash might be calculated differently or be absent.
            // For now, let's assume selectedDisclosures is non-empty if we are creating a KB-JWT.
            // If selectedDisclosures can be empty, define how sd_hash is computed then?
            // Often, an empty string is hashed, or the field is omitted if allowed by profile.
        }
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
