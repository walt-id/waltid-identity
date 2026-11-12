package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.W3CExamples
import id.walt.credentials.formats.DigitalCredential
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsonSchemaRemotePolicyTest {

    @Test
    fun testOk() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = WALT_ID_SCHEMA_URL,
            schema = JsonSchemaTestFixtures.issuer,
            credential = CredentialParser.parseOnly(W3CExamples.waltidIssuedJoseSignedW3CCredential),
            shouldSucceed = true,
        )
    }

    @Test
    fun testFail() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = WALT_ID_SCHEMA_URL,
            schema = JsonSchemaTestFixtures.issuer,
            credential = CredentialParser.parseOnly(W3CExamples.joseSignedW3CCredential),
            shouldSucceed = false,
        )
    }
}

class JsonSchemaRemotePolicyTest2 {

    @Test
    fun testOk() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = OPEN_BADGE_SCHEMA_URL,
            schema = JsonSchemaTestFixtures.openBadge,
            credential = W3CExamples.compliantOpenBadgeCredential,
            shouldSucceed = true,
        )
    }

    @Test
    fun testFail() = runTest {
        runRemoteSchemaPolicyTest(
            schemaUrl = OPEN_BADGE_SCHEMA_URL,
            schema = JsonSchemaTestFixtures.openBadge,
            credential = W3CExamples.noncompliantOpenBadgeCredential,
            shouldSucceed = false,
        )
    }
}

private suspend fun runRemoteSchemaPolicyTest(
    schemaUrl: Url,
    schema: String,
    credential: DigitalCredential,
    shouldSucceed: Boolean,
) {
    DidService.minimalInit()

    val requests = mutableListOf<Url>()
    val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                requests += request.url
                respond(schema, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        install(ContentNegotiation) { json() }
    }
    val fetcher = WebDataFetcher.wrapping(client, "json-schema-policy-tests")
    try {
        val result = JsonSchemaPolicy(schemaUrl = schemaUrl).verify(credential, PolicyExecutionContext.Empty, fetcher)
        if (shouldSucceed) {
            assertTrue(result.isSuccess)
            assertEquals(Json.decodeFromString<JsonObject>(schema), result.getOrThrow().jsonObject["schema"])
        } else {
            assertIs<JsonSchemaVerificationException>(result.exceptionOrNull())
        }
        assertEquals(listOf(schemaUrl), requests)
    } finally {
        fetcher.close()
    }
}

private val WALT_ID_SCHEMA_URL = Url("https://schemas.example/issuer.json")
private val OPEN_BADGE_SCHEMA_URL = Url("https://schemas.example/open-badge.json")
