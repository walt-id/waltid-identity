package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.KeyManager
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.mdocs.HOLDER_SERIALIZED_KEY
import id.walt.verifier2.mdocs.mdlFixtureCredential
import id.walt.verifier2.mdocs.restoreMdlHolderCrypto2Key
import id.walt.verifier2.sdjwt.SD_JWT_VC_TEST_VCT
import id.walt.verifier2.sdjwt.issueSdJwtVcForHolder
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
import io.ktor.server.application.Application
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wallet that answers a query id with a valid credential that does not satisfy that query is rejected,
 * for SD-JWT VC and mso_mdoc, with the default VP policies (OpenID4VP 1.0 §8.6). A credential that satisfies
 * the query's claims, including a selectively disclosed one, is accepted.
 */
class DcqlQueryBindingVerifier2IntegrationTest {

    private val policies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy()))
    )

    private val holderDid = "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17"

    private fun sdJwtQuery(vct: String = SD_JWT_VC_TEST_VCT, givenNameValues: List<String>? = null) = DcqlQuery(
        listOf(
            CredentialQuery(
                id = "requested",
                format = CredentialFormat.DC_SD_JWT,
                meta = SdJwtVcMeta(vctValues = listOf(vct)),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("given_name"), values = givenNameValues?.map { JsonPrimitive(it) }),
                    // Selectively disclosable in the fixture, so it is matched from the presented disclosure.
                    ClaimsQuery(pathStrings = listOf("birthdate")),
                ),
            )
        )
    )

    /** The wallet selects against [walletQuery], which may differ from the query it was sent. */
    private suspend fun presentSdJwt(bootstrapUrl: Url, walletQuery: DcqlQuery) =
        issueSdJwtVcForHolder(holderDid).let { credential ->
            WalletPresentFunctionality2.walletPresentHandling(
                holderKey = KeyManager.resolveSerializedKey(HOLDER_SERIALIZED_KEY),
                holderDid = holderDid,
                presentationRequestUrl = bootstrapUrl,
                selectCredentialsForQuery = { select(walletQuery, credential) },
                holderPoliciesToRun = null,
                runPolicies = null,
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
            )
        }

    @Test
    fun `an SD-JWT VC of another vct is rejected`() {
        runPresentation(sdJwtQuery(vct = "https://issuer.example/other_credential")) { bootstrapUrl ->
            presentSdJwt(bootstrapUrl, sdJwtQuery())
        }
    }

    @Test
    fun `an SD-JWT VC with the requested claims is accepted`() {
        runPresentation(sdJwtQuery(), expectAccepted = true) { bootstrapUrl ->
            presentSdJwt(bootstrapUrl, sdJwtQuery())
        }
    }

    @Test
    fun `an SD-JWT VC with a claim value outside the requested values is rejected`() {
        runPresentation(sdJwtQuery(givenNameValues = listOf("Jane"))) { bootstrapUrl ->
            presentSdJwt(bootstrapUrl, sdJwtQuery())
        }
    }

    private val photoIdData = """
        {
            "org.iso.18013.5.1": {"family_name_unicode": "Doe", "given_name_unicode": "John", "issuing_country": "AT"},
            "org.iso.23220.photoid.1": {"person_id": "AT12345"},
            "docType": "org.iso.23220.photoid.1"
        }
    """

    private fun mdocQuery(doctype: String) = DcqlQuery(
        listOf(
            CredentialQuery(
                id = "requested",
                format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(doctypeValue = doctype),
                claims = listOf(ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name_unicode"))),
            )
        )
    )

    @Test
    fun `an mdoc of another doctype is rejected`() {
        runPresentation(mdocQuery("org.iso.18013.5.1.mDL")) { bootstrapUrl ->
            presentMdoc(bootstrapUrl, mdlFixtureCredential(photoIdData), mdocQuery("org.iso.23220.photoid.1"))
        }
    }

    @Test
    fun `an mdoc whose document docType differs from its MSO docType is rejected`() {
        // Document.docType is not issuer-signed; only the MSO's is (ISO/IEC 18013-5 §9.3.1).
        val photoId = mdlFixtureCredential(photoIdData)
        val relabelled = photoId.copy(
            docType = "org.iso.18013.5.1.mDL",
            credentialData = buildJsonObject {
                photoId.credentialData.forEach { (key, value) -> put(key, value) }
                put("docType", JsonPrimitive("org.iso.18013.5.1.mDL"))
            },
        )

        runPresentation(mdocQuery("org.iso.18013.5.1.mDL")) { bootstrapUrl ->
            presentMdoc(bootstrapUrl, relabelled, mdocQuery("org.iso.18013.5.1.mDL"))
        }
    }

    private suspend fun presentMdoc(bootstrapUrl: Url, credential: DigitalCredential, walletQuery: DcqlQuery) =
        restoreMdlHolderCrypto2Key("binding-mdoc-holder").let { holderKey ->
            WalletPresentFunctionality2.walletPresentHandling(
                holderKey = holderKey,
                holderDid = null,
                presentationRequestUrl = bootstrapUrl,
                selectCredentialsForQuery = { select(walletQuery, credential) },
                holderPoliciesToRun = null,
                runPolicies = null,
                transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                mdocHolderKeyResolver = { _, _ -> holderKey },
            )
        }

    private fun select(query: DcqlQuery, credential: DigitalCredential): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val dcqlCredential = RawDcqlCredential(
            id = "0",
            format = credential.format,
            data = credential.credentialData,
            originalCredential = credential,
            disclosures = (credential as? SelectivelyDisclosableVerifiableCredential)?.disclosures
                ?.map { DcqlDisclosure(it.name, it.value, it.location) },
        )
        return DcqlMatcher.match(query, listOf(dcqlCredential)).getOrThrow()
    }

    private fun runPresentation(
        dcqlQuery: DcqlQuery,
        expectAccepted: Boolean = false,
        present: suspend (bootstrapUrl: Url) -> Result<WalletPresentFunctionality2.WalletPresentResult>,
    ) {
        val host = "127.0.0.1"
        val port = freePort()
        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = null,
                        clientMetadata = ClientMetadata(clientName = "Verifier2"),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize"
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
            val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(dcqlQuery = dcqlQuery, policies = policies))
            val created = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(setup)
                }.body<VerificationSessionCreationResponse>()
            }

            val presentation = testAndReturn("Present with wallet") { present(created.bootstrapAuthorizationRequestUrl!!) }

            test("Verifier ${if (expectAccepted) "accepts" else "rejects"} the transmission") {
                assertTrue(presentation.isSuccess, presentation.exceptionOrNull()?.stackTraceToString())
                assertEquals(expectAccepted, presentation.getOrThrow().transmissionSuccess)
            }

            val session = testAndReturn("View presented session") {
                http.get("/verification-session/${created.sessionId}/info").body<Verification2Session>()
            }

            if (expectAccepted) {
                test("Session succeeded") {
                    assertEquals(Verification2Session.VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
                }
            } else {
                test("Session failed on DCQL fulfillment, before credential policies") {
                    assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
                    val failure = assertIs<SessionFailure.DcqlFulfillment>(session.failure, session.failure.toString())
                    assertEquals(setOf("requested"), failure.failure.missingQueryIds)
                    assertNull(session.policyResults)
                }
            }
        }
    }
}
