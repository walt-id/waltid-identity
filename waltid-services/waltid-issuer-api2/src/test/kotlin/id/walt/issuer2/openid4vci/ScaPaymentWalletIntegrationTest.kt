@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.issuer2.openid4vci

import id.walt.mobile.test.backend.DemoPayment
import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.wallet2.consent.*
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.Ts12PaymentValidationException
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.openapi.Verifier2OpenApiExamples
import id.walt.verifier2.verifierApi
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.sse.SSE
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Real configured issuer, wallet and verifier; only authentication factors are simulated. */
class ScaPaymentWalletIntegrationTest {
    private enum class MissingMetadata { ACTION_LABEL, OPTIONAL_CLAIM }
    private enum class ExpiryBoundary { REVIEW, SIGNING }

    @AfterEach
    fun clearConfig() = clearIssuer2TestEnvironment()

    @Test
    fun configuredPaymentCardReachesWalletAndVerifier() = paymentFlow()

    @Test
    fun missingIssuerLabelBlocksConsentBeforeAuthorizationOrSigning() = paymentFlow(MissingMetadata.ACTION_LABEL)

    @Test
    fun missingOptionalFieldMetadataBlocksConsentBeforeAuthorizationOrSigning() =
        paymentFlow(MissingMetadata.OPTIONAL_CLAIM, recurringTerms())

    @Test
    fun scheduledPaymentReachesWalletAndVerifier() = paymentFlow(extra = DemoPayment.SCHEDULED.payload())

    @Test
    fun recurringPaymentReachesWalletAndVerifier() = paymentFlow(extra = DemoPayment.RECURRING.payload())

    @Test
    fun paymentExpiryDuringReviewDoesNotReleaseAProof() =
        paymentFlow(extra = DemoPayment.SCHEDULED.payload(), expiry = ExpiryBoundary.REVIEW)

    @Test
    fun paymentExpiryDuringSigningDoesNotReleaseAProof() =
        paymentFlow(extra = DemoPayment.SCHEDULED.payload(), expiry = ExpiryBoundary.SIGNING)

    private fun recurringTerms() = Json.parseToJsonElement(
        """{"recurrence":{"frequency":"MNTH","number":12,"mit_options":{"amount_variable":false,"min_amount":1.23,"max_amount":9007199254740993.01,"apr":0.1234567890123456789}}}""").jsonObject

    private fun paymentFlow(missingMetadata: MissingMetadata? = null, extra: JsonObject? = null,
        expiry: ExpiryBoundary? = null) = testApplication {
        val attester = Issuer2TestKeyAttester.create()
        installIssuer2WithConfigFiles(configureServiceConfig = {
            it.copy(baseUrl = "https://localhost", keyAttestationConfig = attester.issuerTrust)
        })
        if (missingMetadata != null) {
            val config = ConfigManager.getConfig<Issuer2MetadataConfig>()
            val metadata = config.sdJwtVcTypeMetadataConfiguration.getValue("sca_payment_card_sd_jwt")
            val parameters = requireNotNull(metadata.customParameters)
            val types = parameters.getValue("transaction_data_types").jsonObject
            val payment = types.getValue("urn:eudi:sca:payment:1").jsonObject
            val incompletePayment = when (missingMetadata) {
                MissingMetadata.ACTION_LABEL -> JsonObject(payment + ("ui_labels" to
                    JsonObject(payment.getValue("ui_labels").jsonObject - "affirmative_action_label")))
                MissingMetadata.OPTIONAL_CLAIM -> {
                    val claims = payment.getValue("claims").jsonArray
                    val remaining = claims.filterNot {
                        it.jsonObject.getValue("path").jsonArray.map { part -> part.jsonPrimitive.content } ==
                            listOf("recurrence", "mit_options", "apr")
                    }
                    assertEquals(claims.size - 1, remaining.size)
                    JsonObject(payment + ("claims" to JsonArray(remaining)))
                }
            }
            val incomplete = metadata.copy(customParameters = parameters +
                ("transaction_data_types" to JsonObject(types + ("urn:eudi:sca:payment:1" to incompletePayment))))
            ConfigManager.loadedConfigurations["credential-issuer-metadata" to Issuer2MetadataConfig::class] =
                config.copy(sdJwtVcTypeMetadataConfiguration = config.sdJwtVcTypeMetadataConfiguration +
                    ("sca_payment_card_sd_jwt" to incomplete))
        }
        ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
        ConfigManager.loadedConfigurations["verifier-service" to OSSVerifier2ServiceConfig::class] = OSSVerifier2ServiceConfig(
            urlPrefix = "https://verifier.example/verification-session", urlHost = "openid4vp://authorize",
        )
        externalServices {
            hosts("https://verifier.example") {
                // Match verifier2's HTTP encoding, including default policy identifiers.
                install(ContentNegotiation) { json(Json(issuer2TestJson) { encodeDefaults = true }) }
                install(SSE)
                verifierApi()
            }
        }
        val issuerKey = KeyManager.resolveSerializedKey(ConfigManager.getConfig<Issuer2ProfilesConfig>()
            .profiles.getValue("scaPaymentCardSdJwt").issuerKey)
        paymentFlow(apiClient(), "https://localhost", "https://verifier.example",
            issuerKey.getPublicKey().exportJWK(),
            "Prüfen Sie Empfänger, Beträge, Termine und wiederkehrende Bedingungen.",
            missingMetadata != null, attester, extra, expiry)
    }

    @Test @Tag("live-payment")
    fun deployedPaymentCardReachesWalletAndVerifier() = deployedPayment(DemoPayment.MINIMAL)

    @Test @Tag("live-payment")
    fun deployedScheduledPaymentReachesWalletAndVerifier() = deployedPayment(DemoPayment.SCHEDULED)

    @Test @Tag("live-payment")
    fun deployedRecurringPaymentReachesWalletAndVerifier() = deployedPayment(DemoPayment.RECURRING)

    @Test @Tag("live-payment")
    fun deployedPaymentExpiresDuringReviewWithoutReleasingAProof() =
        deployedPayment(DemoPayment.SCHEDULED, ExpiryBoundary.REVIEW)

    @Test @Tag("live-payment")
    fun deployedPaymentExpiresDuringSigningWithoutReleasingAProof() =
        deployedPayment(DemoPayment.SCHEDULED, ExpiryBoundary.SIGNING)

    @Test @Tag("live-payment")
    fun deployedVerifierRejectsPastSchedulingBeforeCreatingSession() = withDeployedServices { http, _, verifierBase ->
        val shipped = Verifier2OpenApiExamples.openid4vpDcApiSdJwtScaPayment
        val openid = requireNotNull(shipped.openid)
        val entries = requireNotNull(openid.transactionData).map { item ->
            JsonObject(item + ("payload" to JsonObject(item.getValue("payload").jsonObject +
                ("execution_date" to JsonPrimitive((Clock.System.now() - 1.days).toString())))))
        }
        val response = http.post("$verifierBase/verification-session/create") {
            contentType(ContentType.Application.Json)
            setBody<VerificationSessionSetup>(shipped.copy(core = shipped.core.copy(clientId = null, signedRequest = false, encryptedResponse = false),
                openid = openid.copy(transactionData = entries)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status,
            "WAL-1665: deployed verifier must reject past execution_date at session creation")
        val error = response.bodyAsText()
        assertTrue("execution_date" in error && "must not lie in the past" in error,
            "Expected the scheduling rule, not an unrelated HTTP 400")
        assertFalse("sessionId" in error, "Invalid scheduling must not create a session")
    }

    private fun deployedPayment(payment: DemoPayment, expiry: ExpiryBoundary? = null) = withDeployedServices { http, issuerBase, verifierBase ->
        val profiles = http.get("$verifierBase/transaction-data-profiles")
        assertEquals(HttpStatusCode.OK, profiles.status, "WAL-1665: verifier profile discovery unavailable")
        val descriptor = profiles.body<JsonArray>().single {
            it.jsonObject["type"]?.jsonPrimitive?.content == "urn:eudi:sca:payment:1"
        }.jsonObject["payloadSchema"]
        assertTrue(descriptor is JsonObject, "WAL-1665: deploy TS12 payloadSchema before public payment acceptance")
        val metadataResponse = http.get("$issuerBase/openid4vci/sca_payment_card_sd_jwt")
        assertEquals(HttpStatusCode.OK, metadataResponse.status, "WAL-1665: payment type metadata unavailable")
        val metadata = metadataResponse.body<JsonObject>().getValue("transaction_data_types").jsonObject
            .getValue("urn:eudi:sca:payment:1").jsonObject
        val paths = metadata.getValue("claims").jsonArray.associate { claim ->
            val value = claim.jsonObject
            value.getValue("path").jsonArray.joinToString(".") { it.jsonPrimitive.content } to value
        }
        val expectedPaths = setOf("transaction_id", "payee.name", "payee.id", "currency", "amount", "date_time",
            "payee.logo", "payee.website", "pisp.legal_name", "pisp.brand_name", "pisp.domain_name", "amount_estimated",
            "amount_earmarked", "sct_inst", "execution_date", "recurrence.start_date", "recurrence.end_date",
            "recurrence.number", "recurrence.frequency", "recurrence.mit_options.amount_variable",
            "recurrence.mit_options.min_amount", "recurrence.mit_options.max_amount", "recurrence.mit_options.total_amount",
            "recurrence.mit_options.initial_amount", "recurrence.mit_options.initial_amount_number", "recurrence.mit_options.apr")
        assertEquals(expectedPaths, paths.keys, "WAL-1665: issuer must deploy all 26 payment claim paths")
        paths.forEach { (path, claim) ->
            val labels = claim.getValue("display").jsonArray.associate {
                it.jsonObject.getValue("locale").jsonPrimitive.content to it.jsonObject.getValue("label").jsonPrimitive.content
            }
            for (language in listOf("en", "de")) assertFalse(labels[language].isNullOrBlank(), "Missing $language label for $path")
        }
        val issuerPublicJwk = """{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}"""
        paymentFlow(http, issuerBase, verifierBase, issuerPublicJwk,
            "Prüfen Sie Empfänger, Beträge, Termine und wiederkehrende Bedingungen.", extra = payment.payload(), expiry = expiry)
    }

    private fun withDeployedServices(block: suspend (HttpClient, String, String) -> Unit) = runBlocking {
        val issuerBase = System.getProperty("payment.issuerUrl", "https://issuer2.demo.walt.id").trimEnd('/')
        val verifierBase = System.getProperty("payment.verifierUrl", "https://verifier2.demo.walt.id").trimEnd('/')
        HttpClient(Java) {
            followRedirects = false
            defaultRequest { url(issuerBase) }
            install(ClientContentNegotiation) { json(issuer2TestJson) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }.use { http -> withTimeout(120_000) { block(http, issuerBase, verifierBase) } }
    }

    private suspend fun paymentFlow(
        http: HttpClient, issuerBase: String, verifierBase: String,
        issuerPublicJwk: String, expectedSecurityHint: String, missingLabel: Boolean = false,
        attester: Issuer2TestKeyAttester? = null,
        extra: JsonObject? = null,
        expiry: ExpiryBoundary? = null,
    ) {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        try {
            val generatedKey = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("payment-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            var signatures = 0
            var submissionStarted = false
            var deadline: Instant? = null
            suspend fun crossDeadline() {
                val remaining = requireNotNull(deadline) - Clock.System.now()
                if (remaining.isPositive()) delay(remaining + 1.seconds)
            }
            val holderKey = object : Key by generatedKey {
                override val capabilities = generatedKey.capabilities.copy(signer = Signer { bytes, algorithm ->
                    signatures++
                    if (submissionStarted && expiry == ExpiryBoundary.SIGNING) crossDeadline()
                    requireNotNull(generatedKey.capabilities.signer).sign(bytes, algorithm)
                })
            }
            val wallet = Wallet(
                id = "payment-${UUID.randomUUID()}",
                keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(holderKey) }),
                credentialStores = listOf(InMemoryCredentialStore()),
            )
            attester?.let { wallet.attachKeyAttestationProvider(it) }
            val scenario = Issuer2CredentialScenarios.configured.single { it.profileId == "scaPaymentCardSdJwt" }
            val metadata = http.get("$issuerBase/.well-known/openid-credential-issuer/openid4vci")
            assertTrue(metadata.status.isSuccess(), "Issuer metadata unavailable at $issuerBase: ${metadata.status}")
            val configurations = metadata.body<JsonObject>().getValue("credential_configurations_supported").jsonObject
            assertTrue("sca_payment_card_sd_jwt" in configurations,
                "Required payment deployment is missing at $issuerBase: sca_payment_card_sd_jwt is not advertised")
            val offer = http.createWalletFlowCredentialOffer(scenario = scenario,
                authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED, txCodeMode = Issuer2TxCodeMode.NONE)
            val credential = WalletIssuanceHandler.receiveCredentialFlow(
                wallet, ReceiveCredentialRequest(offerUrl = Url(offer.credentialOffer)), httpClient = http,
            ).toList().single()
            val vct = credential.credential.credentialData.getValue("vct").jsonPrimitive.content
            assertEquals("$issuerBase/openid4vci/sca_payment_card_sd_jwt", vct)

            // Reuse the shipped demo request. Only transport protection and the issuer VCT differ;
            // signed/encrypted transport variants already have their own protocol/app coverage.
            val shipped = Verifier2OpenApiExamples.openid4vpDcApiSdJwtScaPayment
            if (expiry != null) deadline = Clock.System.now() + 30.seconds
            val requestExtra = if (expiry == null) extra else JsonObject(requireNotNull(extra) +
                ("execution_date" to JsonPrimitive(requireNotNull(deadline).toString())))
            val example = if (requestExtra == null) shipped else shipped.copy(openid = requireNotNull(shipped.openid).copy(
                transactionData = requireNotNull(requireNotNull(shipped.openid).transactionData).map { item ->
                    JsonObject(item + ("payload" to JsonObject(item.getValue("payload").jsonObject + requestExtra)))
                }))
            val setup = example.copy(core = example.core.copy(
                clientId = null, signedRequest = false, encryptedResponse = false,
                dcqlQuery = DcqlQuery(requireNotNull(example.core.dcqlQuery).credentials.map {
                    it.copy(meta = SdJwtVcMeta(vctValues = listOf(vct)))
                }),
            ))
            if (extra?.containsKey("recurrence") == true) assertEquals("9007199254740993.01",
                requireNotNull(setup.openid?.transactionData).single().getValue("payload").jsonObject
                    .getValue("recurrence").jsonObject.getValue("mit_options").jsonObject.getValue("max_amount").jsonPrimitive.content)
            val created = http.post("$verifierBase/verification-session/create") {
                contentType(ContentType.Application.Json)
                setBody<VerificationSessionSetup>(setup)
            }.also { assertTrue(it.status.isSuccess(), it.bodyAsText()) }
                .body<VerificationSessionCreationResponse>()
            val envelope = http.get("$verifierBase/verification-session/${created.sessionId}/request").body<JsonObject>()
            val request = envelope.getValue("digital").jsonObject.getValue("requests").jsonArray.single().jsonObject
            val data = request.getValue("data").jsonObject
            if (extra?.containsKey("recurrence") == true) {
                val encoded = data.getValue("transaction_data").jsonArray.single().jsonPrimitive.content
                val decoded = Json.parseToJsonElement(Base64.getUrlDecoder().decode(encoded).decodeToString()).jsonObject
                assertEquals("9007199254740993.01", decoded.getValue("payload").jsonObject.getValue("recurrence").jsonObject.getValue("mit_options").jsonObject.getValue("max_amount").jsonPrimitive.content)
            }
            val registry = TransactionDataTypeRegistry(setOf("urn:eudi:sca:payment:1"))
            val preview = WalletPresentationHandler.previewDcApiPresentation(wallet,
                PreviewDcApiPresentationRequest(request.getValue("protocol").jsonPrimitive.content, data, setup.expectedOrigins.single()),
                transactionDataTypeRegistry = registry,
            )
            assertEquals(setOf("sca_payment"), preview.credentialOptions.map { it.queryId }.toSet())
            val submission = SubmitDcApiPresentationRequest(preview.requestId,
                listOf(PresentationCredentialSelection("sca_payment", credential.id)))
            var authorizations = 0
            val authorizer = WalletScaPresentationAuthorizer { _, _ ->
                authorizations++
                ScaAuthenticationMethods.PossessionAndInherence(
                    ScaAuthenticationMethods.Possession.OTHER, ScaAuthenticationMethods.Inherence.OTHER)
            }
            val policy = PaymentConsentPolicy(PaymentConsentResolver(listOf(PaymentCredentialIssuer(
                "$issuerBase/openid4vci", issuerPublicJwk,
            )), http), listOf("de-DE", "en"))
            signatures = 0 // The issuance proof is separate from payment authorization.
            if (missingLabel) {
                val failure = assertFailsWith<PaymentConsentException> {
                    WalletPresentationHandler.prepareDcApiPaymentConsent(wallet, submission, policy)
                }
                assertEquals(PaymentConsentFailure.INVALID_METADATA, failure.reason)
                val blocked = assertFailsWith<PaymentConsentException> {
                    WalletPresentationHandler.submitDcApiPresentation(wallet, submission,
                        transactionDataTypeRegistry = registry, scaAuthorizer = authorizer, paymentConsentPolicy = policy)
                }
                assertEquals(PaymentConsentFailure.CONSENT_REQUIRED, blocked.reason)
                assertEquals(0, authorizations)
                assertEquals(0, signatures)
                val info = http.get("$verifierBase/verification-session/${created.sessionId}/info").body<JsonObject>()
                assertEquals(false, info.getValue("attempted").jsonPrimitive.boolean)
                return
            }
            val prepared = assertNotNull(WalletPresentationHandler.prepareDcApiPaymentConsent(wallet, submission, policy))
            val payment = prepared.payment
            assertEquals("de", payment.locale)
            assertEquals("Diese Zahlung bestätigen", payment.title)
            assertEquals(expectedSecurityHint, payment.securityHint)
            assertEquals("Zahlung bestätigen", payment.affirmativeAction)
            assertEquals("Zahlung abbrechen", payment.denialAction)
            val fields = payment.fields.associateBy { it.path }
            val payee = fields.getValue(listOf("payee", "name"))
            assertEquals("Zahlungsempfänger", payee.label)
            assertEquals("Super Store", payee.value)
            assertEquals(PaymentFieldPlacement.PROMINENT, payee.placement)
            assertEquals("Betrag", fields.getValue(listOf("amount")).label)
            assertEquals("11.56", fields.getValue(listOf("amount")).value)
            if (extra?.containsKey("execution_date") == true) {
                assertEquals(requireNotNull(requestExtra).getValue("execution_date").jsonPrimitive.content, fields.getValue(listOf("execution_date")).value)
            }
            if (extra?.containsKey("recurrence") == true) {
                assertEquals("Monatlich", fields.getValue(listOf("recurrence", "frequency")).value)
                assertEquals("9007199254740993.01", fields.getValue(listOf("recurrence", "mit_options", "max_amount")).value)
                assertEquals("0.1234567890123456789", fields.getValue(listOf("recurrence", "mit_options", "apr")).value)
            }
            assertEquals(0, authorizations)
            assertEquals(0, signatures)
            submissionStarted = true
            if (expiry != null) {
                if (expiry == ExpiryBoundary.REVIEW) crossDeadline()
                val failure = assertFailsWith<Ts12PaymentValidationException> {
                    WalletPresentationHandler.submitDcApiPresentation(wallet,
                        submission.copy(paymentConsentRevision = prepared.revision), transactionDataTypeRegistry = registry,
                        scaAuthorizer = authorizer, paymentConsentPolicy = policy)
                }
                assertEquals("/execution_date", failure.path)
                assertEquals(if (expiry == ExpiryBoundary.REVIEW) 0 else 1, signatures)
                assertEquals(if (expiry == ExpiryBoundary.REVIEW) 0 else 1, authorizations)
                val info = http.get("$verifierBase/verification-session/${created.sessionId}/info").body<JsonObject>()
                assertFalse(info.getValue("attempted").jsonPrimitive.boolean, "Expired payment released a response")
                return
            }
            val response = WalletPresentationHandler.submitDcApiPresentation(wallet,
                submission.copy(paymentConsentRevision = prepared.revision), transactionDataTypeRegistry = registry,
                scaAuthorizer = authorizer, paymentConsentPolicy = policy)
            assertEquals(1, signatures)
            assertEquals(1, authorizations)
            val wire = DcApiWallet.encodeResponse(response)
            val presentation = Json.parseToJsonElement(wire).jsonObject.getValue("data").jsonObject
                .getValue("vp_token").jsonObject.getValue("sca_payment").jsonArray.single().jsonPrimitive.content
            val proof = Json.parseToJsonElement(Base64.getUrlDecoder().decode(presentation.substringAfterLast('~').split('.')[1])
                .decodeToString()).jsonObject
            val original = data.getValue("transaction_data").jsonArray.single().jsonPrimitive.content
            val expectedHash = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(original.encodeToByteArray()))
            assertEquals(listOf(expectedHash), proof.getValue("transaction_data_hashes").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("sha-256", proof.getValue("transaction_data_hashes_alg").jsonPrimitive.content)
            assertEquals(data.getValue("nonce"), proof.getValue("nonce"))

            val accepted = http.post("$verifierBase/verification-session/${created.sessionId}/response") {
                contentType(ContentType.Application.Json)
                setBody(wire)
            }
            assertTrue(accepted.status.isSuccess(), accepted.bodyAsText())
            val info = http.get("$verifierBase/verification-session/${created.sessionId}/info").body<JsonObject>()
            assertEquals("SUCCESSFUL", info.getValue("status").jsonPrimitive.content, info.toString())
            val policies = info.getValue("policy_results").jsonObject.getValue("vp_policies").jsonObject
                .getValue("sca_payment").jsonObject
            for (id in listOf("dc+sd-jwt/kb-jwt_signature", "dc+sd-jwt/sd_hash-check", "dc+sd-jwt/transaction-data-hash-check")) {
                val result = policies.getValue(id).jsonObject
                assertEquals(id, result.getValue("policy_executed").jsonObject.getValue("id").jsonPrimitive.content)
                assertTrue(result.getValue("success").jsonPrimitive.boolean, "Required verifier policy did not pass: $id; $info")
            }
        } finally {
            runtime.close()
        }
    }
}
