@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.consent

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.*
import id.walt.dcql.models.*
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.*
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.*
import id.walt.wallet2.handlers.*
import id.walt.wallet2.stores.inmemory.*
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.request.AuthenticatedClientFacts
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Real software signatures; authentication is deliberately simulated, never native evidence. */
class PaymentConsentSubmissionTest {
    private fun test(block: suspend Context.() -> Unit) = runTest { withContext(Dispatchers.Default) {
        val fixture = PaymentConsentFixture.create()
        try { Context(fixture).apply { initialize() }.block() } finally { fixture.close() }
    } }

    private class Context(val fixture: PaymentConsentFixture) {
        var signatures = 0
        var authorizations = 0
        var cancelAuthorization = false
        lateinit var wallet: Wallet
        val policy = PaymentConsentPolicy(fixture.resolver, listOf("en"))
        val registry = TransactionDataTypeRegistry(setOf(TS12_PAYMENT_TYPE))
        val selections = listOf(PresentationCredentialSelection("ordinary", "ordinary"), PresentationCredentialSelection("payment", "payment"))
        val authorizer = WalletScaPresentationAuthorizer { _, _ ->
            authorizations++
            if (cancelAuthorization) throw CancellationException("Simulated native cancellation")
            ScaAuthenticationMethods.PossessionAndInherence(
                ScaAuthenticationMethods.Possession.OTHER, ScaAuthenticationMethods.Inherence.OTHER)
        }
        suspend fun initialize() {
            val original = fixture.issuerKey
            val countingKey = object : Key by original {
                override val capabilities = original.capabilities.copy(signer = Signer { data, algorithm ->
                    signatures++
                    requireNotNull(original.capabilities.signer).sign(data, algorithm)
                })
            }
            val jwk = requireNotNull(original.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(original.spec)
            val credential = fixture.credential(buildJsonObject { put("cnf", buildJsonObject {
                put("jwk", Json.parseToJsonElement(jwk.data.toByteArray().decodeToString()))
            }) })
            val ordinary = fixture.credential(buildJsonObject {
                put("vct", "$ISSUER/ordinary"); put("cnf", credential.credentialData.getValue("cnf"))
            })
            wallet = Wallet(id = "consent-${kotlin.random.Random.nextLong()}",
                keyStores = listOf(InMemoryKeyStore().also { it.addCrypto2Key(countingKey) }),
                credentialStores = listOf(InMemoryCredentialStore().also { store ->
                    store.addCredential(StoredCredential("ordinary", ordinary))
                    store.addCredential(StoredCredential("payment", credential))
                    store.addCredential(StoredCredential("other-payment", fixture.credential(buildJsonObject {
                        put("card_id", "other-card"); put("cnf", credential.credentialData.getValue("cnf"))
                    })))
                }))
        }
        fun authorization() = AuthorizationRequest(
            nonce = "consent-nonce", responseType = OpenID4VPResponseType.VP_TOKEN,
            responseMode = OpenID4VPResponseMode.DC_API,
            dcqlQuery = DcqlQuery(selections.map { CredentialQuery(id = it.queryId, format = CredentialFormat.DC_SD_JWT, multiple = true,
                meta = SdJwtVcMeta(vctValues = listOf(if (it.queryId == "payment") "$ISSUER/vct" else "$ISSUER/ordinary"))) }),
            transactionData = listOf(buildJsonObject {
                put("type", TS12_PAYMENT_TYPE); put("credential_ids", buildJsonArray { add(JsonPrimitive("payment")) })
                put("payload", fixture.payload)
            }.toString().encodeToByteArray().encodeToBase64Url()),
        )
        suspend fun dcRequest(): SubmitDcApiPresentationRequest {
            val preview = WalletPresentationHandler.previewDcApiPresentation(wallet,
                PreviewDcApiPresentationRequest("openid4vp-v1-unsigned", Json.encodeToJsonElement(authorization()).jsonObject,
                    "https://verifier.example"), transactionDataTypeRegistry = registry)
            assertEquals(setOf("ordinary", "payment"), preview.credentialOptions.map { it.queryId }.toSet())
            return SubmitDcApiPresentationRequest(preview.requestId, selections)
        }
        suspend fun submit(request: SubmitDcApiPresentationRequest) = WalletPresentationHandler.submitDcApiPresentation(
            wallet, request, transactionDataTypeRegistry = registry, scaAuthorizer = authorizer, paymentConsentPolicy = policy)
        suspend fun prepare(request: SubmitDcApiPresentationRequest) = assertNotNull(
            WalletPresentationHandler.prepareDcApiPaymentConsent(wallet, request, policy))
        suspend fun urlRequest(): SubmitPresentationRequest {
            val request = authorization().copy(clientId = "redirect_uri:https://verifier.example/callback",
                responseUri = "https://verifier.example/callback", responseMode = OpenID4VPResponseMode.DIRECT_POST)
            val handle = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(wallet,
                WalletPresentationHandler.PreviewedPresentation.Ready(Url("openid4vp://request"),
                    ResolvedAuthorizationRequest.Plain(request, AuthenticatedClientFacts.redirectUriBound(request)), fixture.issuerKey.id.value))
            return SubmitPresentationRequest(handle, selections)
        }
        suspend fun submit(request: SubmitPresentationRequest) = WalletPresentationHandler.submitPresentation(
            wallet, request, transactionDataTypeRegistry = registry, scaAuthorizer = authorizer, paymentConsentPolicy = policy)
    }

    @Test fun rejectionInterruptsMetadataPreparationBeforeEnteringTheDenialTransport() = test {
        val request = urlRequest()
        val started = CompletableDeferred<Unit>()
        fixture.onMetadataRequest = { started.complete(Unit); awaitCancellation() }
        coroutineScope {
            val preparation = launch { WalletPresentationHandler.preparePaymentConsent(wallet, request, policy) }
            started.await()
            // Stop at the transport boundary: no external verifier is needed to prove lease arbitration.
            assertFailsWith<RejectionTransportReached> {
                WalletPresentationHandler.rejectPresentation(wallet, RejectPresentationRequest(request.previewHandle),
                    onEvent = { throw RejectionTransportReached() })
            }
            preparation.join()
            assertTrue(preparation.isCancelled)
        }
        assertEquals(0, signatures)
        assertEquals(0, authorizations)
        assertFailsWith<PreviewSessionException> { WalletPresentationHandler.preparePaymentConsent(wallet, request, policy) }
    }

    private class RejectionTransportReached : RuntimeException()

    @Test fun dcApiSignsOnlyAfterReviewAndConsumesSuccessfulPreview() = test {
        val request = dcRequest()
        val consent = prepare(request)
        assertTrue(consent.requiresUnsignedRequestWarning)
        assertEquals(0, signatures)
        val response = submit(request.copy(paymentConsentRevision = consent.revision))
        assertNotNull(response)
        assertEquals(2, signatures)
        assertEquals(1, authorizations)
        assertFailsWith<PreviewSessionException> { submit(request.copy(paymentConsentRevision = consent.revision)) }
        assertEquals(2, signatures)
    }

    @Test fun bothTransportsRejectMissingAndStaleReviewBeforeSigningEvenWithOrdinaryCredentialFirst() = test {
        for (stale in listOf(false, true)) {
            val dc = dcRequest()
            if (stale) prepare(dc)
            assertFailure(if (stale) PaymentConsentFailure.STALE_CONSENT else PaymentConsentFailure.CONSENT_REQUIRED) {
                submit(dc.copy(paymentConsentRevision = if (stale) "wrong" else null))
            }
            val url = urlRequest()
            if (stale) WalletPresentationHandler.preparePaymentConsent(wallet, url, policy)
            assertFailure(if (stale) PaymentConsentFailure.STALE_CONSENT else PaymentConsentFailure.CONSENT_REQUIRED) {
                submit(url.copy(paymentConsentRevision = if (stale) "wrong" else null))
            }
        }
        assertEquals(0, signatures)
        assertEquals(0, authorizations)
    }

    @Test fun changingTheSelectedCredentialRequiresNewConsentOnBothTransports() = test {
        val changed = listOf(selections.first(), PresentationCredentialSelection("payment", "other-payment"))
        val dc = dcRequest()
        val initialDc = prepare(dc)
        assertFailure(PaymentConsentFailure.STALE_CONSENT) {
            submit(dc.copy(selectedCredentialOptions = changed, paymentConsentRevision = initialDc.revision))
        }
        val url = urlRequest()
        val initialUrl = assertNotNull(WalletPresentationHandler.preparePaymentConsent(wallet, url, policy))
        assertFailure(PaymentConsentFailure.STALE_CONSENT) {
            submit(url.copy(selectedCredentialOptions = changed, paymentConsentRevision = initialUrl.revision))
        }
        assertEquals(0, signatures)
        assertEquals(0, authorizations)
        val updated = dc.copy(selectedCredentialOptions = changed)
        val consent = prepare(updated)
        assertNotEquals(initialDc.revision, consent.revision)
        assertNotNull(submit(updated.copy(paymentConsentRevision = consent.revision)))
        assertEquals(2, signatures)
        assertEquals(1, authorizations)
    }

    @Test fun invalidMetadataCannotLeaveAnEarlierReviewUsable() = test {
        val request = dcRequest()
        val consent = prepare(request)
        fixture.metadata = JsonObject(emptyMap())
        assertFailure(PaymentConsentFailure.INVALID_METADATA) { prepare(request) }
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { submit(request.copy(paymentConsentRevision = consent.revision)) }
        assertEquals(0, signatures)
    }

    @Test fun cancelledAuthorizationRequiresNewReviewAndDiscardPreventsRetry() = test {
        val request = dcRequest().copy(selectedCredentialOptions = selections.reversed())
        val consent = prepare(request)
        cancelAuthorization = true
        assertFailsWith<CancellationException> { submit(request.copy(paymentConsentRevision = consent.revision)) }
        assertEquals(1, signatures) // The ordinary proof was built first; no payment proof or response was released.
        cancelAuthorization = false
        assertFailure(PaymentConsentFailure.CONSENT_REQUIRED) { submit(request.copy(paymentConsentRevision = consent.revision)) }
        val retry = prepare(request)
        assertNotEquals(consent.revision, retry.revision)
        WalletPresentationHandler.discardDcApiPreview(wallet, request.requestId)
        assertFailsWith<PreviewSessionException> { submit(request.copy(paymentConsentRevision = retry.revision)) }
        assertEquals(1, signatures) // The ordinary proof was built first; no payment proof or response was released.
    }
}
