package id.walt.openid4vci

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.defaultJwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.handlers.credential.SdJwtVcCredentialHandler
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInputProvider
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenIssuer
import id.walt.sdjwt.SDJwt
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ProviderCredentialIssuanceTest {
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `unsupported credential handler is rejected`() = runBlocking {
        val provider = buildOAuth2Provider(createTestConfig())
        val requestResult = provider.createCredentialRequest(
            parameters = mapOf("credential_configuration_id" to listOf("unsupported-credential")),
            session = DefaultSession(subject = "demo-subject"),
        )
        assertTrue(requestResult is CredentialRequestResult.Success)

        val responseResult = provider.createCredentialResponse(
            request = requestResult.request,
            configuration = CredentialConfiguration(format = CredentialFormat.LDP_VC),
            issuerKey = JWKKey.generate(KeyType.Ed25519),
            issuerId = "did:example:issuer",
            issuanceInputData = issuanceInputs(
                buildJsonObject { put("given_name", "Alice") },
            ),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.UNKNOWN_CREDENTIAL_CONFIGURATION, responseResult.error.error)
    }

    @Test
    fun `authorization flow issues signed sd-jwt credential`() = runBlocking {
        val credentialId = "test-credential"
        val issuerId = "did:example:issuer"
        val accessTokenKey = KeyManager.resolveSerializedKey(
            KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1))
        )
        val accessTokenIssuer = JwtAccessTokenIssuer(resolver = { accessTokenKey })
        val config = createTestConfig(accessTokenIssuer = accessTokenIssuer)

        val provider = buildOAuth2Provider(config)

        // Issuer creates an IssuerState, add a validator and then
        // creates credential offer and shares it with the wallet.
        val offer = CredentialOffer.withAuthorizationCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf(credentialId),
            issuerState = "issuer-state-123",
        )

        // Issuer builds an offer URL and shares it (QR code, link, etc.).
        val offerRequest = CredentialOfferRequest(credentialOffer = offer)
        val offerUrlString = offerRequest.toUrl()

        // Wallet reads the URL, extracts the offer, and reads issuer_state.
        val params = Url(offerUrlString).parameters.toMap()
        val offerPayload = params["credential_offer"]?.firstOrNull().orEmpty()
        val json = Json { encodeDefaults = false; explicitNulls = false }
        val decodedOffer = json.decodeFromString(CredentialOffer.serializer(), offerPayload)
        val offerIssuerState = decodedOffer.grants?.authorizationCode?.issuerState

        // Wallet sends an authorization request.
        val authorizationRequestWallet = buildMap {
            put("response_type", listOf("code"))
            put("client_id", listOf("demo-client"))
            put("redirect_uri", listOf("https://openid4vci.walt.id/callback"))
            put("scope", listOf("openid credential"))
            put("state", listOf("authorization-request-state"))
            offerIssuerState?.let { put("issuer_state", listOf(it)) }
        }

        // Issuer api authenticates the user and call the following:
        val authorizeResult = provider.createAuthorizationRequest(
            authorizationRequestWallet
        )

        assertTrue(authorizeResult is AuthorizationRequestResult.Success)
        val authorizeRequest = authorizeResult.request.withIssuer(issuerId)

        val session = DefaultSession(subject = "demo-subject")

        // Issuer API calls the following to issues an authorization response (code).
        val authorizeResponse = provider.createAuthorizationResponse(authorizeRequest, session)
        assertTrue(authorizeResponse is AuthorizationResponseResult.Success)
        val code = authorizeResponse.response.code

        // Issuer API provide the code to wallet via redirect
        // Wallet exchanges the code for an access token
        val accessTokenRequestWallet = mapOf(
            "grant_type" to listOf(GrantType.AuthorizationCode.value),
            "code" to listOf(code),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "client_id" to listOf("demo-client"),
        )

        // Issuer API gets the request and call the following:
        val accessRequestResult = provider.createAccessTokenRequest(
            accessTokenRequestWallet
        )

        assertTrue(accessRequestResult is AccessTokenRequestResult.Success)
        val accessRequest = accessRequestResult.request.withIssuer(issuerId)

        // Issuer API returns the access token response.
        val accessResponse = provider.createAccessTokenResponse(accessRequest)
        assertTrue(accessResponse is AccessTokenResponseResult.Success)
        assertTrue(accessResponse.response.accessToken.isNotBlank())

        // Wallet constructs a proof JWT to bind the credential to its key.
        val holderKey = JWKKey.generate(KeyType.Ed25519)
        DidService.minimalInit()
        val holderDid = DidService.registerByKey("key", holderKey).did
        val holderKid = "$holderDid#${holderDid.removePrefix("did:key:")}"
        assertFailsWith<NoSuchElementException> {
            Crypto2JwtKeyResolver().resolveFromDid(holderDid, "$holderDid#unknown")
        }
        val proofPayload = buildJsonObject {
            put("aud", issuerId)
            put("nonce", "nonce")
        }
        val proofJwt = holderKey.signJws(
            plaintext = proofPayload.toString().toByteArray(),
            headers = mapOf("kid" to JsonPrimitive(holderKid)),
        )
        val proofParam = buildJsonObject {
            put("jwt", JsonArray(listOf(JsonPrimitive(proofJwt))))
        }.toString()

        // Wallet submits a credential request; issuer parses and validates it.
        val credentialRequestResult = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf(credentialId),
                "proofs" to listOf(proofParam),
            ),
            session = session,
        )
        assertTrue(credentialRequestResult is CredentialRequestResult.Success)
        val credentialRequest = credentialRequestResult.request.withIssuer(issuerId)

        // Issuer signs and returns the SD-JWT credential.
        val issuerKey = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("credential-issuer"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val credentialData = buildJsonObject {
            put("given_name", "Alice")
            put("family_name", "Doe")
        }

        val credentialConfigurations = mapOf(
            credentialId to CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                vct = credentialId,
            )
        )
        val issuerMetadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = credentialConfigurations,
        )
        val configuration = issuerMetadata.getCredentialConfiguration(credentialId)
            ?: error("Missing credential configuration for $credentialId")
        val credentialResponse = provider.createCredentialResponse(
            request = credentialRequest,
            configuration = configuration,
            issuerKey = Crypto2CredentialSigningKey.select(issuerKey, configuration),
            issuerId = issuerId,
            issuanceInputData = issuanceInputs(credentialData),
        )

        assertTrue(credentialResponse is CredentialResponseResult.Success)
        val response = credentialResponse.response
        assertNotNull(response.credentials)
        assertEquals(1, response.credentials.size)

        // Wallet verifies the SD-JWT signature using the issuer public key.
        val credential = response.credentials.first().credential.jsonPrimitive.content
        assertTrue(credential.isNotBlank())

        val jwtPart = credential.substringBefore("~")
        assertTrue(CompactJws.verify(jwtPart, issuerKey, JwsAlgorithm.ES256).payload.isNotEmpty())
    }

    @Test
    fun `credential signing key selects compatible configured algorithm`() = runBlocking {
        val key = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("algorithm-selection"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val mdocConfiguration = CredentialConfiguration(
            format = CredentialFormat.MSO_MDOC,
            doctype = "org.iso.18013.5.1.mDL",
            credentialSigningAlgValuesSupported = linkedSetOf(
                SigningAlgId.CoseValue(-9),
                SigningAlgId.CoseValue(-7),
            ),
        )

        assertEquals(
            SigningAlgId.CoseValue(-7),
            Crypto2CredentialSigningKey.select(key, mdocConfiguration).algorithm,
        )
        assertEquals(JwsAlgorithm.RS384, KeySpec.Rsa(3072).defaultJwsAlgorithm())
        assertFailsWith<IllegalArgumentException> {
            Crypto2CredentialSigningKey.select(
                key,
                CredentialConfiguration(
                    format = CredentialFormat.SD_JWT_VC,
                    vct = "ExampleCredential",
                    credentialSigningAlgValuesSupported = setOf(SigningAlgId.Jose("EdDSA")),
                ),
            )
        }
        val p384Key = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("incompatible-mdoc-key"),
                spec = KeySpec.Ec(EcCurve.P384),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        assertFailsWith<IllegalArgumentException> {
            Crypto2CredentialSigningKey.select(
                p384Key,
                mdocConfiguration.copy(
                    credentialSigningAlgValuesSupported = setOf(SigningAlgId.CoseValue(-35)),
                ),
            )
        }
    }

    @Test
    fun `missing jwt proof fails with invalid proof`() = runBlocking {
        val credentialId = "test-credential"
        val issuerId = "did:example:issuer"
        val provider = buildOAuth2Provider(createTestConfig())
        val credentialRequestResult = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf(credentialId),
            ),
            session = DefaultSession(subject = "demo-subject"),
        )

        assertTrue(credentialRequestResult is CredentialRequestResult.Success)
        val credentialResponseResult = provider.createCredentialResponse(
            request = credentialRequestResult.request.withIssuer(issuerId),
            configuration = CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                vct = credentialId,
            ),
            issuerKey = JWKKey.generate(KeyType.Ed25519),
            issuerId = issuerId,
            issuanceInputData = issuanceInputs(
                buildJsonObject { put("given_name", "Alice") },
            ),
        )

        assertTrue(credentialResponseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, credentialResponseResult.error.error)
        val http = provider.writeCredentialError(credentialResponseResult.error)
        assertEquals(400, http.status)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, http.payload["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sd-jwt single and batch issuance round generated timestamps before signing`() = runBlocking {
        val mapping = buildJsonObject {
            put("iat", "<timestamp-seconds>")
            put("nbf", "<timestamp-seconds>")
            put("exp", "<timestamp-in-seconds:365d>")
            put("unchanged", "sample")
        }
        val times = listOf(
            "2026-09-10T00:00:00Z", "2026-09-10T17:43:28Z",
            "2026-09-10T17:59:59Z", "2026-09-10T18:00:00Z",
            "2026-09-10T23:59:59Z", "2026-09-11T00:00:00Z",
            "2028-02-29T17:43:28Z",
        )
        for (legacy in listOf(false, true)) {
            for (time in times) {
                for (count in 1..2) {
                    val now = Instant.parse(time)
                    val payloads = timeMappedPayloads(issueTimeMappedSdJwt(mapping, time, count, legacy))
                    assertEquals(count, payloads.size)
                    payloads.forEach { payload ->
                        assertEquals(now.epochSeconds.floorDiv(86_400L) * 86_400L, payload["iat"]!!.jsonPrimitive.long)
                        assertEquals(payload["iat"], payload["nbf"])
                        val exp = payload["exp"]!!.jsonPrimitive.long
                        assertEquals((now + 365.days).epochSeconds.floorDiv(3_600L) * 3_600L, exp)
                        assertTrue((now + 365.days).epochSeconds - exp in 0L..3_599L)
                        assertEquals("sample", payload["unchanged"]!!.jsonPrimitive.content)
                    }
                    assertEquals(count, payloads.map { it["cnf"] }.distinct().size)
                }
            }
        }
        assertEquals("<timestamp-seconds>", mapping["iat"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sd-jwt rounding preserves explicit dates missing claims and configured lifetimes`() = runBlocking {
        val time = "2026-09-10T17:43:28Z"
        val explicit = buildJsonObject {
            put("iat", Instant.parse(time).epochSeconds)
            put("nbf", Instant.parse("2026-09-11T09:43:28Z").epochSeconds)
            put("exp", Instant.parse("2026-09-12T09:43:28Z").epochSeconds)
        }
        val payload = timeMappedPayloads(issueTimeMappedSdJwt(explicit, time)).single()
        explicit.forEach { (claim, value) -> assertEquals(value, payload[claim]) }
        val missing = timeMappedPayloads(issueTimeMappedSdJwt(null, time)).single()
        listOf("iat", "nbf", "exp").forEach { assertNull(missing[it]) }
        val customLifetime = buildJsonObject { put("exp", "<timestamp-in-seconds:30d>") }
        val custom = timeMappedPayloads(issueTimeMappedSdJwt(customLifetime, time)).single()
        assertEquals(Instant.parse("2026-10-10T17:00:00Z").epochSeconds, custom["exp"]!!.jsonPrimitive.long)

        val preciseMapping = buildJsonObject {
            put("iat", "<timestamp-seconds>")
            put("nbf", "<timestamp-seconds>")
            put("exp", "<timestamp-in-seconds:365d>")
        }
        val before = Clock.System.now().epochSeconds
        val unrounded = timeMappedPayloads(issueTimeMappedSdJwt(preciseMapping, time, round = false)).single()
        val after = Clock.System.now().epochSeconds
        assertTrue(unrounded["iat"]!!.jsonPrimitive.long in before..after)
        assertTrue(unrounded["nbf"]!!.jsonPrimitive.long in before..after)
        assertTrue(unrounded["exp"]!!.jsonPrimitive.long in (before + 365.days.inWholeSeconds)..(after + 365.days.inWholeSeconds))
    }

    @Test
    fun `sd-jwt rounding rejects generated expiry that would already be expired`() = runBlocking {
        for (lifetime in listOf("10m", "0s", "-1d", "Infinity")) {
            val mapping = buildJsonObject { put("exp", "<timestamp-in-seconds:$lifetime>") }
            val result = issueTimeMappedSdJwt(mapping, "2026-09-10T17:43:28Z")
            assertEquals(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, assertIs<CredentialResponseResult.Failure>(result).error.error)
        }
    }

    private fun timeMappedPayloads(result: CredentialResponseResult): List<JsonObject> =
        assertNotNull(assertIs<CredentialResponseResult.Success>(result).response.credentials).map {
            SDJwt.parse(it.credential.jsonPrimitive.content).fullPayload
        }

    private suspend fun issueTimeMappedSdJwt(
        mapping: JsonObject?,
        time: String,
        count: Int = 1,
        legacy: Boolean = false,
        round: Boolean = true,
    ): CredentialResponseResult {
        suspend fun key(id: String) = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )
        val configuration = CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "https://issuer.example/identity")
        val request = DefaultCredentialRequest(
            client = DefaultClient("test-client", emptyList(), emptySet(), emptySet()),
            credentialIdentifier = null, credentialConfigurationId = "identity", proofs = null,
            credentialResponseEncryption = null,
        )
        val batch = CredentialIssuanceBatch(
            inputs = List(count) { CredentialIssuanceInput(buildJsonObject { put("given_name", "Jane") }) },
            verifiedProofs = List(count) { index ->
                VerifiedCredentialProof("jwt", "", "ES256", buildJsonObject {}, buildJsonObject {}, key("holder-$index"), null, null, null)
            },
        )
        var clockReads = 0
        val handler = SdJwtVcCredentialHandler(roundGeneratedTimeClaims = round, now = {
            // Any accidental per-credential clock read would move the batch into another day.
            Instant.parse(time) + (clockReads++).days
        })
        val issuer = key("issuer")
        val legacyIssuer = if (legacy) JWKKey.generate(KeyType.secp256r1) else null
        val result = if (legacyIssuer != null) {
            handler.sign(
                request, configuration, legacyIssuer, "https://issuer.example", batch,
                dataMapping = mapping, selectiveDisclosure = null, x5Chain = null, display = null,
                w3cVersion = null, mDocNameSpacesDataMappingConfig = null, authorizedTransactionDataTypes = null,
                validFrom = null, validUntil = null,
            )
        } else {
            handler.sign(
                request, configuration, Crypto2CredentialSigningKey.select(issuer, configuration), "https://issuer.example", batch,
                dataMapping = mapping, selectiveDisclosure = null, x5Chain = null, display = null,
                w3cVersion = null, mDocNameSpacesDataMappingConfig = null, authorizedTransactionDataTypes = null,
                validFrom = null, validUntil = null,
            )
        }
        assertEquals(if (round && mapping != null) 1 else 0, clockReads)
        if (result is CredentialResponseResult.Success) {
            result.response.credentials!!.forEach {
                val jwt = it.credential.jsonPrimitive.content.substringBefore("~")
                if (legacyIssuer != null) assertTrue(legacyIssuer.verifyJws(jwt).isSuccess)
                else assertTrue(CompactJws.verify(jwt, issuer, JwsAlgorithm.ES256).payload.isNotEmpty())
            }
        }
        return result
    }

    private fun issuanceInputs(credentialData: JsonObject) =
        CredentialIssuanceInputProvider { credentialCount ->
            List(credentialCount) { CredentialIssuanceInput(credentialData) }
        }
}
