@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.mdocs

import id.walt.commons.config.statics.RunConfiguration
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.commons.web.plugins.configureStatusPages
import id.walt.crypto2.keys.EncodedKey
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.createAndSignDetached
import id.walt.cose.toCoseKey
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceEngagementSecurity
import id.walt.mdoc.proximity.MdocSessionHandover
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.RegexPolicy
import id.walt.policies2.vp.policies.DeviceAuthMdocVpPolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.verifier2.InMemoryVerificationSessionRepository
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.Verifier2Service
import id.walt.verifier2.data.ExternalReaderDeviceResponse
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionStatus
import id.walt.verifier2.events.Verifier2WebhookRecorder
import io.ktor.client.plugins.sse.SSE as ClientSSE
import io.ktor.client.plugins.sse.sseSession
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.Application
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.coroutines.withTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days

class ExternalReaderVerificationTest {
    private val configFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        System.clearProperty("config.file.verifier-service")
        System.clearProperty("config.file._features")
        System.clearProperty("config.file.web")
        FeatureManager.preclear()
        ConfigManager.preclear()
        configFiles.forEach { it.deleteIfExists() }
    }

    /** Exercise the same feature/config loader as service startup, including its default-off behavior. */
    private fun startup(enabled: Boolean = true) = runBlocking {
        fun config(name: String, body: String) {
            val file = Files.createTempFile("wal1612-$name", ".conf")
            configFiles.add(file)
            Files.writeString(file, body)
            System.setProperty("config.file.$name", file.toString())
        }
        config("verifier-service", """
            urlPrefix = "http://localhost/verification-session"
            urlHost = "openid4vp://authorize"
            sessionRetention = "3d"
        """.trimIndent())
        config("web", "humanReadableResultEncoding = false")
        config("_features", if (enabled) "enabledFeatures = [proximity-verifier]" else "enabledFeatures = []")
        ConfigManager.preclear()
        FeatureManager.preclear()
        FeatureManager.registerCatalog(object : ServiceFeatureCatalog {
            override val baseFeatures = listOf(CommonsFeatureCatalog.webFeature, OSSVerifier2FeatureCatalog.verifierService)
            override val optionalFeatures = listOf(CommonsFeatureCatalog.proximityVerifierFeature)
        })
        RunConfiguration.args = emptyArray()
        RunConfiguration.configArgs = emptyArray()
        FeatureManager.load(emptyMap())
        assertEquals(3.days, ConfigManager.getConfig<OSSVerifier2ServiceConfig>().sessionRetention)
    }

    private fun Application.installRoutes(repository: InMemoryVerificationSessionRepository, secure: Boolean = false) {
        install(ContentNegotiation) { json() }
        install(SSE)
        configureStatusPages()
        install(Authentication) {
            bearer {
                authenticate { credential -> credential.token.takeIf { it == "kiosk-test-token" }?.let(::UserIdPrincipal) }
            }
        }
        routing { authenticate(optional = !secure) { Verifier2Service.run { registerRoute(repository) } } }
    }

    private fun ApplicationTestBuilder.routes(repository: InMemoryVerificationSessionRepository = InMemoryVerificationSessionRepository(), secure: Boolean = false) {
        application { installRoutes(repository, secure) }
    }

    private suspend fun fixture(nfc: Boolean = false, familyName: String = "Doe", docType: String = "org.iso.18013.5.1.mDL", staticHandover: Boolean = false): ExternalReaderDeviceResponse {
        val holder = restoreMdlHolderCrypto2Key("external-reader-holder")
        val readerKey = (holder.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
        val engagement = DeviceEngagement("1.0", DeviceEngagementSecurity(1u, ByteStringWrapper(readerKey, coseCompliantCbor.encodeToByteArray(readerKey))))
        val engagementBytes = coseCompliantCbor.encodeToByteArray(engagement)
        val readerBytes = coseCompliantCbor.encodeToByteArray(readerKey)
        val select = byteArrayOf(1, 2, 3)
        val request = byteArrayOf(4, 5, 6)
        val handover = if (nfc) MdocSessionHandover.NfcConnection(ByteString(select), if (staticHandover) null else ByteString(request)) else MdocSessionHandover.Qr
        val transcript = handover.createTranscript(ByteString(engagementBytes), ByteString(readerBytes))
        val credential = mdlFixtureCredential(buildJsonObject {
            put("docType", docType)
            put("org.iso.18013.5.1", buildJsonObject { put("family_name", familyName) })
        }.toString())
        val namespaces = ByteStringWrapper(DeviceNameSpaces(emptyMap()))
        val signature = CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            detachedPayload = MdocCryptoHelper.buildDeviceAuthenticationBytes(transcript, credential.docType, namespaces),
            key = holder,
        )
        val document = credential.document.copy(deviceSigned = DeviceSigned(namespaces, DeviceAuth(deviceSignature = signature)))
        return ExternalReaderDeviceResponse(
            flowType = "iso_18013_5_external_reader",
            core = GeneralFlowConfig(),
            deviceResponse = Base64.encode(coseCompliantCbor.encodeToByteArray(DeviceResponse("1.0", arrayOf(document), status = 0u))),
            deviceEngagement = Base64.encode(engagementBytes),
            eReaderKey = Base64.encode(readerBytes),
            handover = if (nfc) buildJsonObject {
                put("type", "nfc")
                put("handoverSelect", Base64.encode(select))
                if (!staticHandover) put("handoverRequest", Base64.encode(request))
            } else JsonPrimitive("qr"),
        )
    }

    @Test
    fun `QR and NFC responses store a public session and captured bodies can be resubmitted`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() }; install(ClientSSE) }
        for (nfc in listOf(false, true)) {
            val request = fixture(nfc).let { it.copy(core = it.core.copy(reattemptable = nfc)) }
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(request) }
            assertEquals(HttpStatusCode.OK, response.status)
            val session = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
            assertTrue(assertNotNull(session.policyResults).overallSuccess)
            assertEquals("Doe", session.presentedCredentials!!.values.single().single()
                .credentialData.getValue("org.iso.18013.5.1").jsonObject.getValue("family_name").jsonPrimitive.content)
            assertEquals(Json.encodeToString(session), Json.encodeToString(http.get("/verification-session/${session.id}/info").body<Verification2Session>()))
            assertEquals(3.days, session.retentionDate!! - session.creationDate)
            val second = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(request) }.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.SUCCESSFUL, second.status)
            assertNotEquals(session.id, second.id)
        }
    }

    @Test
    fun `static NFC accepts absent or null request and binds the exact select bytes`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val request = fixture(nfc = true, staticHandover = true)
        Verifier2WebhookRecorder().start().use { webhook ->
            for (explicitNull in listOf(false, true)) {
                val handover = buildJsonObject {
                    request.handover.jsonObject.forEach { (key, value) -> put(key, value) }
                    if (explicitNull) put("handoverRequest", JsonNull)
                }
                val accepted = http.post(ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(request.copy(handover = handover, core = request.core.copy(notifications = webhook.notifications())))
                }
                assertEquals(HttpStatusCode.OK, accepted.status)
                val session = accepted.body<Verification2Session>()
                assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
                webhook.assertReceivedInOrder(session.id, listOf(SessionEvent.credential_policy_results_available))
            }
            val changed = request.copy(handover = buildJsonObject {
                put("type", "nfc")
                put("handoverSelect", "AQIE")
            }, core = request.core.copy(notifications = webhook.notifications()))
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(changed) }
            assertEquals(HttpStatusCode.OK, response.status)
            val failed = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.FAILED, failed.status)
            assertNull(failed.policyResults)
            webhook.assertReceivedInOrder(failed.id, listOf(SessionEvent.presentation_validation_failed))
        }
    }

    @Test
    fun `transcript changes fail before policies and policy failure has the policy event`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() }; install(ClientSSE) }
        val request = fixture(nfc = true)
        val engagement = coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(Base64.decode(request.deviceEngagement))
        val readerKey = coseCompliantCbor.decodeFromByteArray<CoseKey>(Base64.decode(request.eReaderKey))
        val changed = listOf(
            request.copy(deviceEngagement = Base64.encode(coseCompliantCbor.encodeToByteArray(engagement.copy(originInfos = emptyList(), version = "1.1")))),
            request.copy(eReaderKey = Base64.encode(coseCompliantCbor.encodeToByteArray(readerKey.copy(kid = byteArrayOf(7))))),
            request.copy(handover = buildJsonObject { put("type", "nfc"); put("handoverSelect", "AQIE"); put("handoverRequest", "BAUG") }),
        )
        Verifier2WebhookRecorder().start().use { webhook ->
            for (body in changed) {
                val response = http.post(ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(body.copy(core = body.core.copy(notifications = webhook.notifications())))
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val session = response.body<Verification2Session>()
                assertEquals(VerificationSessionStatus.FAILED, session.status)
                assertNull(session.policyResults)
                assertEquals(HttpStatusCode.BadRequest, http.get("/verification-session/${session.id}/request").status)
                assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/${session.id}/response") {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("vp_token=cannot-reuse-this-session")
                }.status)
                webhook.assertReceivedInOrder(session.id, listOf(SessionEvent.presentation_validation_failed))
            }
            val policyRequest = request.copy(core = GeneralFlowConfig(
                policies = Verification2Session.DefinedVerificationPolicies(
                    vc_policies = VCPolicyList(listOf(RegexPolicy(path = "$['org.iso.18013.5.1'].family_name", regex = "Smith"))),
                ), notifications = webhook.notifications(),
            ))
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(policyRequest) }
            assertEquals(HttpStatusCode.OK, response.status)
            val session = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.FAILED, session.status)
            assertTrue(!assertNotNull(session.policyResults).overallSuccess)
            webhook.assertReceivedInOrder(session.id, listOf(SessionEvent.credential_policy_results_available))
        }
    }

    @Test
    fun `webhook receives exactly one public terminal update and public setup redacts credentials`() = runBlocking {
        startup()
        val server = embeddedServer(CIO, port = 0) { installRoutes(InMemoryVerificationSessionRepository()) }.start()
        val port = server.engine.resolvedConnectors().single().port
        val http = HttpClient(Java) {
            defaultRequest { url("http://127.0.0.1:$port") }
            install(ClientContentNegotiation) { json() }
            install(ClientSSE)
        }
        try {
        Verifier2WebhookRecorder().start().use { webhook ->
            val request = fixture().let { it.copy(core = it.core.copy(
                notifications = webhook.notifications(),
                policies = Verification2Session.DefinedVerificationPolicies(vp_policies = VPPolicyList(emptyList(), emptyList(), listOf(DeviceAuthMdocVpPolicy()))),
            )) }
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(request) }
            val session = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
            webhook.assertReceivedInOrder(session.id, listOf(SessionEvent.credential_policy_results_available))
            val update = webhook.awaitEvents(session.id).single()
            assertEquals(Json.encodeToJsonElement(session), update.session)
            val streamed = withTimeout(5.seconds) {
                val stream = http.sseSession("/verification-session/${session.id}/events")
                try {
                    val event = stream.incoming.filter { it.data != null && it.data != "{}" }.first()
                    Json.decodeFromString<KtorSessionUpdate>(requireNotNull(event.data))
                } finally {
                    stream.cancel()
                }
            }
            assertEquals(update, streamed)
        }
        } finally {
            http.close()
            server.stop(0, 1_000)
        }
    }

    @Test
    fun `malformed responses private reader keys and encrypted SessionData return 400 without sessions`() = testApplication {
        startup()
        val repository = InMemoryVerificationSessionRepository()
        routes(repository)
        val http = createClient { install(ClientContentNegotiation) { json() }; install(ClientSSE) }
        val request = fixture()
        val invalid = listOf(
            request.copy(deviceResponse = "not base64"),
            request.copy(deviceResponse = Base64.encode(coseCompliantCbor.encodeToByteArray(mapOf("data" to "encrypted", "status" to "0")))),
            request.copy(deviceResponse = Base64.encode(byteArrayOf(0xff.toByte()))),
            request.copy(eReaderKey = Base64.encode(coseCompliantCbor.encodeToByteArray(
                coseCompliantCbor.decodeFromByteArray<CoseKey>(Base64.decode(request.eReaderKey)).copy(d = byteArrayOf(1)),
            ))),
            request.copy(core = GeneralFlowConfig(sessionId = "must-not-exist")),
        )
        for (body in invalid) {
            assertEquals(HttpStatusCode.BadRequest, http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(body) }.status)
        }
        assertNull(repository.get("must-not-exist"))
        assertEquals(0, repository.size)
    }

    @Test
    fun `DCQL matches returned claims specific policies and retention overrides`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val base = fixture()
        val core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(listOf(CredentialQuery(
                id = "licence", format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
                claims = listOf(ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name"))),
            ))),
            retentionDuration = 2.hours,
            policies = Verification2Session.DefinedVerificationPolicies(specific_vc_policies = mapOf(
                "licence" to VCPolicyList(listOf(RegexPolicy(path = "$['org.iso.18013.5.1'].family_name", regex = "Doe"))),
            )),
        )
        val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(base.copy(core = core)) }
        assertEquals(HttpStatusCode.OK, response.status)
        val session = response.body<Verification2Session>()
        assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
        assertEquals(core.retentionDate, session.retentionDate)
        assertEquals(setOf("licence"), session.presentedCredentials!!.keys)
        assertTrue(session.policyResults!!.specificVcPolicies.getValue("licence").single().success)

        val missing = core.copy(dcqlQuery = core.dcqlQuery!!.let { query -> query.copy(credentials = query.credentials.map {
            it.copy(claims = listOf(ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "missing_claim"))))
        }) })
        val failed = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(base.copy(core = missing)) }
        assertEquals(HttpStatusCode.OK, failed.status)
        assertEquals(VerificationSessionStatus.FAILED, failed.body<Verification2Session>().status)
    }

    private fun combineResponses(vararg requests: ExternalReaderDeviceResponse): ExternalReaderDeviceResponse {
        val responses = requests.map { coseCompliantCbor.decodeFromByteArray<DeviceResponse>(Base64.decode(it.deviceResponse)) }
        return requests.first().copy(deviceResponse = Base64.encode(coseCompliantCbor.encodeToByteArray(
            responses.first().copy(documents = responses.flatMap { it.documents.orEmpty().toList() }.toTypedArray()),
        )))
    }

    @Test
    fun `extra document ids cannot overwrite queried credentials or bypass specific policies`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val body = combineResponses(fixture(), fixture(familyName = "Smith", docType = "org.example.extra")).copy(
            core = GeneralFlowConfig(
                dcqlQuery = DcqlQuery(listOf(CredentialQuery(
                    id = "unrequested_0", format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
                ))),
                policies = Verification2Session.DefinedVerificationPolicies(specific_vc_policies = mapOf(
                    "unrequested_0" to VCPolicyList(listOf(RegexPolicy(path = "$['org.iso.18013.5.1'].family_name", regex = "Smith"))),
                    "unrequested_1" to VCPolicyList(emptyList()),
                )),
            ),
        )
        val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(body) }
        assertEquals(HttpStatusCode.OK, response.status)
        val session = response.body<Verification2Session>()
        assertEquals(VerificationSessionStatus.FAILED, session.status)
        assertEquals(setOf("unrequested_0", "unrequested_2"), session.presentedCredentials!!.keys)
        assertEquals("Doe", session.presentedCredentials!!.getValue("unrequested_0").single()
            .credentialData.getValue("org.iso.18013.5.1").jsonObject.getValue("family_name").jsonPrimitive.content)
        assertTrue(!session.policyResults!!.specificVcPolicies.getValue("unrequested_0").single().success)
    }

    @Test
    fun `DCQL alternatives sharing a doctype match their complete claim constraints`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val names = listOf("Doe", "Smith")
        val query = DcqlQuery(
            credentials = names.map { name -> CredentialQuery(
                id = name.lowercase(), format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
                claims = listOf(ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name"), values = listOf(JsonPrimitive(name)))),
            ) },
            credentialSets = listOf(CredentialSetQuery(options = names.map { listOf(it.lowercase()) })),
        )
        for (name in names) {
            val request = fixture(familyName = name).copy(core = GeneralFlowConfig(dcqlQuery = query))
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(request) }
            assertEquals(HttpStatusCode.OK, response.status)
            val session = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
            assertEquals(setOf(name.lowercase()), session.presentedCredentials!!.keys)
        }
    }

    @Test
    fun `DCQL multiple selects one or all matching documents and preserves extras`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val body = combineResponses(fixture(), fixture(familyName = "Smith"))
        for (multiple in listOf(false, true)) {
            val request = body.copy(core = GeneralFlowConfig(dcqlQuery = DcqlQuery(listOf(CredentialQuery(
                id = "licences", format = CredentialFormat.MSO_MDOC, multiple = multiple,
                meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
            )))))
            val response = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(request) }
            assertEquals(HttpStatusCode.OK, response.status)
            val session = response.body<Verification2Session>()
            assertEquals(VerificationSessionStatus.SUCCESSFUL, session.status, session.failure.toString())
            assertEquals(if (multiple) 2 else 1, session.presentedCredentials!!.getValue("licences").size)
            assertEquals(2, session.presentedCredentials!!.values.sumOf { it.size })
        }
    }

    @Test
    fun `an invalid second document fails the whole response`() = testApplication {
        startup()
        routes()
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val base = fixture()
        val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(Base64.decode(base.deviceResponse))
        val document = response.documents!!.single()
        val signature = document.deviceSigned!!.deviceAuth.deviceSignature!!
        val invalid = document.copy(deviceSigned = document.deviceSigned!!.copy(
            deviceAuth = DeviceAuth(deviceSignature = signature.copy(signature = signature.signature.copyOf().apply {
                this[0] = (this[0].toInt() xor 1).toByte()
            })),
        ))
        val body = base.copy(deviceResponse = Base64.encode(coseCompliantCbor.encodeToByteArray(
            response.copy(documents = arrayOf(document, invalid)),
        )))
        val result = http.post(ENDPOINT) { contentType(ContentType.Application.Json); setBody(body) }
        assertEquals(HttpStatusCode.OK, result.status)
        val failed = result.body<Verification2Session>()
        assertEquals(VerificationSessionStatus.FAILED, failed.status)
        assertEquals(2, failed.presentedCredentials!!.values.single().size)
        assertNull(failed.policyResults)
    }

    @Test
    fun `session-management authentication rejects missing and incorrect credentials`() = testApplication {
        startup()
        val repository = InMemoryVerificationSessionRepository()
        routes(repository, secure = true)
        val http = createClient { install(ClientContentNegotiation) { json() } }
        val request = fixture()
        for (token in listOf(null, "wrong-token")) {
            val response = http.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                token?.let { bearerAuth(it) }
                setBody(request)
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
        assertEquals(0, repository.size)
        val accepted = http.post(ENDPOINT) {
            contentType(ContentType.Application.Json)
            bearerAuth("kiosk-test-token")
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals(1, repository.size)
    }

    @Test
    fun `feature is off by default`() = testApplication {
        startup(enabled = false)
        routes()
        assertEquals(HttpStatusCode.NotFound, client.post(ENDPOINT).status)
    }

    companion object {
        private const val ENDPOINT = "/verification-session/proximity/device-response"
    }
}
