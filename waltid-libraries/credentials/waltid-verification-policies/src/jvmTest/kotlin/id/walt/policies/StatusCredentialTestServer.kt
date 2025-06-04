package id.walt.policies

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json

object StatusCredentialTestServer {
    private const val port = 8080
    const val url = "http://localhost:$port"
    const val waltidRevoked4044 = "widr4044"
    const val waltidUnrevoked4044 = "widu4044"
    const val sampleRevoked07 = "r07"
    const val sampleRevoked42 = "r42"
    const val statusCredentialPath = "credentials/%s"

    private var serverStarted = false

    //statusList2021
    private val credentials = mapOf(
        waltidRevoked4044 to """
                eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoiaHR0cDovL2xvY2FsaG9zdDo3MDA0L2NyZWRlbnRpYWxzLzUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlN0YXR1c0xpc3QyMDIxQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJpc3N1YW5jZURhdGUiOiIyMDI1LTAyLTA1VDIxOjUzOjAwLjEwNzU3NTMwMCIsImV4cGlyYXRpb25EYXRlIjoiMjAzNS0wMi0wNVQyMTo1MzowMC4xMDc1NzUzMDAiLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6IjUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxIiwic3RhdHVzUHVycG9zZSI6InJldm9jYXRpb24iLCJlbmNvZGVkTGlzdCI6Ikg0c0lBQUFBQUFBQS8rM09JUUVBQUFnRHNFdjZKOGErQVlJdHdSSmVtdXNBQUFBQUFBQUFBQUFBQUFBQUFBQUFiUUhWQjh4WUFFQUFBQT09In19fQ.B0MB-O9425unBls1bAgwxsgesms99lItn4G0ntA9CLDrJXg8ozlkMOFHwZ6fvhy-805nV3OSx7dytXx9AvRSCg
            """.trimIndent(),
        waltidUnrevoked4044 to """
                eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoiaHR0cDovL2xvY2FsaG9zdDo3MDA0L2NyZWRlbnRpYWxzLzUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlN0YXR1c0xpc3QyMDIxQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJpc3N1YW5jZURhdGUiOiIyMDI1LTAyLTA1VDIxOjU3OjA1LjI0OTA3MjgwMCIsImV4cGlyYXRpb25EYXRlIjoiMjAzNS0wMi0wNVQyMTo1NzowNS4yNTAxMjUzMDAiLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6IjUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxIiwic3RhdHVzUHVycG9zZSI6InJldm9jYXRpb24iLCJlbmNvZGVkTGlzdCI6Ikg0c0lBQUFBQUFBQS8rM0JNUUVBQUFEQ29QVlBiUXdmb0FBQUFBQUFBQUFBQUFBQUFBQUFBSUMzQVliU1ZLc0FRQUFBIn19fQ.1CC2Qr0pQN70sbGvZUjW11PUX07WDkvKbm0PTNsC3HhRYDT9JQnGKkQInPit0bKQJlgTTursaxTz4tXtJ3d0Bg
            """.trimIndent(),
        sampleRevoked07 to """
                eyJraWQiOiJWeng3bDVmaDU2RjNQZjlhUjNERUNVNUJ3ZnJZNlpKZTA1YWlXWVd6YW44IiwiYWxnIjoiRWREU0EifQ.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy9leGFtcGxlcy92MSJdLCJpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NzAwNC9jcmVkZW50aWFscy81MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJTdGF0dXNMaXN0MjAyMUNyZWRlbnRpYWwiXSwiaXNzdWVyIjoiZGlkOmp3azpleUpyZEhraU9pSlBTMUFpTENKamNuWWlPaUpGWkRJMU5URTVJaXdpYTJsa0lqb2lUVm80VUdaVU4wbHFSMk5RTWkxSk9YWlVlV0Z3ZGpaa2FIWmxkR1pvVVVsa2MyZFhURUl5WlRkS1JTSXNJbmdpT2lJdGFGWTNkbEpzTlhaME5WTnVhRlYxTkd4cGIzcHNkblJyWkRobWRHazROREJsVVdVM05EWnBNVVpOSW4wIiwiaXNzdWFuY2VEYXRlIjoiMjAyNS0wMS0wOVQyMTozODoxNS4wMTczODQ0MDAiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMzUtMDEtMDlUMjE6Mzg6MTUuMDE3Mzg0NDAwIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiI1MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInR5cGUiOiJTdGF0dXNMaXN0MjAyMSIsInN0YXR1c1B1cnBvc2UiOiJyZXZvY2F0aW9uIiwiZW5jb2RlZExpc3QiOiJINHNJQUFBQUFBQUFBKzNCSVFFQUFBQUNJUDEvMmhrV29BRUFBQUFBQUFBQUFBQUFBQUFBQUFEZUJqbjd4VFlBUUFBQSJ9fX0.OFOCkDxrZ5eKf3h_FxQqkzD22PdeTbbI6sIyV25NWOW7yiNn0IhGXzl-SJwMkY_f-TxkgZlrHq3r2458s-pBAA
            """.trimIndent(),
        sampleRevoked42 to """
                eyJraWQiOiJWeng3bDVmaDU2RjNQZjlhUjNERUNVNUJ3ZnJZNlpKZTA1YWlXWVd6YW44IiwiYWxnIjoiRWREU0EifQ.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiLCJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy9leGFtcGxlcy92MSJdLCJpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NzAwNC9jcmVkZW50aWFscy81MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJTdGF0dXNMaXN0MjAyMUNyZWRlbnRpYWwiXSwiaXNzdWVyIjoiZGlkOmp3azpleUpyZEhraU9pSlBTMUFpTENKamNuWWlPaUpGWkRJMU5URTVJaXdpYTJsa0lqb2lUVm80VUdaVU4wbHFSMk5RTWkxSk9YWlVlV0Z3ZGpaa2FIWmxkR1pvVVVsa2MyZFhURUl5WlRkS1JTSXNJbmdpT2lJdGFGWTNkbEpzTlhaME5WTnVhRlYxTkd4cGIzcHNkblJyWkRobWRHazROREJsVVdVM05EWnBNVVpOSW4wIiwiaXNzdWFuY2VEYXRlIjoiMjAyNS0wMS0wOVQyMTozODoxNS4wMTczODQ0MDAiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMzUtMDEtMDlUMjE6Mzg6MTUuMDE3Mzg0NDAwIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiI1MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInR5cGUiOiJTdGF0dXNMaXN0MjAyMSIsInN0YXR1c1B1cnBvc2UiOiJyZXZvY2F0aW9uIiwiZW5jb2RlZExpc3QiOiJINHNJQUFBQUFBQUFBKzNCTVFFQUFBakFvRVd4ZjBwdDRRUFVtUUFBQUFBQUFBQUFBQUFBQUFBQUFJQkhDek9hN1RBQVFBQUEifX19.HWmYRdlce_C3GjFCKxPUSDJDgGqrPy1h7UJEcfGOcxisfWhYIBAbdis9-sws4sFjzpoA14Himp9lUzFdZTqDAw
            """.trimIndent(),
    )
    private val server by lazy {
        println("Initializing embedded webserver...")
        embeddedServer(Netty, configure = {
            connector {
                port = 8080
            }
        }, module = { module() })
    }

    fun start() {
        if (!serverStarted) {
            println("Starting status credential test server...")
            server.start()
            serverStarted = true
        }
    }

    fun stop() {
        if (serverStarted) {
            println("Stopping status credential test server...")
            server.stop()
        }
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("credentials/{id}") {
                val id = call.parameters.getOrFail("id")
                call.respond<String>(credentials[id]!!)
            }
        }
    }
}