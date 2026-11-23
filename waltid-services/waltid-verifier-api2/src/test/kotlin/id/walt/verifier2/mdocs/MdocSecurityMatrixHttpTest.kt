@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.verifier2.mdocs

import kotlin.test.assertNull
import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.cose.coseCompliantCbor
import id.walt.cose.CoseCertificate
import id.walt.credentials.formats.MdocsCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionFailure
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.data.VerificationSessionUsage
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.useBoundVerifierAddress
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.parseQueryString
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.application.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.delay
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.hours

/** Real issuer and device signatures, actual session creation and direct-post/info HTTP routes. */
class MdocSecurityMatrixHttpTest {
    private val namespace = "org.iso.23220.1"
    private val docType = "org.iso.23220.photoid.1"
    private val schema = JsonSchemaPolicy(schema = Json.decodeFromString<JsonObject>("""
        {"type":"object","required":["$namespace"],"properties":{
          "$namespace":{"type":"object","required":["age_over_18"],
            "properties":{"age_over_18":{"type":"boolean"}}}}}
    """))

    private fun data(age: JsonElement? = JsonPrimitive(true)) = buildJsonObject {
        put("docType", docType)
        putJsonObject(namespace) {
            age?.let { put("age_over_18", it) }
            put("portrait", "test-portrait")
        }
    }

    private fun query(fields: List<String>) = CredentialQuery(
        id = "mdoc", format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta(doctypeValue = docType),
        claims = fields.map { ClaimsQuery(pathStrings = listOf(namespace, it)) },
    )

    private data class Presentation(val id: String, val request: AuthorizationRequest, val form: String)

    private suspend fun form(
        request: AuthorizationRequest,
        credential: MdocsCredential,
        disclosed: List<String>,
        queryIds: List<String> = listOf("mdoc"),
        mutate: suspend (DeviceResponse) -> DeviceResponse = { it },
    ): String {
        val match = DcqlMatcher.matchCredential(query(disclosed), RawDcqlCredential(
            id = "credential", format = credential.format, data = credential.credentialData, originalCredential = credential,
        )).getOrThrow()
        val presented = MdocPresenter.presentMdoc(
            credential, match, request, restoreMdlHolderCrypto2Key("security-matrix-holder"),
            TransactionDataTypeRegistry(emptySet()),
        ).content
        val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(presented))
        val token = buildJsonObject {
            val encoded = Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(mutate(response)))
            queryIds.forEach { put(it, JsonArray(listOf(JsonPrimitive(encoded)))) }
        }
        return listOf("state" to requireNotNull(request.state), "vp_token" to token.toString()).formUrlEncode()
    }

    private suspend fun prepare(
        http: HttpClient,
        credential: MdocsCredential,
        disclosed: List<String> = listOf("age_over_18"),
        usage: VerificationSessionUsage = VerificationSessionUsage.REUSABLE,
        dcql: DcqlQuery = DcqlQuery(listOf(query(listOf("age_over_18")))),
        queryIds: List<String> = listOf("mdoc"),
        vcPolicies: VCPolicyList = VCPolicyList(listOf(schema)),
        mutate: suspend (DeviceResponse) -> DeviceResponse = { it },
    ): Presentation {
        val setup: VerificationSessionSetup = CrossDeviceFlowSetup(core = GeneralFlowConfig(
            dcqlQuery = dcql,
            sessionUsage = usage,
            policies = Verification2Session.DefinedVerificationPolicies(vc_policies = vcPolicies),
        ))
        val created = http.post("/verification-session/create") { setBody(setup) }.body<VerificationSessionCreationResponse>()
        val request = http.get("/verification-session/${created.sessionId}/request").body<AuthorizationRequest>()
        return Presentation(created.sessionId, request, form(request, credential, disclosed, queryIds, mutate))
    }

    private suspend fun submit(http: HttpClient, presentation: Presentation, success: Boolean, vpPolicy: String? = null): Verification2Session {
        val response = http.post("/verification-session/${presentation.id}/response") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(presentation.form)
        }
        assertEquals(if (success) HttpStatusCode.OK else HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        val session = http.get("/verification-session/${presentation.id}/info").body<Verification2Session>()
        assertEquals(if (success) Verification2Session.VerificationSessionStatus.SUCCESSFUL else Verification2Session.VerificationSessionStatus.FAILED,
            session.status, session.failure.toString())
        if (success) {
            assertTrue(assertNotNull(session.policyResults).overallSuccess)
            assertNotNull(session.presentedCredentials?.get("mdoc")?.singleOrNull())
        }
        vpPolicy?.let {
            val failure = assertIs<SessionFailure.PresentationValidation>(session.failure)
            assertTrue(it in failure.failedPolicies.getValue("mdoc"), failure.toString())
        }
        return session
    }

    private fun withVerifier(block: suspend E2ETest.() -> Unit) {
        E2ETest("127.0.0.1", 0, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = { ConfigManager.preloadConfig("verifier-service", OSSVerifier2ServiceConfig(
                clientId = null, clientMetadata = ClientMetadata(clientName = "Security matrix"),
                urlPrefix = "http://127.0.0.1/verification-session", urlHost = "openid4vp://authorize",
            )) },
            init = {},
            module = Application::verifierModule,
        ) {
            useBoundVerifierAddress()
            block()
        }
    }

    private suspend fun mso(response: DeviceResponse, change: (MobileSecurityObject) -> MobileSecurityObject): DeviceResponse {
        val document = response.documents!!.single()
        return response.copy(documents = arrayOf(resignMdlFixtureMso(document, change(document.issuerSigned.decodeMobileSecurityObject()))))
    }

    private fun corrupt(bytes: ByteArray) = bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

    @Test
    fun signedMdocSecurityMatrix() = withVerifier {
        val http = testHttpClient()
        val credential = mdlFixtureCredential(data().toString())
        for (age in listOf(true, false)) test("genuine boolean $age remains typed") {
            val result = submit(http, prepare(http, mdlFixtureCredential(data(JsonPrimitive(age)).toString())), true)
            assertEquals(JsonPrimitive(age), result.presentedCredentials!!.getValue("mdoc").single().credentialData[namespace]!!.jsonObject["age_over_18"])
        }
        for (value in listOf<JsonElement>(JsonPrimitive("false"), JsonPrimitive(1), JsonArray(emptyList()), JsonObject(emptyMap()))) {
            test("authentic wrong claim type $value fails the schema") {
                val result = submit(http, prepare(http, mdlFixtureCredential(data(value).toString())), false)
                assertIs<SessionFailure.VcPolicyViolations>(result.failure)
            }
        }
        test("missing requested claim cannot substitute portrait") {
            val result = submit(http, prepare(http, mdlFixtureCredential(data(null).toString()), disclosed = listOf("portrait")), false)
            assertIs<SessionFailure.DcqlFulfillment>(result.failure)
        }
        test("authentic extra portrait is rejected") {
            val result = submit(http, prepare(http, credential, disclosed = listOf("age_over_18", "portrait")), false)
            assertIs<SessionFailure.DcqlFulfillment>(result.failure)
            assertNull(result.presentedRawData)
            assertNull(result.presentedPresentations)
            assertNull(result.presentedCredentials)
            assertNull(result.policyResults)
        }
        test("issuer signature corruption is rejected") {
            submit(http, prepare(http, credential) { response ->
                val document = response.documents!!.single()
                val auth = document.issuerSigned.issuerAuth
                response.copy(documents = arrayOf(document.copy(issuerSigned = IssuerSigned.fromIssuerSignedLists(
                    requireNotNull(document.issuerSigned.namespaces), auth.copy(signature = corrupt(auth.signature)),
                ))))
            }, false, "mso_mdoc/issuer_auth")
        }
        test("malformed issuer certificate returns a client error") {
            submit(http, prepare(http, credential) { response ->
                val document = response.documents!!.single()
                val auth = document.issuerSigned.issuerAuth
                val certificate = auth.unprotected.x5chain!!.first()
                response.copy(documents = arrayOf(document.copy(issuerSigned = IssuerSigned.fromIssuerSignedLists(
                    requireNotNull(document.issuerSigned.namespaces),
                    auth.copy(unprotected = auth.unprotected.copy(x5chain = listOf(CoseCertificate(corrupt(certificate.rawBytes))))),
                ))))
            }, false)
        }
        test("device signature corruption is rejected") {
            submit(http, prepare(http, credential) { response ->
                val document = response.documents!!.single()
                val signed = requireNotNull(document.deviceSigned)
                val signature = requireNotNull(signed.deviceAuth.deviceSignature)
                response.copy(documents = arrayOf(document.copy(deviceSigned = signed.copy(deviceAuth = signed.deviceAuth.copy(
                    deviceSignature = signature.copy(signature = corrupt(signature.signature)),
                )))))
            }, false, "mso_mdoc/device-auth")
        }
        test("issuer-signed claim mutation fails digest validation") {
            submit(http, prepare(http, credential) { response ->
                val document = response.documents!!.single()
                val items = document.issuerSigned.namespaces!!.mapValues { (_, list) ->
                    list.entries.map { wrapped -> wrapped.value.copy(elementValue = CborString("tampered")) }
                }
                response.copy(documents = arrayOf(document.copy(issuerSigned = IssuerSigned.fromIssuerSignedItems(
                    items, document.issuerSigned.issuerAuth,
                ))))
            }, false, "mso_mdoc/issuer_signed_integrity")
        }
        test("validly signed MSO docType cannot relabel the document") {
            submit(http, prepare(http, credential) { mso(it) { m -> m.copy(docType = "another.document") } },
                false, "mso_mdoc/issuer_signed_integrity")
        }
        test("validly signed expired MSO is rejected") {
            // Keep signed <= validFrom < validUntil inside the DSC validity window, then let it expire.
            delay(2.seconds)
            val expired = submit(http, prepare(http, credential) { mso(it) { m -> m.copy(
                validityInfo = m.validityInfo.copy(validUntil = m.validityInfo.validFrom + 1.seconds),
            ) } }, false, "mso_mdoc/mso")
            assertEquals(setOf("mso_mdoc/mso"),
                assertIs<SessionFailure.PresentationValidation>(expired.failure).failedPolicies.getValue("mdoc").keys)
        }
        test("validly signed future MSO is rejected") {
            submit(http, prepare(http, credential) { mso(it) { m -> m.copy(validityInfo = m.validityInfo.copy(
                validFrom = Clock.System.now() + 1.hours, validUntil = Clock.System.now() + 2.hours,
            )) } }, false, "mso_mdoc/mso")
        }
        test("validly signed unsupported MSO version is rejected") {
            submit(http, prepare(http, credential) { mso(it) { m -> m.copy(version = "9.0") } }, false, "mso_mdoc/mso")
        }
        test("response status cannot report an error with valid documents") {
            submit(http, prepare(http, credential) { it.copy(status = 1u) }, false)
        }
        test("unsupported DeviceResponse version is rejected") {
            submit(http, prepare(http, credential) { it.copy(version = "9.0") }, false)
        }
        test("extra document cannot be silently trimmed") {
            submit(http, prepare(http, credential) { it.copy(documents = arrayOf(it.documents!!.single(), it.documents!!.single())) }, false)
        }
    }

    @Test
    fun sameSignedCredentialCanSatisfyTwoQueriesWithTheirCombinedDisclosures() = withVerifier {
        test("same signed credential under two query ids") {
            val http = testHttpClient()
            val credential = mdlFixtureCredential(data().toString())
            val queries = DcqlQuery(listOf(query(listOf("age_over_18")), query(listOf("portrait")).copy(id = "photo")))
            val presentation = prepare(http, credential, disclosed = listOf("age_over_18", "portrait"),
                dcql = queries, queryIds = listOf("mdoc", "photo"))
            val result = submit(http, presentation, true)
            assertEquals(setOf("mdoc", "photo"), result.presentedCredentials!!.keys)
        }
    }

    @Test
    fun separateSignedPresentationsSatisfyTheirOwnClaimAlternativeInEitherQueryOrder() = withVerifier {
        val http = testHttpClient()
        val credential = mdlFixtureCredential(data().toString())
        val choice = query(listOf("age_over_18", "age_over_21")).let { query ->
            query.copy(claims = query.claims!!.mapIndexed { index, claim -> claim.copy(id = "claim$index") },
                claimSets = listOf(listOf("claim0"), listOf("claim1")))
        }
        val photo = query(listOf("portrait")).copy(id = "photo")
        for (queries in listOf(listOf(choice, photo), listOf(photo, choice))) test("separate signed responses with query order ${queries.map { it.id }}") {
            val presentation = prepare(http, credential, dcql = DcqlQuery(queries), vcPolicies = VCPolicyList(emptyList()))
            val second = form(presentation.request, credential, listOf("portrait"), queryIds = listOf("photo"))
            val tokens = listOf(presentation.form, second).flatMap { encoded ->
                Json.parseToJsonElement(requireNotNull(parseQueryString(encoded)["vp_token"])).jsonObject.entries
            }.associate { it.key to it.value }
            val combined = listOf("state" to requireNotNull(presentation.request.state), "vp_token" to JsonObject(tokens).toString()).formUrlEncode()
            val result = submit(http, presentation.copy(form = combined), true)
            assertEquals(setOf("mdoc", "photo"), result.presentedCredentials!!.keys)
        }
    }

    @Test
    fun combinedDisclosuresMustUseOneAlternativePerQueryAcrossPresentations() = withVerifier {
        val http = testHttpClient()
        val claims = data().getValue(namespace).jsonObject + ("age_over_21" to JsonPrimitive(true))
        val credential = mdlFixtureCredential(JsonObject(data() + (namespace to JsonObject(claims))).toString())
        fun choice(id: String, names: List<String>) = query(names).let { query ->
            query.copy(id = id, claims = query.claims!!.mapIndexed { index, claim -> claim.copy(id = "claim$index") },
                claimSets = listOf(listOf("claim0"), listOf("claim1")))
        }
        val first = choice("mdoc", listOf("age_over_18", "age_over_21"))
        val second = choice("photo", listOf("age_over_21", "portrait"))
        for (queries in listOf(listOf(first, second), listOf(second, first))) test("combined alternatives ${queries.map { it.id }}") {
            // Each response alone can be covered by one choice from each query. All three fields
            // together cannot be covered by the two choices the request permits.
            val presentation = prepare(http, credential, disclosed = listOf("age_over_18", "age_over_21"),
                dcql = DcqlQuery(queries), vcPolicies = VCPolicyList(emptyList()))
            val other = form(presentation.request, credential, listOf("age_over_21", "portrait"), queryIds = listOf("photo"))
            val tokens = listOf(presentation.form, other).flatMap { encoded ->
                Json.parseToJsonElement(requireNotNull(parseQueryString(encoded)["vp_token"])).jsonObject.entries
            }.associate { it.key to it.value }
            val combined = listOf("state" to requireNotNull(presentation.request.state), "vp_token" to JsonObject(tokens).toString()).formUrlEncode()
            val failed = submit(http, presentation.copy(form = combined), false)
            assertNull(failed.presentedCredentials)
        }
    }

    @Test
    fun failedSignedPresentationFollowsTheConfiguredSessionMode() = withVerifier {
        val http = testHttpClient()
        val credential = mdlFixtureCredential(data().toString())
        for (mode in VerificationSessionUsage.entries) test("signed failure followed by correction follows $mode") {
            val presentation = prepare(http, credential, usage = mode) { response ->
                val document = response.documents!!.single()
                val auth = document.issuerSigned.issuerAuth
                response.copy(documents = arrayOf(document.copy(issuerSigned = IssuerSigned.fromIssuerSignedLists(
                    requireNotNull(document.issuerSigned.namespaces), auth.copy(signature = corrupt(auth.signature)),
                ))))
            }
            val failed = submit(http, presentation, false, "mso_mdoc/issuer_auth")
            val corrected = presentation.copy(form = form(presentation.request, credential, listOf("age_over_18")))
            if (mode == VerificationSessionUsage.SINGLE_ATTEMPT) {
                val rejected = http.post("/verification-session/${presentation.id}/response") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(corrected.form)
                }
                assertEquals(HttpStatusCode.Conflict, rejected.status)
                val retained = http.get("/verification-session/${presentation.id}/info").body<Verification2Session>()
                assertEquals(failed.presentationId, retained.presentationId)
                assertEquals(Verification2Session.VerificationSessionStatus.FAILED, retained.status)
            } else {
                val successful = submit(http, corrected, true)
                assertNotEquals(failed.presentationId, successful.presentationId)
            }
        }
    }

    @Test
    fun signedPresentationReplayFollowsTheConfiguredSessionMode() = withVerifier {
        val http = testHttpClient()
        val credential = mdlFixtureCredential(data().toString())
        for (mode in VerificationSessionUsage.entries) test("signed replay follows $mode") {
            val presentation = prepare(http, credential, usage = mode)
            val first = submit(http, presentation, true)
            if (mode == VerificationSessionUsage.REUSABLE) {
                val second = submit(http, presentation, true)
                assertNotEquals(first.presentationId, second.presentationId)
            } else {
                val replay = http.post("/verification-session/${presentation.id}/response") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(presentation.form)
                }
                assertEquals(HttpStatusCode.Conflict, replay.status)
                val retained = http.get("/verification-session/${presentation.id}/info").body<Verification2Session>()
                assertEquals(first.presentationId, retained.presentationId)
                assertEquals(Json.encodeToJsonElement(first.policyResults), Json.encodeToJsonElement(retained.policyResults))
            }
        }
    }
}
