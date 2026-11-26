package id.walt.itb

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.exclude
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

/** GITB 1.29.5 status, report and cleanup API for sessions created by the portal bridge. */
class ItbRestClient(
    private val httpClient: HttpClient,
    private val apiUrl: Url,
    private val organisationKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        require(organisationKey.isNotBlank()) { "ITB organisation API key is required" }
        require(apiUrl.protocol == URLProtocol.HTTPS ||
            (apiUrl.protocol == URLProtocol.HTTP && apiUrl.host in setOf("localhost", "127.0.0.1", "::1"))) {
            "Remote ITB API access requires HTTPS"
        }
        require(apiUrl.user == null && apiUrl.password == null && apiUrl.parameters.isEmpty()) {
            "ITB API URL must not contain credentials or query parameters"
        }
    }

    class HttpFailure(internal val operation: String, val statusCode: Int) :
        IllegalStateException("ITB $operation request failed (HTTP $statusCode)")

    internal class TransportFailure(val operation: String, detail: String) :
        IllegalStateException("ITB $operation request failed ($detail)")

    internal class InvalidStatusResponse : IllegalStateException("ITB status returned an invalid response")

    @Serializable
    data class SessionStatus(
        val session: String,
        val result: ItbSessionReport.Verdict,
        val startTime: String,
        val endTime: String? = null,
        val report: String? = null,
    ) {
        init {
            require(session.isNotBlank()) { "Missing ITB session identity" }
            val start = Instant.parse(startTime)
            require(endTime == null || Instant.parse(endTime) >= start) { "Invalid ITB session chronology" }
        }

        val isComplete: Boolean get() = endTime != null
    }

    @Serializable
    private data class StatusRequest(val session: List<String>, val withReports: Boolean)

    @Serializable
    private data class StatusResponse(val sessions: List<SessionStatus>)

    @Serializable
    private data class StopRequest(val session: List<String>)

    suspend fun status(sessionIds: List<String>, withReports: Boolean = false): List<SessionStatus> {
        validateSessions(sessionIds)
        val body = json.encodeToString(StatusRequest(sessionIds, withReports))
        val response = request("status", HttpMethod.Post, body)
        val sessions = try {
            json.decodeFromString<StatusResponse>(response).sessions
        } catch (_: SerializationException) {
            throw InvalidStatusResponse()
        } catch (_: Exception) {
            throw IllegalStateException("ITB status returned an invalid response")
        }
        require(sessions.map { it.session }.toSet() == sessionIds.toSet() && sessions.size == sessionIds.size) {
            "ITB status response has missing, duplicate or unexpected sessions"
        }
        return sessions
    }

    suspend fun report(caseId: String, sessionId: String): ItbSessionReport {
        validateSessions(listOf(sessionId))
        return ItbSessionReport.parse(request("report", HttpMethod.Get, sessionId = sessionId), caseId, sessionId)
    }

    /** Call only with sessions created by the current runner; never cancel the tenant's other runs. */
    suspend fun stop(sessionIds: List<String>) {
        validateSessions(sessionIds)
        request("stop", HttpMethod.Post, json.encodeToString(StopRequest(sessionIds)))
    }

    private suspend fun request(operation: String, method: HttpMethod, body: String? = null, sessionId: String? = null): String {
        try {
            return withTimeoutOrNull(30_000) {
                val response = httpClient.request(URLBuilder(apiUrl).apply {
                    appendPathSegments("tests", operation)
                    sessionId?.let { appendPathSegments(it, encodeSlash = true) }
                }.build()) {
                    this.method = method
                    header("ITB-API-KEY", organisationKey)
                    // ITB treats Accept as one report format and rejects the wallet plugin's added JSON type.
                    if (operation == "report") exclude(ContentType.Application.Json)
                    accept(if (operation == "report") ContentType.Application.Xml else ContentType.Application.Json)
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }
                if (!response.status.isSuccess()) throw HttpFailure(operation, response.status.value)
                response.bodyAsText()
            } ?: throw TransportFailure(operation, "timeout")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HttpFailure) {
            throw failure
        } catch (failure: TransportFailure) {
            throw failure
        } catch (error: IOException) {
            throw TransportFailure(operation, error.javaClass.simpleName)
        } catch (error: Exception) {
            // Server bodies and client exceptions can include credentials or protocol payloads.
            // Expose the operation and error class without echoing those values into CI reports.
            throw IllegalStateException("ITB $operation request failed (${error::class.simpleName})")
        }
    }

    private fun validateSessions(sessionIds: List<String>) {
        require(sessionIds.isNotEmpty() && sessionIds.all { it.isNotBlank() } && sessionIds.distinct().size == sessionIds.size) {
            "An explicit nonempty selection of distinct ITB sessions is required"
        }
    }
}
