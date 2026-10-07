package id.walt.wallet2.handlers

import id.walt.credentials.jsonld.loadW3cContextDocuments
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class W3cContextDocumentLoaderTest {
    private val legalPerson = "https://w3id.org/gaia-x/development#LegalPerson"
    private val contextUrl = "https://w3id.org/gaia-x/development"

    @Test
    fun loaderRequestsJsonLdAndExpandedTypeMatches() = runTest {
        var accept: String? = null
        var requestedUrl: String? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    accept = request.headers[HttpHeaders.Accept]
                    requestedUrl = request.url.toString()
                    respond(
                        content = """{ "@context": { "gx": "https://w3id.org/gaia-x/development#" } }""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/ld+json"),
                    )
                }
            }
        }
        val credential = RawDcqlCredential(
            id = "legal-person",
            format = "jwt_vc_json",
            data = Json.parseToJsonElement(
                """
                {
                  "@context": [
                    "https://www.w3.org/ns/credentials/v2",
                    "https://w3id.org/gaia-x/development#",
                    { "vcard": "http://www.w3.org/2006/vcard/ns#", "schema": "https://schema.org/" }
                  ],
                  "type": ["VerifiableCredential", "gx:LegalPerson"]
                }
                """.trimIndent()
            ).jsonObject,
        )
        val documents = client.loadW3cContextDocuments(listOf(credential))
        assertEquals(contextUrl, requestedUrl?.substringBefore('?'))
        assertTrue(accept.orEmpty().contains("application/ld+json"))

        val query = DcqlParser.parse(
            """
            {
              "credentials": [{
                "id": "credential_1",
                "format": "jwt_vc_json",
                "meta": { "type_values": [["VerifiableCredential", "$legalPerson"]] }
              }]
            }
            """.trimIndent()
        ).getOrThrow()
        val matched = DcqlMatcher.match(query, listOf(credential), contextDocuments = documents).getOrThrow()
        assertEquals(listOf("legal-person"), matched.getValue("credential_1").map { it.credential.id })
        client.close()
    }
}
