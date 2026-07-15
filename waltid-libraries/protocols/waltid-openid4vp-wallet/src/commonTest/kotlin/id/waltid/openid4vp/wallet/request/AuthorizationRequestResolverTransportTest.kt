package id.waltid.openid4vp.wallet.request

import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.webdatafetching.WebDataFetcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthorizationRequestResolverTransportTest {

    @Test
    fun `request uri post sends Final wallet metadata nonce and JWT accept header`() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedRequest = request
                    respond(
                        content = "request.object.jwt",
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            "application/oauth-authz-req+jwt",
                        ),
                    )
                }
            }
        }
        val fetcher = WebDataFetcher.wrapping(client, "request-uri-post-test")

        val response = AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
            webResolveAuthReq = fetcher,
            requestUri = "https://verifier.example/request.jwt",
            requestUriMethod = RequestUriHttpMethod.POST,
        )

        val request = assertNotNull(capturedRequest)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.FormUrlEncoded, request.body.contentType)
        assertEquals("application/oauth-authz-req+jwt", request.headers[HttpHeaders.Accept])
        val form = request.bodyText().parseUrlEncodedParameters()
        assertEquals(response.walletNonce, form["wallet_nonce"])
        assertTrue(!form["wallet_nonce"].isNullOrBlank())
        val metadata = Json.parseToJsonElement(assertNotNull(form["wallet_metadata"])).jsonObject
        assertNotNull(metadata["vp_formats_supported"])
        assertNotNull(metadata["authorization_encryption_alg_values_supported"])
        assertNotNull(metadata["authorization_encryption_enc_values_supported"])
        assertTrue("encrypted_response_enc_values_supported" !in metadata)
        assertEquals(ContentType.parse("application/oauth-authz-req+jwt"), response.contentType)
    }

    private fun HttpRequestData.bodyText(): String = when (val requestBody = body) {
        is OutgoingContent.ByteArrayContent -> requestBody.bytes().decodeToString()
        is TextContent -> requestBody.text
        else -> error("Unsupported request body type: ${requestBody::class}")
    }
}
