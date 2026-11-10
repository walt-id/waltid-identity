package id.walt.wallet2.handlers

import id.walt.wallet2.consent.PaymentConsentFixture
import id.walt.wallet2.data.StoredCredential
import id.walt.sdjwt.metadata.type.TypeMetadataResolver
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.WebDataFetcherManager
import id.walt.webdatafetching.config.AllowList
import id.walt.webdatafetching.config.RequestConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import id.walt.webdatafetching.config.url.UrlProtocols
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.ClaimDisplay
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplayBackgroundImage
import id.walt.openid4vci.metadata.issuer.CredentialDisplayLogo
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.issuer.IssuerLogo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WalletDisplayMetadataTest {
    @Test
    fun inheritedDisplayAndPathsSurvivePersistenceAndPinnedOfflineResolution() = runTest { withContext(Dispatchers.Default) {
        val fixture = PaymentConsentFixture.create()
        var requests = 0
        val http = HttpClient(MockEngine { request ->
            requests++
            respond(TypeMetadataFixture.documents.getValue(request.url.toString()))
        })
        try {
            val credential = fixture.credential(buildJsonObject {
                put("vct", "https://issuer.example/leaf")
                put("vct#integrity", TypeMetadataFixture.LEAF_PIN)
                put("accounts", buildJsonArray { add(buildJsonObject { put("iban", "DE123") }) })
            })
            val stored = StoredCredential("id", credential, "My label", Clock.System.now(), buildJsonObject { put("custom", "retained") })
                .withTypeMetadata(http)
            assertEquals("My label", stored.label)
            assertEquals(JsonPrimitive("retained"), stored.metadata?.get("custom"))
            assertEquals("Bank attestation", stored.metadata?.get("credentialDisplay")?.jsonArray?.single()?.jsonObject?.get("name")?.jsonPrimitive?.content)
            val claim = stored.metadata!!.getValue("credentialClaims").jsonArray.single().jsonObject
            assertEquals(listOf("accounts", "0", "iban"), claim.getValue("path").jsonArray.map { it.jsonPrimitive.content })
            assertEquals("IBAN", claim.getValue("display").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
            val restored = Json.decodeFromString(StoredCredential.serializer(), Json.encodeToString(StoredCredential.serializer(), stored))
            assertEquals(3, requests)
            val before = requests
            assertEquals(listOf("https://issuer.example/leaf", "https://types.example/sca-user", "https://types.example/sca"), restored.resolvedSdJwtVcTypes())
            assertEquals(before, requests)
            val snapshot = restored.metadata!!.getValue(TYPE_METADATA_SNAPSHOT).jsonObject
            val oldVersion = JsonObject(restored.metadata!! + (TYPE_METADATA_SNAPSHOT to JsonObject(snapshot + ("version" to JsonPrimitive(0)))))
            assertTrue(restored.copy(metadata = oldVersion).resolvedSdJwtVcTypes().isEmpty())
            val changedPin = fixture.credential(buildJsonObject {
                put("vct", "https://issuer.example/leaf")
                put("vct#integrity", id.walt.sdjwt.metadata.type.MetadataIntegrity.sha256("replacement document".encodeToByteArray()))
            })
            assertTrue(restored.copy(credential = changedPin).resolvedSdJwtVcTypes().isEmpty())
            assertEquals(before, requests)
            val forged = credential.copy(credentialData = JsonObject(credential.credentialData + ("vct" to JsonPrimitive("https://wrong.example/type"))))
            assertFailsWith<IllegalArgumentException> { restored.copy(credential = forged).resolvedSdJwtVcTypes() }
        } finally { http.close(); fixture.close() }
    } }


    @Test
    fun storedClaimMetadataRetainsArrayPaths() {
        val path = listOf(JsonPrimitive("namespace"), JsonPrimitive("items"), JsonNull, JsonPrimitive(0))
        val original = issuerMetadata()
        val configuration = original.credentialConfigurationsSupported.getValue("pid")
        val issuer = original.copy(credentialConfigurationsSupported = mapOf(
            "pid" to configuration.copy(credentialMetadata = CredentialMetadata(
                claims = listOf(ClaimDescription(path = path)),
            )),
        ))
        val stored = storedCredentialDisplayMetadata(issuer, "pid")!!
        assertEquals(
            JsonArray(path),
            stored.getValue("credentialClaims").jsonArray.single().jsonObject.getValue("path"),
        )
    }

    @Test
    fun contradictoryClaimDescriptionsAreLeftOutButDisplayIsKept() {
        val original = issuerMetadata()
        val configuration = original.credentialConfigurationsSupported.getValue("pid")
        val issuer = original.copy(credentialConfigurationsSupported = mapOf(
            "pid" to configuration.copy(credentialMetadata = configuration.credentialMetadata?.copy(claims = listOf(
                ClaimDescription(path = listOf(JsonPrimitive("nationalities"), JsonNull)),
                ClaimDescription(path = listOf(JsonPrimitive("nationalities"), JsonPrimitive(1))),
            ))),
        ))

        val stored = assertNotNull(storedCredentialDisplayMetadata(issuer, "pid"))

        assertNull(stored["credentialClaims"])
        assertNotNull(stored["credentialDisplay"])
        assertNotNull(stored["issuerDisplay"])
    }

    @Test
    fun injectedIssuanceTransportRetainsMetadataPolicyAndRequestConfiguration() = runTest { withContext(Dispatchers.Default) {
        val id = WebDataFetcherId.WALLET2_PAYMENT_METADATA.name
        val previous = WebDataFetcherManager.getConfigurationForId(id, null)
        val fixture = PaymentConsentFixture.create()
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            assertEquals("test-bank", request.headers["X-Metadata-Tenant"])
            respond("""{"vct":"${request.url}"}""")
        })
        suspend fun enrich(vct: String) = StoredCredential("policy", fixture.credential(buildJsonObject { put("vct", vct) }))
            .withTypeMetadata(client)
        try {
            WebDataFetcherManager.appendConfiguration(id, previous.copy(url = UrlConfiguration(), request = RequestConfiguration(headers = mapOf("X-Metadata-Tenant" to "test-bank"))))
            assertNull(enrich("http://metadata.example/type").metadata?.get(TYPE_METADATA_SNAPSHOT))
            assertTrue(requests.isEmpty())
            WebDataFetcherManager.appendConfiguration(id, previous.copy(url = UrlConfiguration(hosts = AllowList(whitelist = listOf("allowed.example")))))
            assertNull(enrich("https://blocked.example/type").metadata?.get(TYPE_METADATA_SNAPSHOT))
            assertTrue(requests.isEmpty())
            WebDataFetcherManager.appendConfiguration(id, previous.copy(
                url = UrlConfiguration(protocols = UrlProtocols(allowHttp = true)),
                request = RequestConfiguration(headers = mapOf("X-Metadata-Tenant" to "test-bank")),
            ))
            assertTrue(TYPE_METADATA_SNAPSHOT in enrich("http://metadata.example/type").metadata.orEmpty())
            assertEquals(listOf("http://metadata.example/type"), requests)
        } finally {
            WebDataFetcherManager.appendConfiguration(id, previous)
            client.close(); fixture.close()
        }
    } }

    @Test
    fun overlappingSelectorsProduceUniquePathsWithoutWeakeningRestrictionsOrDroppingOtherLabels() = runTest {
        val fixture = PaymentConsentFixture.create()
        val vct = "https://issuer.example/type"
        val metadata = """{"vct":"$vct","claims":[
            {"path":["accounts",0,"iban"],"display":[{"locale":"en","label":"First IBAN"}]},
            {"path":["accounts",null,"iban"],"mandatory":true,"sd":"always","display":[{"locale":"en","label":"IBAN"}]},
            {"path":["name"],"display":[{"locale":"en","label":"Full name"}]}
        ]}""".encodeToByteArray()
        try {
            val credential = fixture.credential(buildJsonObject {
                put("vct", vct); put("name", "Ada")
                put("accounts", buildJsonArray { add(buildJsonObject { put("iban", "DE123") }) })
            })
            val resolved = TypeMetadataResolver({ metadata }).resolve(vct)
            val claims = projectTypeMetadata(resolved, credential, null).getValue("credentialClaims").jsonArray
            assertEquals(listOf(listOf("accounts", "0", "iban"), listOf("name")), claims.map { it.jsonObject.getValue("path").jsonArray.map { it.jsonPrimitive.content } })
            assertEquals(listOf("First IBAN", "Full name"), claims.map { it.jsonObject.getValue("display").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content })
            assertEquals(JsonPrimitive(true), claims.first().jsonObject["mandatory"])
            assertEquals(JsonPrimitive("always"), claims.first().jsonObject["sd"])
            val conflicting = metadata.decodeToString().replace("\"path\":[\"accounts\",0,\"iban\"]", "\"path\":[\"accounts\",0,\"iban\"],\"sd\":\"never\"").encodeToByteArray()
            val incompatible = TypeMetadataResolver({ conflicting }).resolve(vct)
            assertFailsWith<IllegalArgumentException> { projectTypeMetadata(incompatible, credential, null) }
        } finally { fixture.close() }
    }

    @Test
    fun retainsClaimsWithoutArtworkAndPreservesOtherRequestMetadata() {
        val original = issuerMetadata()
        val configuration = original.credentialConfigurationsSupported.getValue("pid")
        val issuer = original.copy(display = null, credentialConfigurationsSupported = mapOf("pid" to configuration.copy(
            credentialMetadata = CredentialMetadata(claims = listOf(ClaimDescription(
                path = listOf("namespace.with.dots", "given_name"), display = listOf(ClaimDisplay(name = "First name", locale = "en")),
            ))),
        )))
        val request = kotlinx.serialization.json.buildJsonObject { put("custom", kotlinx.serialization.json.JsonPrimitive("kept")) }
        val metadata = storedCredentialDisplayMetadata(issuer, "pid", request)!!
        assertEquals(request["custom"], metadata["custom"])
        assertEquals("namespace.with.dots", metadata.getValue("credentialClaims").jsonArray.single().jsonObject.getValue("path").jsonArray.first().jsonPrimitive.content)
        assertNull(metadata["credentialDisplay"])
        assertNull(storedCredentialDisplayMetadata(issuer, "unknown"))
    }

    @Test
    fun persistsIssuerAndCredentialDisplayFields() {
        val metadata = storedCredentialDisplayMetadata(
            issuerMetadata = issuerMetadata(),
            credentialConfigurationId = "pid",
        )

        val issuer = metadata!!.jsonObject.getValue("issuerDisplay").jsonArray.single().jsonObject
        assertEquals("Example Issuer", issuer.getValue("name").jsonPrimitive.content)
        assertEquals("https://issuer.example/issuer.png", issuer.getValue("logo").jsonObject.getValue("uri").jsonPrimitive.content)

        val credential = metadata.jsonObject.getValue("credentialDisplay").jsonArray.single().jsonObject
        assertEquals("Personal ID", credential.getValue("name").jsonPrimitive.content)
        assertEquals("#12107c", credential.getValue("background_color").jsonPrimitive.content)
        assertEquals(
            "https://issuer.example/pid-bg.png",
            credential.getValue("background_image").jsonObject.getValue("uri").jsonPrimitive.content,
        )
        assertEquals("#FFFFFF", credential.getValue("text_color").jsonPrimitive.content)
        assertEquals("https://issuer.example/pid.png", credential.getValue("logo").jsonObject.getValue("uri").jsonPrimitive.content)
        assertEquals("PID logo", credential.getValue("logo").jsonObject.getValue("alt_text").jsonPrimitive.content)
    }

    @Test
    fun returnsNullWhenNoDisplayIsAdvertised() {
        val metadata = storedCredentialDisplayMetadata(
            issuerMetadata = CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "pid" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
            ),
            credentialConfigurationId = "pid",
        )
        assertNull(metadata)
    }

    private fun issuerMetadata() = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example",
        credentialEndpoint = "https://issuer.example/credential",
        display = listOf(
            IssuerDisplay(
                name = "Example Issuer",
                locale = "en",
                logo = IssuerLogo(uri = "https://issuer.example/issuer.png", altText = "Issuer logo"),
            ),
        ),
        credentialConfigurationsSupported = mapOf(
            "pid" to CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Personal ID",
                            locale = "en",
                            logo = CredentialDisplayLogo(
                                uri = "https://issuer.example/pid.png",
                                altText = "PID logo",
                            ),
                            description = "Government identity",
                            backgroundColor = "#12107c",
                            backgroundImage = CredentialDisplayBackgroundImage(
                                uri = "https://issuer.example/pid-bg.png",
                            ),
                            textColor = "#FFFFFF",
                        ),
                    ),
                ),
            ),
        ),
    )
}
