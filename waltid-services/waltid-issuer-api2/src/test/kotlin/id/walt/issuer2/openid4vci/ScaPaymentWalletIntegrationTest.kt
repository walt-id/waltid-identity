@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.issuer2.openid4vci

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

/** Real configured issuer, wallet and verifier; only authentication factors are simulated. */
class ScaPaymentWalletIntegrationTest {
    @AfterEach
    fun clearConfig() = clearIssuer2TestEnvironment()

    @Test
    fun configuredPaymentCardReachesWalletAndVerifier() = paymentFlow(missingLabel = false)

    @Test
    fun missingIssuerLabelBlocksConsentBeforeAuthorizationOrSigning() = paymentFlow(missingLabel = true)

    private fun paymentFlow(missingLabel: Boolean) = testApplication {
        installIssuer2WithConfigFiles(configureServiceConfig = { it.copy(baseUrl = "https://localhost") })
        if (missingLabel) {
            val config = ConfigManager.getConfig<Issuer2MetadataConfig>()
            val metadata = config.sdJwtVcTypeMetadataConfiguration.getValue("sca_payment_card_sd_jwt")
            val parameters = requireNotNull(metadata.customParameters)
            val types = parameters.getValue("transaction_data_types").jsonObject
            val payment = types.getValue("urn:eudi:sca:payment:1").jsonObject
            val catalogue = payment.getValue("ui_labels").jsonObject
            val incompletePayment = JsonObject(payment +
                ("ui_labels" to JsonObject(catalogue - "affirmative_action_label")))
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
            issuerKey.getPublicKey().exportJWK(), missingLabel)
    }

    @Test
    @Tag("live-payment")
    fun deployedPaymentCardReachesWalletAndVerifier() = runBlocking {
        val issuerBase = System.getProperty("payment.issuerUrl", "https://issuer2.demo.walt.id").trimEnd('/')
        val verifierBase = System.getProperty("payment.verifierUrl", "https://verifier2.demo.walt.id").trimEnd('/')
        HttpClient(Java) {
            followRedirects = false
            defaultRequest { url(issuerBase) }
            install(ClientContentNegotiation) { json(issuer2TestJson) }
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }.use { http ->
            // Pin the same public verification key as the demos, independently of the received credential.
            val issuerPublicJwk = """{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}"""
            withTimeout(120_000) { paymentFlow(http, issuerBase, verifierBase, issuerPublicJwk) }
        }
    }

    private suspend fun paymentFlow(
        http: HttpClient, issuerBase: String, verifierBase: String,
        issuerPublicJwk: String, missingLabel: Boolean = false,
    ) {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        try {
            val generatedKey = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId("payment-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ))
            var signatures = 0
            val holderKey = object : Key by generatedKey {
                override val capabilities = generatedKey.capabilities.copy(signer = Signer { bytes, algorithm ->
                    signatures++
                    requireNotNull(generatedKey.capabilities.signer).sign(bytes, algorithm)
                })
            }
            val wallet = Wallet(
                id = "payment-${UUID.randomUUID()}",
                keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(holderKey) }),
                credentialStores = listOf(InMemoryCredentialStore()),
            )
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
            val example = Verifier2OpenApiExamples.openid4vpDcApiSdJwtScaPayment
            val setup = example.copy(core = example.core.copy(
                clientId = null, signedRequest = false, encryptedResponse = false,
                dcqlQuery = DcqlQuery(requireNotNull(example.core.dcqlQuery).credentials.map {
                    it.copy(meta = SdJwtVcMeta(vctValues = listOf(vct)))
                }),
            ))
            val created = http.post("$verifierBase/verification-session/create") {
                contentType(ContentType.Application.Json)
                setBody<VerificationSessionSetup>(setup)
            }.also { assertTrue(it.status.isSuccess(), it.bodyAsText()) }
                .body<VerificationSessionCreationResponse>()
            val envelope = http.get("$verifierBase/verification-session/${created.sessionId}/request").body<JsonObject>()
            val request = envelope.getValue("digital").jsonObject.getValue("requests").jsonArray.single().jsonObject
            val data = request.getValue("data").jsonObject
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
            assertEquals("Prüfen Sie Empfänger und Betrag vor der Bestätigung.", payment.securityHint)
            assertEquals("Zahlung bestätigen", payment.affirmativeAction)
            assertEquals("Zahlung abbrechen", payment.denialAction)
            val fields = payment.fields.associateBy { it.path }
            val payee = fields.getValue(listOf("payee", "name"))
            assertEquals("Zahlungsempfänger", payee.label)
            assertEquals("Super Store", payee.value)
            assertEquals(PaymentFieldPlacement.PROMINENT, payee.placement)
            assertEquals("Betrag", fields.getValue(listOf("amount")).label)
            assertEquals("11.56", fields.getValue(listOf("amount")).value)
            assertEquals(0, authorizations)
            assertEquals(0, signatures)
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
