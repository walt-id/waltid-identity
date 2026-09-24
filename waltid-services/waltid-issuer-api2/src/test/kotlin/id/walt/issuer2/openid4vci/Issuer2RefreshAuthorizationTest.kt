package id.walt.issuer2.openid4vci

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.models.CredentialOfferCredential
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.models.MultiCredentialOfferCreateRequest
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.repository.openid4vci.ConfiguredAuthorizationCodeRepository
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.sdjwt.SDJwt
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class Issuer2RefreshAuthorizationTest {
    @AfterEach
    fun clear() = clearIssuer2TestEnvironment()

    @Test
    fun rejectedRefreshPreservesFullCredentialGrant() = verifyRejectedRefresh(initialSubset = false)

    @Test
    fun rejectedRefreshPreservesInitialCredentialSubset() = verifyRejectedRefresh(initialSubset = true)

    private fun verifyRejectedRefresh(initialSubset: Boolean) = testApplication {
        loadIssuer2ConfigFiles()
        val attestation = createIssuer2ClientAttestationTestMaterial()
        val config = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(clientAuthenticationConfig = attestation.clientAuthenticationConfig)
        val repository = ConfiguredIssuanceSessionRepository()
        val module = Issuer2Module(config, ConfigManager.getConfig(), ConfigManager.getConfig(), issuanceSessionRepository = repository)
        val authorizationCodes = Issuer2Module::class.java.getDeclaredField("authorizationCodeRepository").let {
            it.isAccessible = true
            it.get(module) as ConfiguredAuthorizationCodeRepository
        }
        application {
            install(ContentNegotiation) { json(issuer2TestJson) }
            installIssuer2AuthenticationForTests()
            configurePlugins()
            routing {
                module.managementController.register(this)
                module.openId4VciController.register(this)
            }
        }
        val client = apiClient()
        val wallet = Issuer2WalletFlowDriver(client)
        val a = Issuer2CredentialScenarios.identitySdJwt
        val b = Issuer2CredentialScenarios.isoMdl
        val selected = if (initialSubset) listOf(a) else listOf(a, b)
        val statuses = listOf(94567, 12345).map { index -> buildJsonObject {
            putJsonObject("status_list") {
                put("idx", index)
                put("uri", "https://status.example.com/list/1")
            }
        } }
        val offer = client.createCredentialOffer(MultiCredentialOfferCreateRequest(
            credentials = listOf(a, b).mapIndexed { index, scenario ->
                CredentialOfferCredential(scenario.profileId, CredentialOfferRuntimeOverrides(credentialStatus = statuses[index]))
            },
            authMethod = AuthenticationMethod.AUTHORIZED,
        ))
        val session = assertNotNull(repository.get(offer.offerId))
        val resolved = wallet.resolve(offer)
        assertTrue(resolved.issuerMetadata.credentialConfigurationsSupported.values.none { it.scope == "openid" })

        // Start at the persisted authorization-code boundary; no external login/browser is needed.
        val code = UUID.randomUUID().toString()
        authorizationCodes.save(DefaultAuthorizationCodeRecord(
            code = code, clientId = "issuer2-wallet-test", redirectUri = "https://wallet.example/callback",
            grantedScopes = setOf("openid", a.authorizationScope, b.authorizationScope), grantedAudience = emptySet(),
            session = DefaultSession(subject = offer.offerId, expiresAt = mapOf(TokenType.ACCESS_TOKEN to session.expiresAt)),
            expiresAt = session.expiresAt,
        ))
        val instanceKey = JWKKey.generate(KeyType.secp256r1)
        suspend fun token(parameters: Parameters): io.ktor.client.statement.HttpResponse {
            val headers = attestation.attestationAssembler.buildAttestationHeaders(
                instanceKey = instanceKey, clientId = "issuer2-wallet-test", audience = resolved.authorizationServerMetadata.issuer,
            )
            return client.submitForm(assertNotNull(resolved.authorizationServerMetadata.tokenEndpoint), parameters) {
                header(ClientAttestationHeaders.CLIENT_ATTESTATION, headers.attestationJwt)
                header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, headers.popJwt)
            }
        }
        val initialResponse = token(Parameters.build {
            append("grant_type", "authorization_code")
            append("client_id", "issuer2-wallet-test")
            append("redirect_uri", "https://wallet.example/callback")
            append("code", code)
            append("authorization_details", details(selected.map { it.credentialConfigurationId }))
        })
        assertEquals(HttpStatusCode.OK, initialResponse.status, initialResponse.bodyAsText())
        val initial = initialResponse.body<JsonObject>()
        val originalRefresh = initial["refresh_token"]!!.jsonPrimitive.content
        suspend fun refresh(refreshToken: String, scope: String? = null, configurations: List<String>? = null) = token(Parameters.build {
            append("grant_type", "refresh_token")
            append("client_id", "issuer2-wallet-test")
            append("refresh_token", refreshToken)
            scope?.let { append("scope", it) }
            configurations?.let { append("authorization_details", details(it)) }
        })

        val noCredentials = refresh(originalRefresh, scope = "openid")
        assertEquals(HttpStatusCode.BadRequest, noCredentials.status, noCredentials.bodyAsText())
        assertEquals("invalid_request", noCredentials.body<JsonObject>()["error"]!!.jsonPrimitive.content)
        if (initialSubset) {
            val outsideGrant = refresh(originalRefresh, configurations = listOf(b.credentialConfigurationId))
            assertEquals(HttpStatusCode.BadRequest, outsideGrant.status, outsideGrant.bodyAsText())
            assertEquals("invalid_request", outsideGrant.body<JsonObject>()["error"]!!.jsonPrimitive.content)
        }
        val correctedResponse = refresh(originalRefresh, scope = a.authorizationScope)
        assertEquals(HttpStatusCode.OK, correctedResponse.status, correctedResponse.bodyAsText())
        val corrected = correctedResponse.body<JsonObject>()
        val accessToken = corrected["access_token"]!!.jsonPrimitive.content
        assertEquals(listOf(a.credentialConfigurationId), accessToken.decodeJws().payload["authorization_details"]!!.jsonArray.map {
            it.jsonObject["credential_configuration_id"]!!.jsonPrimitive.content
        })
        val restoredResponse = refresh(corrected["refresh_token"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.OK, restoredResponse.status, restoredResponse.bodyAsText())
        assertEquals(selected.map { it.credentialConfigurationId }, restoredResponse.body<JsonObject>()["access_token"]!!.jsonPrimitive.content
            .decodeJws().payload["authorization_details"]!!.jsonArray.map { it.jsonObject["credential_configuration_id"]!!.jsonPrimitive.content })

        val deniedCredential = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(credentialRequest(b.credentialConfigurationId, wallet.buildJwtProofs(resolved.issuerMetadata, b.credentialConfigurationId)))
        }
        assertEquals(HttpStatusCode.BadRequest, deniedCredential.status, deniedCredential.bodyAsText())
        assertTrue(assertNotNull(repository.get(offer.offerId)).issuanceResults.isEmpty())
        val first = wallet.buildJwtProofs(resolved.issuerMetadata, a.credentialConfigurationId, includeDidInProof = false)
        val second = wallet.buildJwtProofs(resolved.issuerMetadata, a.credentialConfigurationId, includeDidInProof = false)
        val issued = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(credentialRequest(a.credentialConfigurationId, first.copy(jwt = first.jwt!! + second.jwt!!)))
        }
        assertEquals(HttpStatusCode.OK, issued.status, issued.bodyAsText())
        val copies = issued.body<JsonObject>().getValue("credentials").jsonArray
        assertEquals(2, copies.size)
        copies.forEach { copy ->
            val credential = copy.jsonObject.getValue("credential").jsonPrimitive.content
            assertEquals(statuses[0], SDJwt.parse(credential).fullPayload["status"])
        }
        val after = assertNotNull(repository.get(offer.offerId))
        assertEquals(session.issuanceRequests, after.issuanceRequests)
        assertEquals(session.issuanceRequests.take(selected.size).map { it.credentialIdentifier }, after.authorizedCredentialIdentifiers)
    }

    private fun details(ids: List<String>) = buildJsonArray {
        ids.forEach { id -> add(buildJsonObject {
            put("type", "openid_credential")
            put("credential_configuration_id", id)
        }) }
    }.toString()
}
