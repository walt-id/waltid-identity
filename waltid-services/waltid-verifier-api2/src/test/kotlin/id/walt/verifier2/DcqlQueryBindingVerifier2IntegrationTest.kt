package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.statuslist.codec.TokenStatusListCodec
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
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    @Test
    fun `default status policy fetches the list and rejects revoked credentials`() {
        for (value in listOf(0, 1)) {
            var statusToken = ""
            val fetches = AtomicInteger()
            runPresentation(sdJwtQuery(), expectAccepted = value == 0, expectStatusRejected = value != 0,
                statusToken = { fetches.incrementAndGet(); statusToken }) { bootstrapUrl ->
                val uri = ConfigManager.getConfig<OSSVerifier2ServiceConfig>().urlPrefix!!.substringBefore("/verification-session") + "/test-status-list"
                val credential = issueSdJwtVcForHolder(holderDid) { issuerKey, issuerDid ->
                    val now = Clock.System.now().epochSeconds
                    val payload = buildJsonObject {
                        put("iss", issuerDid); put("sub", uri); put("iat", now); put("exp", now + 3600)
                        putJsonObject("status_list") {
                            put("bits", 1)
                            put("lst", TokenStatusListCodec.encode(ByteArray(16_384).also { it[0] = value.toByte() }))
                        }
                    }
                    statusToken = CompactJws.sign(payload.toString().encodeToByteArray(), issuerKey, JwsAlgorithm.EDDSA,
                        buildJsonObject { put("typ", "statuslist+jwt"); put("kid", issuerDid) })
                    buildJsonObject { putJsonObject("status_list") { put("idx", 0); put("uri", uri) } }
                }
                presentSdJwt(bootstrapUrl, sdJwtQuery(), credential)
            }
            assertTrue(fetches.get() > 0)
        }
    }

    /** The wallet selects against [walletQuery], which may differ from the query it was sent. */
    private suspend fun presentSdJwt(bootstrapUrl: Url, walletQuery: DcqlQuery, supplied: DigitalCredential? = null) =
        (supplied ?: issueSdJwtVcForHolder(holderDid)).let { credential ->
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

        runPresentation(mdocQuery("org.iso.18013.5.1.mDL"), expectMdocIntegrityFailure = true) { bootstrapUrl ->
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
        expectStatusRejected: Boolean = false,
        statusToken: (() -> String)? = null,
        expectMdocIntegrityFailure: Boolean = false,
        present: suspend (bootstrapUrl: Url) -> Result<WalletPresentFunctionality2.WalletPresentResult>,
    ) {
        val host = "127.0.0.1"
        E2ETest(host, 0, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = null,
                        clientMetadata = ClientMetadata(clientName = "Verifier2"),
                        urlPrefix = "http://$host/verification-session",
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
            module = {
                verifierModule()
                if (statusToken != null) routing {
                    get("/test-status-list") { call.respondText(statusToken(), ContentType("application", "statuslist+jwt")) }
                }
            }
        ) {
            useBoundVerifierAddress()
            val http = testHttpClient()
            val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(dcqlQuery = dcqlQuery))
            val created = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(setup)
                }.body<VerificationSessionCreationResponse>()
            }

            test("Session creation installs signature and status defaults") {
                val session = http.get("/verification-session/${created.sessionId}/info").body<Verification2Session>()
                assertEquals(listOf("signature", "credential-status"), session.policies.vc_policies!!.policies.map { it.id })
            }

            if (expectAccepted) test("Malformed status policy arguments return HTTP 400") {
                val encoded = Json.encodeToJsonElement<VerificationSessionSetup>(setup).jsonObject
                for (argument in listOf("""{"value":-1}""", """{"discriminator":"unsupported"}""")) {
                    val policies = Json.parseToJsonElement("""{"vc_policies":[{"policy":"credential-status","argument":$argument}]}""")
                    val core = JsonObject(encoded.getValue("core_flow").jsonObject + ("policies" to policies))
                    val invalid = JsonObject(encoded + ("core_flow" to core))
                    assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/create") {
                        contentType(ContentType.Application.Json)
                        setBody(invalid.toString())
                    }.status)
                }
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
                    assertTrue(session.policyResults!!.vcPolicies.any { it.policy.id == "credential-status" && it.success })
                }
            } else if (expectStatusRejected) {
                test("Revoked credential fails the default status policy") {
                    assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
                    assertIs<SessionFailure.VcPolicyViolations>(session.failure)
                    assertTrue(session.policyResults!!.vcPolicies.any { it.policy.id == "credential-status" && !it.success })
                }
            } else {
                test("Session failed before credential policies") {
                    assertEquals(Verification2Session.VerificationSessionStatus.FAILED, session.status)
                    if (expectMdocIntegrityFailure) {
                        // Structural inconsistencies are rejected by the VP policy before DCQL matching.
                        val failure = assertIs<SessionFailure.PresentationValidation>(session.failure)
                        assertTrue("mso_mdoc/issuer_signed_integrity" in failure.failedPolicies.getValue("requested"))
                    } else {
                        val failure = assertIs<SessionFailure.DcqlFulfillment>(session.failure, session.failure.toString())
                        assertEquals(setOf("requested"), failure.failure.missingQueryIds)
                    }
                    assertNull(session.policyResults)
                }
            }
        }
    }
}
