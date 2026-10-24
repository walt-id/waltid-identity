package id.walt.policies.policies.status.status

import id.walt.policies.policies.StatusPolicy
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusCwtPolicyTest {
    private val sut = StatusPolicy()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var baseUrl: String
    private val tokens = mutableMapOf<String, Pair<ByteArray, ContentType>>()

    @BeforeAll
    fun startServer() {
        val valid = IetfStatusListTokens.unsignedCwt(index = 0, status = 0u)
        val revoked = IetfStatusListTokens.unsignedCwt(index = 0, status = 1u)
        val suspended = IetfStatusListTokens.unsignedCwt(index = 0, status = 2u, bits = 2)
        tokens["cwt-valid"] = valid to ContentType("application", "statuslist+cwt")
        tokens["cwt-revoked"] = revoked to ContentType("application", "statuslist+cwt")
        tokens["cwt-suspended"] = suspended to ContentType("application", "statuslist+cwt")
        tokens["cwt-valid-hex"] = valid to ContentType.Text.Plain
        tokens["cwt-valid-octet"] = valid to ContentType.Application.OctetStream

        server = embeddedServer(Netty, port = 0) {
            routing {
                get("credentials/{id}") {
                    val id = call.parameters["id"] ?: error("missing id")
                    val (body, contentType) = tokens[id] ?: run {
                        call.response.status(HttpStatusCode.NotFound)
                        return@get
                    }
                    if (contentType.match(ContentType.Text.Plain)) {
                        call.respondText(body.toHexString(), contentType)
                    } else {
                        call.respondBytes(body, contentType)
                    }
                }
            }
        }.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        baseUrl = "http://localhost:$port"
    }

    @AfterAll
    fun stopServer() {
        if (::server.isInitialized) {
            server.stop()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun verifyCwtStatusList(
        name: String,
        id: String,
        useMdoc: Boolean,
        expectedValue: Int,
        expectSuccess: Boolean,
    ) = runTest {
        val credential = if (useMdoc) mdocStatus(id) else jsonStatus(id)
        val args = json.parseToJsonElement(
            """{"discriminator":"ietf","value":$expectedValue}"""
        )
        val result = sut.verify(credential, args, emptyMap())
        assertEquals(expectSuccess, result.isSuccess, "$name: ${result.exceptionOrNull()?.message}")
        if (!expectSuccess) {
            assertTrue(
                result.exceptionOrNull()!!.message.orEmpty().contains("Status validation failed"),
                result.exceptionOrNull()?.message,
            )
        }
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> = Stream.of(
            arguments("binary CWT valid (value 0)", "cwt-valid", false, 0, true),
            arguments("binary CWT revoked fails expected 0", "cwt-revoked", false, 0, false),
            arguments("binary CWT revoked succeeds expected 1", "cwt-revoked", false, 1, true),
            arguments("binary CWT suspended succeeds expected 2", "cwt-suspended", false, 2, true),
            arguments("binary CWT suspended fails expected 0", "cwt-suspended", false, 0, false),
            arguments("hex-encoded CWT over text/plain", "cwt-valid-hex", false, 0, true),
            arguments("binary CWT over octet-stream via COSE tag", "cwt-valid-octet", false, 0, true),
            arguments("mdoc status reference against binary CWT", "cwt-valid", true, 0, true),
            arguments("mdoc status reference revoked CWT fails expected 0", "cwt-revoked", true, 0, false),
        )
    }

    private fun jsonStatus(id: String): JsonObject = buildJsonObject {
        putJsonObject("status") {
            putJsonObject("status_list") {
                put("idx", 0)
                put("uri", "$baseUrl/credentials/$id")
            }
        }
    }

    private fun mdocStatus(id: String): JsonObject = buildJsonObject {
        put(
            "mdoc",
            IetfStatusListTokens.mdocWithStatus(
                index = 0u,
                uri = "$baseUrl/credentials/$id",
            ).toCBORHex(),
        )
    }
}
