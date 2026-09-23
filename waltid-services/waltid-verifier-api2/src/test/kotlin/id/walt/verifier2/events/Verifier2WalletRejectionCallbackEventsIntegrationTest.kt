package id.walt.verifier2.events

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.server.application.Application
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Verifier2WalletRejectionCallbackEventsIntegrationTest {

    private val dcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "openbadge",
                format = CredentialFormat.JWT_VC_JSON,
                meta = JwtVcJsonMeta(
                    typeValues = listOf(listOf("VerifiableCredential", "OpenBadgeCredential"))
                ),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("name"))
                )
            )
        )
    )

    private fun verificationSessionSetup(notifications: KtorSessionNotifications): VerificationSessionSetup =
        CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = dcqlQuery,
                notifications = notifications,
            )
        )

    @Test
    fun walletRejectionEmitsWalletErrorResponseReceived() {
        val host = "127.0.0.1"
        val port = 17141
        Verifier2WebhookRecorder().start().use { webhook ->
            E2ETest(host, port, true).testBlock(
                features = listOf(OSSVerifier2FeatureCatalog),
                preload = {
                    ConfigManager.preloadConfig(
                        "verifier-service", OSSVerifier2ServiceConfig(
                            clientId = "verifier2",
                            clientMetadata = ClientMetadata(clientName = "Verifier2"),
                            urlPrefix = "http://$host:$port/verification-session",
                            urlHost = "openid4vp://authorize",
                        )
                    )
                },
                init = {
                    DidService.apply {
                        registerResolver(LocalResolver())
                        updateResolversForMethods()
                    }
                },
                module = Application::verifierModule
            ) {
                val http = testHttpClient()
                val created = testAndReturn("Create verification session") {
                    http.post("/verification-session/create") {
                        setBody(verificationSessionSetup(webhook.notifications()))
                    }.body<VerificationSessionCreationResponse>()
                }
                val sessionId = created.sessionId

                val authorizationRequest = testAndReturn("Fetch authorization request") {
                    http.get("/verification-session/$sessionId/request")
                        .body<AuthorizationRequest>()
                }

                val rejection = testAndReturn("Wallet rejects presentation") {
                    WalletPresentFunctionality2.walletRejectHandling(
                        authorizationRequest = authorizationRequest,
                        error = WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED,
                        errorDescription = "User denied",
                    )
                }
                test("Wallet rejection is transmitted") {
                    assertTrue(rejection.isSuccess)
                    assertEquals(true, rejection.getOrThrow().transmissionSuccess)
                }

                val session = testAndReturn("View rejected session") {
                    http.get("/verification-session/$sessionId/info")
                        .body<Verification2Session>()
                }
                test("Session is failed after wallet rejection") {
                    assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
                    val failure = session.failure as? SessionFailure.WalletErrorResponse
                    kotlin.test.assertNotNull(failure)
                    assertEquals("access_denied", failure.error)
                    assertEquals("User denied", failure.errorDescription)
                }

                test("Emit wallet_error_response_received callback") {
                    webhook.assertReceivedInOrder(
                        sessionId,
                        Verifier2WebhookRecorder.walletRejectionEvents,
                    )
                    webhook.assertDoesNotContain(sessionId, SessionEvent.parsed_presentation_available)
                    webhook.assertDoesNotContain(sessionId, SessionEvent.credential_policy_results_available)
                }
            }
        }
    }
}
