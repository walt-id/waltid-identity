package id.walt.wallet2.handlers

import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.RequiredCredentialUnavailableException
import id.walt.dcql.UnsupportedDcqlConstraintException
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.TrustedAuthoritiesQuery
import id.walt.dcql.models.TrustedAuthorityType
import id.walt.dcql.models.meta.NoMeta
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class WalletPresentationHandlerRequirementsTest {

    @Test
    fun presentationEventsReflectProtocolOutcome() {
        assertEquals(
            WalletSessionEvent.presentation_completed,
            WalletPresentResult(transmissionSuccess = true).presentationOutcomeEvent(),
        )
        assertEquals(
            WalletSessionEvent.presentation_failed,
            WalletPresentResult(transmissionSuccess = false).presentationOutcomeEvent(),
        )
        assertEquals(
            WalletSessionEvent.presentation_response_prepared,
            WalletPresentResult(getUrl = "https://verifier.example/callback").presentationOutcomeEvent(),
        )
        assertEquals(
            WalletSessionEvent.presentation_response_prepared,
            WalletPresentResult(formPostHtml = "<form></form>").presentationOutcomeEvent(),
        )
    }

    @Test
    fun presentationRequirementsWithoutCredentialSetsRequireEveryQuery() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("pid"),
                credentialQuery("age"),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(listOf(PresentationCredentialRequirement(options = listOf(listOf("pid", "age")))), requirements)
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("pid", "age")) })
        assertFalse(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("pid")) })
    }

    @Test
    fun presentationRequirementsHonorRequiredCredentialSetAlternatives() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("mdl-id"),
                credentialQuery("photo-id"),
                credentialQuery("address"),
            ),
            credentialSets = listOf(
                CredentialSetQuery(
                    options = listOf(
                        listOf("mdl-id"),
                        listOf("photo-id"),
                    )
                ),
                CredentialSetQuery(
                    required = false,
                    options = listOf(listOf("address")),
                ),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(
            listOf(
                PresentationCredentialRequirement(
                    options = listOf(
                        listOf("mdl-id"),
                        listOf("photo-id"),
                    )
                )
            ),
            requirements,
        )
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("photo-id")) })
        assertFalse(WalletPresentationHandler.run { requirements.satisfiedBy(setOf("address")) })
    }

    @Test
    fun presentationRequirementsIgnoreOptionalSetsWhenNoRequiredSetExists() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("address"),
                credentialQuery("photo-id"),
            ),
            credentialSets = listOf(
                CredentialSetQuery(
                    required = false,
                    options = listOf(
                        listOf("address"),
                        listOf("photo-id"),
                    ),
                ),
            )
        )

        val requirements = WalletPresentationHandler.run { query.requiredCredentialRequirements() }

        assertEquals(emptyList(), requirements)
        assertTrue(WalletPresentationHandler.run { requirements.satisfiedBy(emptySet()) })
    }

    @Test
    fun presentationSelectionRequiresAtLeastOneCredentialOption() {
        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                emptyList<PresentationCredentialSelection>().requireValidPresentationCredentialSelection()
            }
        }
    }

    @Test
    fun submitPresentationRequiresKnownPreviewHandle() = runTest {
        val error = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.submitPresentation(
                wallet = Wallet(id = "wallet-without-preview"),
                request = SubmitPresentationRequest(
                    previewHandle = PresentationPreviewHandle("unknown-preview"),
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                    ),
                ),
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            )
        }

        assertEquals(
            PreviewSessionFailureReason.UNKNOWN,
            error.reason,
        )
    }

    @Test
    fun rejectPresentationRequiresKnownPreviewHandle() = runTest {
        val error = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.rejectPresentation(
                wallet = Wallet(id = "wallet-without-rejection-preview"),
                request = RejectPresentationRequest(
                    previewHandle = PresentationPreviewHandle("unknown-preview"),
                    errorCode = "access_denied",
                ),
            )
        }

        assertEquals(
            PreviewSessionFailureReason.UNKNOWN,
            error.reason,
        )
    }

    @Test
    fun sameRequestUrlPreviewsRemainBoundToTheirOwnResolvedContent() = runTest {
        val wallet = Wallet(id = "same-request-url")
        val requestUrl = Url("openid4vp://authorize?request_uri=https%3A%2F%2Fverifier.example%2Fmutable.jwt")
        val first = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(requestUrl, state = "first"),
        )
        val second = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(requestUrl, state = "second"),
        )

        assertEquals(
            "first",
            WalletPresentationHandler.consumePreviewedAuthorizationRequest(wallet, first)
                .resolvedAuthorizationRequest.authorizationRequest.state,
        )
        assertEquals(
            "second",
            WalletPresentationHandler.consumePreviewedAuthorizationRequest(wallet, second)
                .resolvedAuthorizationRequest.authorizationRequest.state,
        )
    }

    @Test
    fun invalidTransactionDataCanBeReturnedAfterPreviewInteraction() = runTest {
        val wallet = Wallet(
            id = "wallet-invalid-transaction-data",
            staticKey = JWKKey.generate(KeyType.Ed25519),
        )
        val requestUrl = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
            transactionData = listOf(
                buildJsonObject {
                    put("type", "unsupported")
                    put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
                }.toString().encodeToByteArray().encodeToBase64Url(),
            ),
        ).toHttpUrl()

        val preview = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(requestUrl),
            transactionDataTypeRegistry = TransactionDataTypeRegistry("payment"),
        )

        val invalid = assertIs<PreviewPresentationResult.Invalid>(preview)
        assertEquals(WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA, invalid.error.code)
        assertEquals("redirect_uri:https://verifier.example/callback", invalid.authorizationRequest.clientId)

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.submitPresentation(
                wallet = wallet,
                request = SubmitPresentationRequest(
                    previewHandle = invalid.handle,
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                    ),
                ),
                transactionDataTypeRegistry = TransactionDataTypeRegistry("payment"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.rejectPresentation(
                wallet = wallet,
                request = RejectPresentationRequest(invalid.handle, errorCode = "access_denied"),
            )
        }

        val rejection = WalletPresentationHandler.rejectPresentation(
            wallet = wallet,
            request = RejectPresentationRequest(
                previewHandle = invalid.handle,
                errorDescription = "Sensitive local validation details",
            ),
        )

        assertEquals(
            "https://verifier.example/callback#error=invalid_transaction_data&state=state-123",
            rejection.getUrl,
        )
    }

    @Test
    fun buildVpTokenReResolvesAndRejectsInvalidRequest() = runTest {
        val wallet = Wallet(
            id = "wallet-build-reresolve-invalid",
            staticKey = JWKKey.generate(KeyType.Ed25519),
        )
        val authorizationRequest = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
            transactionData = listOf(
                buildJsonObject {
                    put("type", "unsupported")
                    put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
                }.toString().encodeToByteArray().encodeToBase64Url(),
            ),
        )
        val requestUrl = authorizationRequest.toHttpUrl()
        var resolveCalls = 0

        val error = assertFailsWith<IllegalStateException> {
            WalletPresentationHandler.buildVpToken(
                wallet = wallet,
                request = BuildVpTokenRequest(
                    requestUrl = requestUrl,
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                    ),
                ),
                transactionDataTypeRegistry = TransactionDataTypeRegistry("payment"),
                resolveAuthorizationRequest = {
                    resolveCalls += 1
                    ResolvedAuthorizationRequest.Plain(authorizationRequest)
                },
            )
        }

        assertEquals(1, resolveCalls)
        assertTrue(error.message!!.contains("invalid_transaction_data"))
    }

    @Test
    fun sendAuthorizationResponseReResolvesAndRejectsInvalidRequest() = runTest {
        val wallet = Wallet(
            id = "wallet-send-reresolve-invalid",
            staticKey = JWKKey.generate(KeyType.Ed25519),
        )
        val authorizationRequest = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
            transactionData = listOf(
                buildJsonObject {
                    put("type", "unsupported")
                    put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
                }.toString().encodeToByteArray().encodeToBase64Url(),
            ),
        )
        val requestUrl = authorizationRequest.toHttpUrl()
        var resolveCalls = 0

        val error = assertFailsWith<IllegalStateException> {
            WalletPresentationHandler.sendAuthorizationResponse(
                wallet = wallet,
                request = SendAuthorizationResponseRequest(
                    requestUrl = requestUrl,
                    vpToken = """{"pid":"unused"}""",
                ),
                transactionDataTypeRegistry = TransactionDataTypeRegistry("payment"),
                resolveAuthorizationRequest = {
                    resolveCalls += 1
                    ResolvedAuthorizationRequest.Plain(authorizationRequest)
                },
            )
        }

        assertEquals(1, resolveCalls)
        assertTrue(error.message!!.contains("invalid_transaction_data"))
    }

    @Test
    fun unavailableTransactionCredentialCanBeReturnedAfterPreviewInteraction() = runTest {
        val wallet = Wallet(
            id = "wallet-unavailable-transaction-credential",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val requestUrl = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
            transactionData = listOf(
                buildJsonObject {
                    put("type", "payment")
                    put("credential_ids", buildJsonArray { add(JsonPrimitive("pid")) })
                }.toString().encodeToByteArray().encodeToBase64Url(),
            ),
        ).toHttpUrl()

        val preview = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(requestUrl),
            transactionDataTypeRegistry = TransactionDataTypeRegistry("payment"),
        )

        val invalid = assertIs<PreviewPresentationResult.Invalid>(preview)
        assertEquals(WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA, invalid.error.code)

        val rejection = WalletPresentationHandler.rejectPresentation(
            wallet = wallet,
            request = RejectPresentationRequest(invalid.handle),
        )

        assertEquals(
            "https://verifier.example/callback#error=invalid_transaction_data&state=state-123",
            rejection.getUrl,
        )
    }

    @Test
    fun unavailableRequestedCredentialCanBeReturnedAfterPreviewInteraction() = runTest {
        val wallet = Wallet(
            id = "wallet-unavailable-presentation-credential",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val requestUrl = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
        ).toHttpUrl()

        val preview = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(requestUrl),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        )

        val invalid = assertIs<PreviewPresentationResult.Invalid>(preview)
        assertEquals(WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED, invalid.error.code)

        val rejection = WalletPresentationHandler.rejectPresentation(
            wallet = wallet,
            request = RejectPresentationRequest(invalid.handle),
        )

        assertEquals(
            "https://verifier.example/callback#error=access_denied&state=state-123",
            rejection.getUrl,
        )
    }

    @Test
    fun unsupportedDcqlConstraintIsNotPresentedAsCredentialUnavailable() = runTest {
        val wallet = Wallet(
            id = "wallet-unsupported-dcql",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val requestUrl = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    credentialQuery("pid").copy(
                        trustedAuthorities = listOf(
                            TrustedAuthoritiesQuery(
                                type = TrustedAuthorityType.AKI,
                                values = listOf("authority-key-id"),
                            )
                        )
                    )
                )
            ),
        ).toHttpUrl()

        assertFailsWith<UnsupportedDcqlConstraintException> {
            WalletPresentationHandler.previewPresentation(
                wallet = wallet,
                request = PreviewPresentationRequest(requestUrl),
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            )
        }
    }

    @Test
    fun mutableRequestUriContentRemainsBoundToReviewedPreview() = runTest {
        val wallet = Wallet(
            id = "mutable-request-uri",
            credentialStores = listOf(InMemoryCredentialStore()),
            staticKey = JWKKey.generate(KeyType.Ed25519),
        )
        val requestUrl = Url("openid4vp://authorize?request_uri=https%3A%2F%2Fverifier.example%2Fmutable.jwt")
        var currentState = "reviewed"
        var resolutionCalls = 0

        val preview = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(requestUrl),
            executionKey = assertNotNull(wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))).let { { it } },
            onEvent = {},
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            resolveAuthorizationRequest = { resolvedUrl ->
                assertEquals(requestUrl, resolvedUrl)
                resolutionCalls += 1
                resolvedPreviewRequest(currentState)
            },
        )
        currentState = "mutated"
        val invalidPreview = assertIs<PreviewPresentationResult.Invalid>(preview)

        val result = WalletPresentationHandler.rejectPresentation(
            wallet,
            RejectPresentationRequest(invalidPreview.handle),
        )

        assertEquals("reviewed", invalidPreview.authorizationRequest.state)
        assertEquals(
            "https://verifier.example/callback#error=${invalidPreview.error.code.code}&state=reviewed",
            result.getUrl,
        )
        assertEquals(1, resolutionCalls)
    }

    @Test
    fun presentationPreviewIsWalletBoundAndDismissalDiscardsIt() = runTest {
        val wallet = Wallet(id = "presentation-owner")
        val otherWallet = Wallet(id = "presentation-other")
        val handle = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(Url("openid4vp://authorize"), state = "owned"),
        )

        val crossWallet = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.discardPreview(otherWallet, handle)
        }
        assertEquals(PreviewSessionFailureReason.WRONG_WALLET, crossWallet.reason)

        WalletPresentationHandler.discardPreview(wallet, handle)
        val discarded = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.consumePreviewedAuthorizationRequest(wallet, handle)
        }
        assertEquals(PreviewSessionFailureReason.DISCARDED, discarded.reason)
    }

    @Test
    fun rejectConsumesExactlySelectedPresentationPreview() = runTest {
        val wallet = Wallet(id = "reject-selected-preview")
        val first = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(Url("openid4vp://first"), state = "first"),
        )
        val second = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(Url("openid4vp://second"), state = "second"),
        )

        val result = WalletPresentationHandler.rejectPresentation(
            wallet,
            RejectPresentationRequest(previewHandle = first),
        )

        assertEquals("https://verifier.example/callback#error=access_denied&state=first", result.getUrl)
        val consumed = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.rejectPresentation(
                wallet,
                RejectPresentationRequest(previewHandle = first),
            )
        }
        assertEquals(PreviewSessionFailureReason.CONSUMED, consumed.reason)
        assertEquals(
            "second",
            WalletPresentationHandler.consumePreviewedAuthorizationRequest(wallet, second)
                .resolvedAuthorizationRequest.authorizationRequest.state,
        )
    }

    @Test
    fun submitConsumesPreviewEvenWhenLaterPresentationWorkFails() = runTest {
        val wallet = Wallet(id = "submit-consumes")
        val handle = WalletPresentationHandler.rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = readyPreview(Url("openid4vp://submit"), state = "submit"),
        )
        val request = SubmitPresentationRequest(
            previewHandle = handle,
            selectedCredentialOptions = listOf(
                PresentationCredentialSelection(queryId = "pid", credentialId = "credential"),
            ),
        )

        assertFailsWith<IllegalStateException> {
            WalletPresentationHandler.submitPresentation(
                wallet,
                request,
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            )
        }
        val consumed = assertFailsWith<PreviewSessionException> {
            WalletPresentationHandler.submitPresentation(
                wallet,
                request,
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            )
        }
        assertEquals(PreviewSessionFailureReason.CONSUMED, consumed.reason)
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun resolvedRequest(state: String): ResolvedAuthorizationRequest =
        ResolvedAuthorizationRequest.Plain(
            AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                redirectUri = "https://verifier.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
                state = state,
            )
        )

    private fun readyPreview(requestUrl: Url, state: String, keyId: String = "preview-key") =
        WalletPresentationHandler.PreviewedPresentation.Ready(
            requestUrl = requestUrl,
            resolvedAuthorizationRequest = resolvedRequest(state),
            keyId = keyId,
        )

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun resolvedPreviewRequest(state: String): ResolvedAuthorizationRequest =
        ResolvedAuthorizationRequest.Plain(
            AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                redirectUri = "https://verifier.example/callback",
                responseMode = OpenID4VPResponseMode.FRAGMENT,
                state = state,
                dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
            )
        )

    @Test
    fun presentationSelectionAllowsMultipleCredentialsForOneQueryWhenMatched() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
            PresentationCredentialSelection(queryId = "pid", credentialId = "cred-2"),
        )
        val matched = matchResult(
            "pid" to "cred-1",
            "pid" to "cred-2",
            query = { queryId -> credentialQuery(queryId, multiple = true) },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(selections)
        }

        assertEquals(listOf("cred-1", "cred-2"), selected.getValue("pid").map { it.credential.id })
    }

    @Test
    fun presentationSelectionRejectsMultipleCredentialsForNonMultipleQuery() {
        val matched = matchResult(
            "pid" to "cred-1",
            "pid" to "cred-2",
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1"),
                        PresentationCredentialSelection(queryId = "pid", credentialId = "cred-2"),
                    )
                )
            }
        }
    }

    @Test
    fun presentationSelectionRejectsBlankQueryOrCredentialIds() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "pid", credentialId = " "),
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                selections.requireValidPresentationCredentialSelection()
            }
        }
    }

    @Test
    fun presentationSelectionAllowsOneCredentialPerQuery() {
        val selections = listOf(
            PresentationCredentialSelection(queryId = "identity", credentialId = "cred-1"),
            PresentationCredentialSelection(queryId = "age", credentialId = "cred-1"),
        )

        WalletPresentationHandler.run {
            selections.requireValidPresentationCredentialSelection()
        }
    }

    @Test
    fun presentationSelectionRejectsUnknownQueryId() {
        val matched = matchResult("pid" to "cred-1")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(PresentationCredentialSelection(queryId = "unknown", credentialId = "cred-1"))
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.unknown"),
                    ),
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matchResult(
                    "pid" to "cred-1",
                    "pid" to "cred-2",
                    selectedDisclosures = mapOf("$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada"))),
                ).selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-2", path = "$.given_name"),
                    ),
                )
            }
        }

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matchResult("pid" to "cred-1").selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.given_name"),
                    ),
                )
            }
        }
    }

    @Test
    fun presentationSelectionRejectsUnknownCredentialIdForKnownQuery() {
        val matched = matchResult("pid" to "cred-1")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "missing-cred"))
                )
            }
        }
    }

    @Test
    fun presentationSelectionAllowsOneMatchedOptionalOnlyCredentialOption() {
        val matched = matchResult(
            "address" to "cred-address",
            "photo-id" to "cred-photo",
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                listOf(PresentationCredentialSelection(queryId = "photo-id", credentialId = "cred-photo"))
            )
        }

        assertEquals(setOf("photo-id"), selected.keys)
        assertEquals(listOf("cred-photo"), selected.getValue("photo-id").map { it.credential.id })
    }

    @Test
    fun disclosureSelectionKeepsRequiredClaimsWhenFilteringDisclosures() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                    ),
                )
            },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = listOf(
                    PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                ),
            )
        }

        assertEquals(
            setOf(givenName, ageOver18),
            selected.getValue("pid").single().selectedDisclosures.orEmpty().keys,
        )
    }

    @Test
    fun disclosureSelectionFiltersOptionalSelectivelyDisclosableClaimsOnly() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val vct = claimPath("$", "vct")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
                vct to JsonPrimitive("https://issuer.example/pid"),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                        ClaimsQuery(id = "vct", pathStrings = listOf("$", "vct")),
                    ),
                    claimSets = listOf(
                        listOf("given_name", "vct"),
                        listOf("age_over_18", "vct"),
                    ),
                )
            },
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = listOf(
                    PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                ),
            )
        }

        val disclosures = selected.getValue("pid").single().selectedDisclosures.orEmpty()
        assertEquals(setOf(givenName, vct), disclosures.keys)
    }

    @Test
    fun presentationPreviewIncludesOptionalClaimSetDisclosuresWhenMatcherSelectedMinimalSet() {
        val givenName = claimPath("$", "given_name")
        val familyName = claimPath("$", "family_name")
        val birthDate = claimPath("$", "birth_date")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                familyName to DcqlDisclosure("family_name", JsonPrimitive("Lovelace")),
            ),
            credentialDisclosures = listOf(
                DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                DcqlDisclosure("family_name", JsonPrimitive("Lovelace")),
                DcqlDisclosure("birth_date", JsonPrimitive("1815-12-10")),
            ),
            query = ::optionalBirthDateCredentialQuery,
        )

        val disclosures = WalletPresentationHandler.run {
            matched.getValue("pid").single().toPresentationDisclosures()
        }

        assertEquals(listOf(givenName, familyName, birthDate), disclosures.map { it.path })
        assertEquals(listOf(true, true, false), disclosures.map { it.required })
        assertEquals(listOf(false, false, true), disclosures.map { it.selectable })
    }

    @Test
    fun disclosureSelectionCanIncludeOptionalClaimSetDisclosureWhenMatcherSelectedMinimalSet() {
        val givenName = claimPath("$", "given_name")
        val familyName = claimPath("$", "family_name")
        val birthDate = claimPath("$", "birth_date")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                familyName to DcqlDisclosure("family_name", JsonPrimitive("Lovelace")),
            ),
            credentialDisclosures = listOf(
                DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                DcqlDisclosure("family_name", JsonPrimitive("Lovelace")),
                DcqlDisclosure("birth_date", JsonPrimitive("1815-12-10")),
            ),
            query = ::optionalBirthDateCredentialQuery,
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = listOf(
                    PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = birthDate),
                ),
            )
        }

        assertEquals(
            setOf(givenName, familyName, birthDate),
            selected.getValue("pid").single().selectedDisclosures.orEmpty().keys,
        )
    }

    @Test
    fun disclosureSelectionOmitsSelectivelyDisclosableClaimsWhenNoClaimsAreRequested() {
        val givenName = claimPath("$", "given_name")
        val vct = claimPath("$", "vct")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                vct to JsonPrimitive("https://issuer.example/pid"),
            ),
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                selectedDisclosureOptions = emptyList(),
            )
        }

        val disclosures = selected.getValue("pid").single().selectedDisclosures.orEmpty()
        assertEquals(setOf(vct), disclosures.keys)
    }

    @Test
    fun disclosureSelectionRejectsOptionalSubsetThatDoesNotSatisfyClaimSets() {
        val givenName = claimPath("$", "given_name")
        val ageOver18 = claimPath("$", "age_over_18")
        val portrait = claimPath("$", "portrait")
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                givenName to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                ageOver18 to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
                portrait to DcqlDisclosure("portrait", JsonPrimitive("image")),
            ),
            query = { queryId ->
                credentialQuery(
                    queryId,
                    claims = listOf(
                        ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                        ClaimsQuery(id = "age_over_18", pathStrings = listOf("$", "age_over_18")),
                        ClaimsQuery(id = "portrait", pathStrings = listOf("$", "portrait")),
                    ),
                    claimSets = listOf(
                        listOf("given_name", "age_over_18"),
                        listOf("given_name", "portrait"),
                    ),
                )
            },
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = givenName),
                    ),
                )
            }
        }
    }

    @Test
    fun disclosureSelectionRejectsUnknownOrNonSelectiveDisclosurePaths() {
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                "$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                "$.vct" to JsonPrimitive("https://issuer.example/pid"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                matched.selectCredentialOptions(
                    selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
                    selectedDisclosureOptions = listOf(
                        PresentationDisclosureSelection(queryId = "pid", credentialId = "cred-1", path = "$.vct"),
                    ),
                )
            }
        }
    }

    @Test
    fun disclosureSelectionPreservesLegacyAllDisclosuresWhenUnset() {
        val matched = matchResult(
            "pid" to "cred-1",
            selectedDisclosures = mapOf(
                "$.given_name" to DcqlDisclosure("given_name", JsonPrimitive("Ada")),
                "$.age_over_18" to DcqlDisclosure("age_over_18", JsonPrimitive(true)),
            ),
        )

        val selected = WalletPresentationHandler.run {
            matched.selectCredentialOptions(
                selectedCredentialOptions = listOf(PresentationCredentialSelection(queryId = "pid", credentialId = "cred-1")),
            )
        }

        assertEquals(
            setOf("$.given_name", "$.age_over_18"),
            selected.getValue("pid").single().selectedDisclosures.orEmpty().keys,
        )
    }

    @Test
    fun transactionDataSelectionRejectsMoreThanOneReferencedCredential() {
        val transactionData = transactionDataForCredentialIds("identity", "payment")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                validateSelectedTransactionDataCredentials(
                    transactionData = listOf(transactionData),
                    selectedQueryIds = setOf("identity", "payment"),
                )
            }
        }

        WalletPresentationHandler.run {
            validateSelectedTransactionDataCredentials(
                transactionData = listOf(transactionData),
                selectedQueryIds = setOf("identity"),
            )
        }
    }

    @Test
    fun transactionDataSelectionRejectsMissingReferencedCredential() {
        val transactionData = transactionDataForCredentialIds("identity", "payment")

        assertFailsWith<IllegalArgumentException> {
            WalletPresentationHandler.run {
                validateSelectedTransactionDataCredentials(
                    transactionData = listOf(transactionData),
                    selectedQueryIds = setOf("profile"),
                )
            }
        }
    }

    @Test
    fun statelessPreviewBindsRequestedSigningKey() = runTest {
        val defaultKey = JWKKey.generate(KeyType.Ed25519)
        val signingKey = JWKKey.generate(KeyType.secp256k1)
        val keyStore = InMemoryKeyStore().also {
            it.addKey(defaultKey)
            it.addKey(signingKey)
        }
        val credentialStore = InMemoryCredentialStore().also {
            it.addCredential(
                StoredCredential(
                    id = "credential",
                    credential = W3C11(
                        credentialData = buildJsonObject {
                            put("credentialSubject", buildJsonObject { put("given_name", "Ada") })
                        },
                        issuer = "https://issuer.example",
                        subject = "did:example:holder",
                        signature = JwtCredentialSignature(
                            "signature",
                            buildJsonObject {},
                        ),
                        signed = "issuer.jwt.signature",
                    ),
                )
            )
        }
        val wallet = Wallet(
            id = "wallet-stateless-preview-key",
            keyStores = listOf(keyStore),
            credentialStores = listOf(credentialStore),
        )
        val authorizationRequest = AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            redirectUri = "https://verifier.example/callback",
            responseMode = OpenID4VPResponseMode.FRAGMENT,
            nonce = "nonce",
            state = "state-123",
            dcqlQuery = DcqlQuery(credentials = listOf(credentialQuery("pid"))),
        )
        val requestUrl = authorizationRequest.toHttpUrl()

        val preview = WalletPresentationHandler.previewPresentationStateless(
            wallet = wallet,
            request = PreviewPresentationRequest(
                requestUrl = requestUrl,
                keyId = signingKey.getKeyId(),
            ),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            resolveAuthorizationRequest = {
                ResolvedAuthorizationRequest.Plain(authorizationRequest)
            },
        )

        val ready = assertIs<StatelessPreviewPresentationResult.Ready>(preview)
        assertEquals(signingKey.getKeyId(), ready.keyId)
        assertEquals("credential", ready.credentialOptions.single().credentialId)
    }

    @Test
    fun isolatedPreviewRejectsUnknownPreRegisteredVerifierWithoutTrust() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = Wallet(id = "wallet-isolated-trust-missing", staticKey = JWKKey.generate(KeyType.Ed25519))

        val failure = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            WalletPresentationHandler.previewPresentationStateless(
                wallet = wallet,
                request = PreviewPresentationRequest(requestUrl = requestUrl),
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                clientIdTrustConfiguration = ClientIdTrustConfiguration(),
            )
        }

        assertEquals(ClientIdError.PreRegisteredClientNotFound("verifier2"), failure.clientIdError)
    }

    @Test
    fun isolatedFlowUsesConfiguredPreRegisteredClientTrust() = runTest {
        val verifierKey = JWKKey.generate(KeyType.Ed25519)
        val requestUrl = preRegisteredRequestUrl(verifierKey)
        val wallet = Wallet(
            id = "wallet-isolated-trust",
            staticKey = JWKKey.generate(KeyType.Ed25519),
            credentialStores = listOf(InMemoryCredentialStore()),
        )
        val trust = ClientIdTrustConfiguration(
            preRegisteredClients = mapOf(
                "verifier2" to ClientMetadata(
                    jwks = ClientMetadata.Jwks(listOf(verifierKey.getPublicKey().exportJWKObject())),
                ),
            ),
        )

        val preview = WalletPresentationHandler.previewPresentationStateless(
            wallet = wallet,
            request = PreviewPresentationRequest(requestUrl = requestUrl),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            clientIdTrustConfiguration = trust,
        )
        val invalid = assertIs<StatelessPreviewPresentationResult.Invalid>(preview)
        assertEquals("verifier2", invalid.authorizationRequest.clientId)

        val buildWithoutTrust = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            WalletPresentationHandler.buildVpToken(
                wallet = wallet,
                request = BuildVpTokenRequest(
                    requestUrl = requestUrl,
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "missing"),
                    ),
                ),
                clientIdTrustConfiguration = ClientIdTrustConfiguration(),
            )
        }
        assertEquals(ClientIdError.PreRegisteredClientNotFound("verifier2"), buildWithoutTrust.clientIdError)

        val buildWithTrust = assertFailsWith<RequiredCredentialUnavailableException> {
            WalletPresentationHandler.buildVpToken(
                wallet = wallet,
                request = BuildVpTokenRequest(
                    requestUrl = requestUrl,
                    selectedCredentialOptions = listOf(
                        PresentationCredentialSelection(queryId = "pid", credentialId = "missing"),
                    ),
                ),
                clientIdTrustConfiguration = trust,
            )
        }
        assertTrue(
            buildWithTrust.queryIds.contains("pid"),
            "Trusted build should pass client-id checks and report the missing requested credential",
        )

        val rejectWithoutTrust = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            WalletPresentationHandler.rejectPresentationByRequestUrl(
                request = RejectPresentationByRequestUrlRequest(requestUrl = requestUrl),
                clientIdTrustConfiguration = ClientIdTrustConfiguration(),
            )
        }
        assertEquals(ClientIdError.PreRegisteredClientNotFound("verifier2"), rejectWithoutTrust.clientIdError)

        // Reject with trust passes client-id validation; transmission to response_uri may still fail locally.
        val rejectWithTrustFailure = runCatching {
            WalletPresentationHandler.rejectPresentationByRequestUrl(
                request = RejectPresentationByRequestUrlRequest(
                    requestUrl = requestUrl,
                    errorCode = "access_denied",
                ),
                clientIdTrustConfiguration = trust,
            )
        }.exceptionOrNull()
        assertFalse(
            rejectWithTrustFailure is AuthorizationRequestResolver.SignedAuthorizationRequestValidationException &&
                rejectWithTrustFailure.clientIdError == ClientIdError.PreRegisteredClientNotFound("verifier2"),
            "Trusted reject must not fail with PreRegisteredClientNotFound",
        )
    }

    private suspend fun preRegisteredRequestUrl(verifierKey: Key): Url {
        val requestObject = verifierKey.signJws(
            buildJsonObject {
                put("client_id", "verifier2")
                put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
                put("nonce", "nonce-123")
                put("response_type", "vp_token")
                put("response_mode", "direct_post")
                put("response_uri", "https://verifier.example/direct-post")
                put(
                    "dcql_query",
                    buildJsonObject {
                        put(
                            "credentials",
                            buildJsonArray {
                                add(buildJsonObject {
                                    put("id", "pid")
                                    put("format", "jwt_vc_json")
                                    put("meta", buildJsonObject {})
                                })
                            },
                        )
                    },
                )
            }.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        return URLBuilder("openid4vp://authorize").apply {
            parameters.append("client_id", "verifier2")
            parameters.append("request", requestObject)
        }.build()
    }

    private fun AuthorizationRequest.toHttpUrl(): Url =
        Url("https://verifier.example/authorize?client_id=${clientId.orEmpty()}&nonce=${nonce.orEmpty()}")

    private fun credentialQuery(
        id: String,
        multiple: Boolean = false,
        claims: List<ClaimsQuery>? = null,
        claimSets: List<List<String>>? = null,
    ): CredentialQuery =
        CredentialQuery(
            id = id,
            format = CredentialFormat.JWT_VC_JSON,
            multiple = multiple,
            meta = NoMeta,
            claims = claims,
            claimSets = claimSets,
        )

    private fun optionalBirthDateCredentialQuery(queryId: String): CredentialQuery =
        credentialQuery(
            queryId,
            claims = listOf(
                ClaimsQuery(id = "given_name", pathStrings = listOf("$", "given_name")),
                ClaimsQuery(id = "family_name", pathStrings = listOf("$", "family_name")),
                ClaimsQuery(id = "birth_date", pathStrings = listOf("$", "birth_date")),
            ),
            claimSets = listOf(
                listOf("given_name", "family_name"),
                listOf("given_name", "family_name", "birth_date"),
            ),
        )

    private fun claimPath(vararg parts: String): String =
        parts.map { JsonPrimitive(it) }.joinToString(".")

    private fun transactionDataForCredentialIds(vararg credentialIds: String): String =
        buildJsonObject {
            put("type", "org.waltid.transaction-data.payment-authorization")
            put("credential_ids", buildJsonArray {
                credentialIds.forEach { add(JsonPrimitive(it)) }
            })
            put("amount", "42.00")
            put("currency", "EUR")
            put("payee", "ACME Corp")
        }.toString().encodeToByteArray().encodeToBase64Url()

    private fun matchResult(
        vararg options: Pair<String, String>,
        selectedDisclosures: Map<String, Any>? = null,
        credentialDisclosures: List<DcqlDisclosure>? = null,
        query: (String) -> CredentialQuery = ::credentialQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> =
        options
            .groupBy(
                keySelector = { (queryId, _) -> queryId },
                valueTransform = { (queryId, credentialId) ->
                    DcqlMatcher.DcqlMatchResult(
                        credential = RawDcqlCredential(
                            id = credentialId,
                            format = CredentialFormat.JWT_VC_JSON.id.first(),
                            data = JsonObject(emptyMap()),
                            disclosures = credentialDisclosures,
                        ),
                        selectedDisclosures = selectedDisclosures,
                        originalQuery = query(queryId),
                    )
                }
            )
}
