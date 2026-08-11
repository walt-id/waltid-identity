package id.walt.mobile.test.backend

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.MetadataSigner
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Test helper for the public walt.id issuer2/verifier2 demo stack.
 */
object DemoTestBackend {

    private const val ISSUER_BASE_URL = "https://issuer2.demo.walt.id"
    private const val ISSUER_IDENTIFIER = "$ISSUER_BASE_URL/openid4vci"
    // RFC 7638 thumbprint of the issuer2 metadata signing key published at
    // https://issuer2.demo.walt.id/openid4vci/jwks. This is an independent trust anchor;
    // it must not be learned from the signed metadata JWT itself.
    private const val ISSUER_METADATA_SIGNING_KEY_THUMBPRINT =
        "XVz5i-iLcVBvjz5X4LGc6dA-VFNSyzWMW32LAHF8fss"
    private const val VERIFIER_BASE_URL = "https://verifier2.demo.walt.id"

    const val TRANSACTION_DATA_PROFILES_URL = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
    private const val EUDI_PID_SD_JWT_VCT = "$ISSUER_BASE_URL/openid4vci/urn:eudi:pid:1"
    private const val PAYMENT_AUTHORIZATION_TYPE = "org.waltid.transaction-data.payment-authorization"

    /** EUDI TS-12 SCA payment, the type whose payload the Credential Manager matcher reads as a nested object. */
    const val SCA_PAYMENT_TYPE = "urn:eudi:sca:payment:1"
    const val SCA_PAYMENT_PAYEE_NAME = "Super Store"
    const val SCA_PAYMENT_CURRENCY = "EUR"
    const val SCA_PAYMENT_AMOUNT = 11.56
    const val SCA_PAYMENT_TRANSACTION_ID = "8D8AC610-566D-4EF0-9C22-186B2A5ED793"
    private val requiredPaymentAuthorizationFields = setOf("merchant_name", "amount", "currency")
    private val metadataCryptoRuntime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    val scenarios = listOf(
        CredentialScenario(
            id = "eudi-pid-sdjwt",
            displayName = "EUDI PID SD-JWT VC",
            profileId = "eudiPidSdJwt",
            credentialConfigurationId = "urn:eudi:pid:1",
            format = "dc+sd-jwt",
            verifierCredentialQuery = sdJwtQuery(
                id = "pid",
                vct = EUDI_PID_SD_JWT_VCT,
            ),
        ),
        CredentialScenario(
            id = "eudi-pid-mdoc",
            displayName = "EUDI PID mdoc",
            profileId = "eudiPidMdoc",
            credentialConfigurationId = "eu.europa.ec.eudi.pid.1",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "pid_mdoc",
                doctype = "eu.europa.ec.eudi.pid.1",
                namespace = "eu.europa.ec.eudi.pid.1",
                claims = listOf("given_name", "family_name"),
            ),
        ),
        CredentialScenario(
            id = "sca-payment-card",
            displayName = "SCA Payment Card",
            profileId = "scaPaymentCardMdoc",
            credentialConfigurationId = "sca_payment_card_mso_mdoc",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "sca_payment_card",
                doctype = "eu.europa.ec.eudi.sca.payment_card.1",
                namespace = "eu.europa.ec.eudi.sca.payment_card.1",
                claims = listOf("card_scheme", "card_last4", "card_holder_name"),
            ),
        ),
        CredentialScenario(
            id = "eu-age-verification",
            displayName = "EU Age Verification",
            profileId = "euAgeVerificationMdoc",
            credentialConfigurationId = "eu.europa.ec.av.1",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "proof_of_age",
                doctype = "eu.europa.ec.av.1",
                namespace = "eu.europa.ec.av.1",
                claims = listOf("age_over_18"),
            ),
        ),
        CredentialScenario(
            id = "iso-mdl",
            displayName = "ISO mDL",
            profileId = "isoMdl",
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            format = "mso_mdoc",
            verifierCredentialQuery = mdocQuery(
                id = "mdl",
                doctype = "org.iso.18013.5.1.mDL",
                namespace = "org.iso.18013.5.1",
                claims = listOf("given_name", "family_name"),
            ),
        ),
    )

    val presentationScenarios = scenarios

    val optionalBirthDatePresentationScenario = scenarios.first { it.id == "eudi-pid-sdjwt" }.copy(
        id = "eudi-pid-sdjwt-optional-birth-date",
        verifierCredentialQuery = sdJwtOptionalBirthDateQuery(
            id = "pid",
            vct = EUDI_PID_SD_JWT_VCT,
        ),
    )

    val transactionDataPresentationScenario = scenarios.first { it.id == "eudi-pid-sdjwt" }

    val persistenceScenario = scenarios.first { it.id == "eudi-pid-mdoc" }

    private val client by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
            }
        }
    }

    data class CredentialScenario(
        val id: String,
        val displayName: String,
        val profileId: String,
        val credentialConfigurationId: String,
        val format: String,
        val verifierCredentialQuery: JsonObject,
    )

    /**
     * @property offerId The issuer-side session id, so callers can poll `/issuer2/sessions/{id}`
     *   to confirm the issuer actually completed issuance rather than trusting a wallet-side ack.
     */
    data class GeneratedOffer(val offerUrl: String, val txCode: String?, val offerId: String)

    data class VerifierSession(val sessionId: String, val authorizationRequestUri: String)

    /**
     * A Digital Credentials API session. Unlike the cross-device flow there is no authorization
     * request URL: [requestJson] is the object the browser or OS hands to the wallet verbatim.
     */
    data class DcApiVerifierSession(val sessionId: String, val requestJson: String)

    /**
     * Creates a pre-authorized credential offer on the public demo issuer2.
     *
     * [inlineOffer] requests `valueMode=BY_VALUE`, so the returned URL carries a `credential_offer`
     * query parameter instead of a `credential_offer_uri`. The issuer defaults to `BY_REFERENCE`,
     * which is what the QR and deep link flows consume; DC API issuance needs the offer object itself
     * because it is passed to `navigator.credentials.create` / Credential Manager verbatim.
     */
    suspend fun createOffer(
        scenario: CredentialScenario,
        withGeneratedTransactionCode: Boolean = false,
        inlineOffer: Boolean = false,
    ): GeneratedOffer {
        val payload = buildJsonObject {
            put("profileId", scenario.profileId)
            put("authMethod", "PRE_AUTHORIZED")
            if (inlineOffer) put("valueMode", "BY_VALUE")
            if (withGeneratedTransactionCode) {
                putJsonObject("txCode") {
                    put("input_mode", "numeric")
                    put("length", 6)
                    put("description", "Enter the transaction code shown by the issuer")
                }
            }
        }

        val response = requestJson(
            url = "$ISSUER_BASE_URL/issuer2/credential-offers",
            body = payload,
        )
        val offerUrl = response["credentialOffer"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing credentialOffer in public demo issuer2 response: $response")
        val txCode = response["txCodeValue"]?.jsonPrimitive?.contentOrNull
            ?: response["txCode"]?.jsonPrimitive?.contentOrNull
        check(!withGeneratedTransactionCode || txCode != null) {
            "Public demo issuer2 did not return the requested transaction code: $response"
        }
        val offerId = response["offerId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing offerId in public demo issuer2 response: $response")

        return GeneratedOffer(
            offerUrl = offerUrl,
            txCode = txCode,
            offerId = offerId,
        )
    }

    /**
     * Lifecycle status of an offer session, e.g. `ACTIVE` or `SUCCESSFUL`.
     *
     * Deliberately narrow: this endpoint also returns the issuer signing key and the full credential
     * payload, so nothing but the status is lifted out of the response and it is never logged whole.
     */
    suspend fun issuerSessionStatus(offerId: String): String? {
        val response = client.get("$ISSUER_BASE_URL/issuer2/sessions/$offerId")
        check(response.status.isSuccess()) {
            "Public demo issuer2 session lookup failed: ${response.status}"
        }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["status"]?.jsonPrimitive?.contentOrNull
    }

    /**
     * Waits until issuer2 closes the offer session, i.e. the credential was actually issued.
     *
     * The DC API create flow gives the caller no trustworthy completion signal of its own: the
     * provider acknowledgment is built from constants, so asserting on it proves nothing about
     * issuance. This is the authoritative signal that the protocol ran to completion.
     */
    suspend fun waitForIssuerIssuanceSuccess(offerId: String, timeoutMs: Long = 60_000) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            val status = runCatching { issuerSessionStatus(offerId) }.getOrNull()
            when (status?.uppercase()) {
                "SUCCESSFUL" -> return
                "UNSUCCESSFUL", "REJECTED_BY_USER", "EXPIRED" ->
                    error("public demo issuer2 reported $status for offer session $offerId")
            }
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error(
                    "public demo issuer2 did not complete issuance within ${timeoutMs}ms for offer " +
                        "session $offerId (last status ${status ?: "<unavailable>"})"
                )
            }
            delay(2_000.milliseconds)
        }
    }

    suspend fun createVerifierSession(scenario: CredentialScenario): VerifierSession {
        return createVerifierSession(scenario.verifierCredentialQuery)
    }

    suspend fun createVerifierSession(
        scenario: CredentialScenario,
        signedRequest: Boolean,
    ): VerifierSession = createVerifierSession(
        credentialQuery = scenario.verifierCredentialQuery,
        transactionData = emptyList(),
        signedRequest = signedRequest,
    )

    /** Trust resolver for signed metadata served by the public issuer2 demo. */
    val publicDemoIssuerMetadataTrustResolver = CredentialIssuerMetadataTrustResolver { compactJwt, expectedCredentialIssuer ->
        require(expectedCredentialIssuer == ISSUER_IDENTIFIER) {
            "Unexpected public demo Credential Issuer: $expectedCredentialIssuer"
        }
        val decoded = CompactJws.decodeUnverified(compactJwt)
        val algorithm = decoded.protectedHeader[JwtHeaderParams.ALGORITHM]?.jsonPrimitive?.contentOrNull
            ?: error("Public demo signed metadata is missing alg")
        require(algorithm == "ES256") {
            "Unsupported public demo signed metadata algorithm: $algorithm"
        }
        val jwk = decoded.protectedHeader[JwtHeaderParams.JSON_WEB_KEY]?.jsonObject
            ?: error("Public demo signed metadata is missing jwk")
        require(jwk["kty"]?.jsonPrimitive?.contentOrNull == "EC") {
            "Public demo signed metadata jwk must use EC"
        }
        require(jwk["crv"]?.jsonPrimitive?.contentOrNull == "P-256") {
            "Public demo signed metadata jwk must use P-256"
        }
        val encodedVerificationKey = EncodedKey.Jwk(
            data = BinaryData(jwk.toString().encodeToByteArray()),
            privateMaterial = false,
        )
        require(Jwk.sha256Thumbprint(encodedVerificationKey) == ISSUER_METADATA_SIGNING_KEY_THUMBPRINT) {
            "Public demo signed metadata key is not the pinned issuer2 signing key"
        }
        val verificationKey = metadataCryptoRuntime.restore(
            encodedVerificationKey.toStoredSoftwareKey(KeyId("issuer2-metadata"), setOf(KeyUsage.VERIFY))
        )
        CompactJws.verify(compactJwt, verificationKey, JwsAlgorithm.ES256)
        MetadataSigner(
            keyId = decoded.protectedHeader[JwtHeaderParams.KEY_ID]?.jsonPrimitive?.contentOrNull,
            algorithm = algorithm,
            trustType = MetadataSignerTrustType.TRUSTED_ISSUER,
        )
    }

    suspend fun createResponseBoundVerifierSession(scenario: CredentialScenario): VerifierSession {
        return createVerifierSession(
            credentialQuery = scenario.verifierCredentialQuery,
            transactionData = emptyList(),
            bindClientIdToResponseUri = true,
        )
    }

    suspend fun createVerifierSession(credentialQuery: JsonObject): VerifierSession {
        return createVerifierSession(
            credentialQuery = credentialQuery,
            transactionData = emptyList(),
        )
    }

    suspend fun createTransactionDataVerifierSession(
        scenario: CredentialScenario = transactionDataPresentationScenario,
    ): VerifierSession = createVerifierSession(
        credentialQuery = scenario.verifierCredentialQuery,
        transactionData = listOf(paymentAuthorizationTransactionData("pid")),
    )

    /** One generic payment-authorization item using the current public-demo profile contract. */
    suspend fun paymentAuthorizationTransactionData(credentialId: String): JsonObject {
        val fields = transactionDataProfileFields(PAYMENT_AUTHORIZATION_TYPE)
        check(fields.containsAll(requiredPaymentAuthorizationFields)) {
            "Public demo transaction data profile '$PAYMENT_AUTHORIZATION_TYPE' is missing required fields: " +
                (requiredPaymentAuthorizationFields - fields).joinToString()
        }
        return buildPaymentAuthorizationTransactionData(credentialId)
    }

    internal fun buildPaymentAuthorizationTransactionData(credentialId: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(PAYMENT_AUTHORIZATION_TYPE))
        putJsonArray("credential_ids") {
            add(JsonPrimitive(credentialId))
        }
        put("require_cryptographic_holder_binding", JsonPrimitive(true))
        putJsonArray("transaction_data_hashes_alg") {
            add(JsonPrimitive("sha-256"))
        }
        put("merchant_name", JsonPrimitive("ACME Corp"))
        put("amount", JsonPrimitive("42.00"))
        put("currency", JsonPrimitive("EUR"))
    }

    /**
     * One `urn:eudi:sca:payment:1` item bound to [credentialId], shaped as the type requires: a nested
     * `payload`, not the flat fields the walt.id payment-authorization type uses.
     *
     * The values are those of the SCA demo request in `Verifier2OpenApiExamples`, so a test can assert
     * the same strings on the Credential Manager prompt and on the wallet's own review. `amount` is a
     * JSON number because the Credential Manager matcher reads it as one for this type and skips the
     * entry if it is a string.
     *
     * The profile is fetched rather than assumed so that a deployment which still declares the old
     * `payment_details` type fails here, instead of producing an item the matcher silently drops.
     */
    suspend fun scaPaymentTransactionData(credentialId: String): JsonObject {
        val fields = transactionDataProfileFields(SCA_PAYMENT_TYPE)
        check(fields.contains("payload")) {
            "Demo transaction data profile '$SCA_PAYMENT_TYPE' does not declare 'payload': $fields"
        }
        return buildJsonObject {
            put("type", JsonPrimitive(SCA_PAYMENT_TYPE))
            putJsonArray("credential_ids") {
                add(JsonPrimitive(credentialId))
            }
            put("require_cryptographic_holder_binding", JsonPrimitive(true))
            putJsonArray("transaction_data_hashes_alg") {
                add(JsonPrimitive("sha-256"))
            }
            putJsonObject("payload") {
                put("transaction_id", JsonPrimitive(SCA_PAYMENT_TRANSACTION_ID))
                putJsonObject("payee") {
                    put("name", JsonPrimitive(SCA_PAYMENT_PAYEE_NAME))
                    put("id", JsonPrimitive("merchant-001"))
                }
                put("currency", JsonPrimitive(SCA_PAYMENT_CURRENCY))
                put("amount", JsonPrimitive(SCA_PAYMENT_AMOUNT))
            }
        }
    }

    suspend fun transactionDataProfileFields(type: String): Set<String> {
        val response = client.get(TRANSACTION_DATA_PROFILES_URL) {
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from public demo transaction data profiles endpoint: $body")
        }
        return json.parseToJsonElement(body)
            .jsonArray
            .firstOrNull { profile -> profile.jsonObject["type"]?.jsonPrimitive?.content == type }
            ?.jsonObject
            ?.get("fields")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            ?: error("Missing public demo transaction data profile: $type")
    }

    private suspend fun createVerifierSession(
        credentialQuery: JsonObject,
        transactionData: List<JsonObject>,
        bindClientIdToResponseUri: Boolean = false,
        signedRequest: Boolean = false,
    ): VerifierSession {
        val requestedSessionId = Uuid.random().toString().takeIf { bindClientIdToResponseUri }
        val payload = buildJsonObject {
            put("flow_type", "cross_device")
            putJsonObject("core_flow") {
                if (signedRequest) {
                    put("signed_request", true)
                }
                requestedSessionId?.let { sessionId ->
                    val responseUri = "$VERIFIER_BASE_URL/verification-session/$sessionId/response"
                    put("sessionId", sessionId)
                    put("clientId", "redirect_uri:$responseUri")
                }
                putJsonObject("dcql_query") {
                    putJsonArray("credentials") {
                        add(credentialQuery)
                    }
                }
            }
            if (transactionData.isNotEmpty()) {
                putJsonObject("openid") {
                    putJsonArray("transactionData") {
                        transactionData.forEach { add(it) }
                    }
                }
            }
        }

        val response = requestJson(
            url = "$VERIFIER_BASE_URL/verification-session/create",
            body = payload,
        )
        val sessionId = response["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing sessionId in public demo verifier2 response: $response")
        check(requestedSessionId == null || requestedSessionId == sessionId) {
            "Public demo verifier2 did not preserve the requested session ID"
        }
        val authorizationRequestUri = response["bootstrapAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["authorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: response["fullAuthorizationRequestUrl"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing authorization request URL in public demo verifier2 response: $response")

        return VerifierSession(sessionId, authorizationRequestUri)
    }

    /**
     * Creates an Annex D (Digital Credentials API) session and fetches its request object.
     *
     * The verifier hashes the first [expectedOrigins] entry into the mdoc session transcript, and the
     * wallet hashes the origin the OS asserted for the calling app; a disagreement fails
     * `mso_mdoc/device-auth` with a signature error that never mentions the origin. Callers must pass the
     * origin the platform will actually report - for a native caller
     * `android:apk-key-hash:<base64url-sha256-of-signing-cert>`.
     */
    suspend fun createDcApiVerifierSession(
        scenario: CredentialScenario,
        expectedOrigins: List<String>,
    ): DcApiVerifierSession = createDcApiVerifierSession(
        credentialQueries = listOf(scenario.verifierCredentialQuery),
        expectedOrigins = expectedOrigins,
    )

    /**
     * As above, for the cases a single [CredentialScenario] cannot express.
     *
     * [encryptedResponse] switches the session to `response_mode=dc_api.jwt`, which also makes the
     * verifier derive the mdoc session transcript from its own encryption key's JWK thumbprint - so it
     * changes what a correct wallet response looks like, not just how it is wrapped.
     *
     * [transactionData] is passed through verbatim, so a test can assert on the exact fields the wallet
     * displayed and hashed; see [paymentAuthorizationTransactionData].
     */
    suspend fun createDcApiVerifierSession(
        credentialQueries: List<JsonObject>,
        expectedOrigins: List<String>,
        encryptedResponse: Boolean = false,
        transactionData: List<JsonObject> = emptyList(),
    ): DcApiVerifierSession {
        require(expectedOrigins.isNotEmpty()) { "DC API sessions require at least one expected origin" }
        require(credentialQueries.isNotEmpty()) { "DC API sessions require at least one DCQL credential query" }

        val payload = buildDcApiVerifierSessionPayload(
            credentialQueries = credentialQueries,
            expectedOrigins = expectedOrigins,
            encryptedResponse = encryptedResponse,
            transactionData = transactionData,
        )

        val response = requestJson(
            url = "$VERIFIER_BASE_URL/verification-session/create",
            body = payload,
        )
        val sessionId = response["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing sessionId in public demo verifier2 DC API response: $response")

        return DcApiVerifierSession(
            sessionId = sessionId,
            requestJson = dcApiRequestJson(sessionId),
        )
    }

    internal fun buildDcApiVerifierSessionPayload(
        credentialQueries: List<JsonObject>,
        expectedOrigins: List<String>,
        encryptedResponse: Boolean = false,
        transactionData: List<JsonObject> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("flow_type", "dc_api_openid4vp")
        putJsonObject("core_flow") {
            putJsonObject("dcql_query") {
                putJsonArray("credentials") {
                    credentialQueries.forEach { add(it) }
                }
            }
            if (encryptedResponse) put("encrypted_response", JsonPrimitive(true))
        }
        if (transactionData.isNotEmpty()) {
            putJsonObject("openid") {
                putJsonArray("transactionData") {
                    transactionData.forEach { add(it) }
                }
            }
        }
        // No vp_policies override: the verifier applies its full default mdoc policy set, so a policy
        // regression is visible here instead of silently skipped.
        putJsonArray("expectedOrigins") {
            expectedOrigins.forEach { add(JsonPrimitive(it)) }
        }
    }

    /**
     * The `digital` member of the verifier's request object: the argument a browser passes to
     * `navigator.credentials.get({ digital: ... })`, and what Credential Manager expects as its request
     * JSON.
     */
    private suspend fun dcApiRequestJson(sessionId: String): String {
        val response = client.get("$VERIFIER_BASE_URL/verification-session/$sessionId/request") {
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from verifier2 DC API request for $sessionId: $body")
        }
        val digital = json.parseToJsonElement(body).jsonObject["digital"]
            ?: error("Missing 'digital' member in public demo verifier2 DC API request object: $body")
        return digital.toString()
    }

    /** Posts a wallet's `{protocol, data}` DC API response to the verifier for real verification. */
    suspend fun submitDcApiResponse(sessionId: String, responseJson: String): String {
        val response = client.post("$VERIFIER_BASE_URL/verification-session/$sessionId/response") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(responseJson)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from verifier2 DC API response for $sessionId: $body")
        }
        return body
    }

    suspend fun verifierSessionInfo(sessionId: String): JsonObject {
        val response = client.get("$VERIFIER_BASE_URL/verification-session/$sessionId/info") {
            accept(ContentType.Application.Json)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from verifier2 session info for $sessionId: $body")
        }
        return json.parseToJsonElement(body).jsonObject
    }

    suspend fun waitForVerifierSuccess(sessionId: String, timeoutMs: Long = 90_000) {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("public demo verifier2 did not confirm presentation within ${timeoutMs}ms for session $sessionId")
            }

            val response = client.get("$VERIFIER_BASE_URL/verification-session/$sessionId/info") {
                accept(ContentType.Application.Json)
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess() && body.isNotBlank()) {
                val status = runCatching {
                    val json = json.parseToJsonElement(body).jsonObject
                    json["status"]?.jsonPrimitive?.contentOrNull
                        ?: json["session"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
                }.getOrNull()

                when (status?.uppercase()) {
                    "SUCCESSFUL" -> return
                    "FAILED", "ERROR", "EXPIRED" -> error("public demo verifier2 reported $status for session $sessionId: $body")
                }
            }

            delay(2_000.milliseconds)
        }
    }

    suspend fun waitForVerifierFailure(
        sessionId: String,
        expectedError: String,
        timeoutMs: Long = 90_000,
    ): JsonObject {
        val mark = TimeSource.Monotonic.markNow()
        while (true) {
            if (mark.elapsedNow() > timeoutMs.milliseconds) {
                error("public demo verifier2 did not report $expectedError within ${timeoutMs}ms for session $sessionId")
            }

            val info = verifierSessionInfo(sessionId)
            val status = info["status"]?.jsonPrimitive?.contentOrNull
                ?: info["session"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
            when (status?.uppercase()) {
                "FAILED" -> {
                    val failure = info["failure"]?.jsonObject
                        ?: error("public demo verifier2 omitted failure details for session $sessionId: $info")
                    val actualError = failure["error"]?.jsonPrimitive?.contentOrNull
                    check(actualError == expectedError) {
                        "public demo verifier2 reported $actualError instead of $expectedError for session $sessionId: $info"
                    }
                    return info
                }

                "SUCCESSFUL", "ERROR", "EXPIRED" ->
                    error("public demo verifier2 reported $status instead of $expectedError for session $sessionId: $info")
            }

            delay(2_000.milliseconds)
        }
    }

    private suspend fun requestJson(url: String, body: JsonObject): JsonObject {
        val response: HttpResponse = client.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            accept(ContentType.Application.Json)
            setBody(body.toString())
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("HTTP ${response.status.value} from $url: $responseBody")
        }
        return json.parseToJsonElement(responseBody).jsonObject
    }

    private fun sdJwtQuery(
        id: String,
        vct: String,
    ): JsonObject = credentialQuery(
        id = id,
        format = "dc+sd-jwt",
        meta = buildJsonObject {
            putJsonArray("vct_values") {
                add(JsonPrimitive(vct))
            }
        },
        // The public demo verifier accepts vct-only SD-JWT requests; claim-path
        // filtering here currently causes wallet presentation matching to miss.
        claimPaths = emptyList(),
    )

    private fun sdJwtOptionalBirthDateQuery(
        id: String,
        vct: String,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("format", JsonPrimitive("dc+sd-jwt"))
        putJsonObject("meta") {
            putJsonArray("vct_values") {
                add(JsonPrimitive(vct))
            }
        }
        putJsonArray("claims") {
            add(claimQuery(id = "given_name", path = listOf("given_name")))
            add(claimQuery(id = "family_name", path = listOf("family_name")))
            add(claimQuery(id = "birth_date", path = listOf("birth_date")))
        }
        putJsonArray("claim_sets") {
            add(claimSet("given_name", "family_name"))
            add(claimSet("given_name", "family_name", "birth_date"))
        }
    }

    private fun mdocQuery(
        id: String,
        doctype: String,
        namespace: String,
        claims: List<String>,
    ): JsonObject = credentialQuery(
        id = id,
        format = "mso_mdoc",
        meta = buildJsonObject {
            put("doctype_value", doctype)
        },
        claimPaths = claims.map { claim -> listOf(namespace, claim) },
    )

    private fun credentialQuery(
        id: String,
        format: String,
        meta: JsonObject,
        claimPaths: List<List<String>>,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("format", JsonPrimitive(format))
        put("meta", meta)
        if (claimPaths.isNotEmpty()) {
            putJsonArray("claims") {
                claimPaths.forEach { path ->
                    add(
                        buildJsonObject {
                            putJsonArray("path") {
                                path.forEach { add(JsonPrimitive(it)) }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun claimQuery(id: String, path: List<String>): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(id))
        putJsonArray("path") {
            path.forEach { add(JsonPrimitive(it)) }
        }
    }

    private fun claimSet(vararg claimIds: String) = kotlinx.serialization.json.buildJsonArray {
        claimIds.forEach { add(JsonPrimitive(it)) }
    }

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }
}
