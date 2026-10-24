package id.walt.policies.policies.status

import id.walt.policies.policies.status.status.IetfStatusListTokens
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialFetcherCwtTest {

    @Test
    fun `detects CWT from content type and COSE tag`() {
        val cwt = byteArrayOf(0xD2.toByte(), 0x84.toByte())
        assertTrue(CredentialFetcher.isCwt(ContentType("application", "statuslist+cwt"), byteArrayOf(0x00)))
        assertTrue(CredentialFetcher.isCwt(ContentType.Application.OctetStream, cwt))
        assertFalse(CredentialFetcher.isCwt(ContentType("application", "statuslist+jwt"), cwt))
        assertFalse(CredentialFetcher.isCwt(ContentType.Text.Plain, "eyJhbGciOiJFUzI1NiJ9.e30.sig".encodeToByteArray()))
    }

    @Test
    fun `fetch converts binary CWT to hex and leaves JWT as text`() = runTest {
        val cwt = IetfStatusListTokens.unsignedCwt(index = 0, status = 0u)
        val jwt = "eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0In0.e30.sig"
        val expectedAccept = "application/statuslist+jwt, application/statuslist+cwt, text/plain, */*"
        val acceptHeaders = mutableListOf<String?>()
        val server = embeddedServer(Netty, port = 0) {
            routing {
                get("cwt") {
                    acceptHeaders += call.request.headers[HttpHeaders.Accept]
                    call.respondBytes(cwt, ContentType("application", "statuslist+cwt"))
                }
                get("jwt") {
                    acceptHeaders += call.request.headers[HttpHeaders.Accept]
                    call.respondText(jwt, ContentType("application", "statuslist+jwt"))
                }
            }
        }.start(wait = false)
        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }
            val client = HttpClient()
            try {
                val fetcher = CredentialFetcher(client)
                val fetchedCwt = fetcher.fetch("http://localhost:$port/cwt").getOrThrow()
                val fetchedJwt = fetcher.fetch("http://localhost:$port/jwt").getOrThrow()
                assertEquals(cwt.toHexString(), fetchedCwt)
                assertEquals(jwt, fetchedJwt)
                assertEquals(2, acceptHeaders.size)
                assertEquals(expectedAccept, acceptHeaders[0])
                assertEquals(expectedAccept, acceptHeaders[1])
            } finally {
                client.close()
            }
        } finally {
            server.stop()
        }
    }
}
