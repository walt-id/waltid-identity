package id.walt.policies

import id.walt.policies.policies.RevocationPolicy
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevocationPolicyTest {
    private val sut = RevocationPolicy()

    @BeforeAll
    fun startServer() {
        if (!serverStarted) {
            println("Starting status credential test server...")
            StatusCredentialTestServer.server.start()
            serverStarted = true
        }
    }

    @AfterAll
    fun stopServer() {
        if (serverStarted) {
            StatusCredentialTestServer.server.stop()
        }
    }

    @ParameterizedTest
    @MethodSource
    fun verify(credential: String, expectValid: Boolean) = runTest {
        val json = Json.parseToJsonElement(credential).jsonObject
        val result = sut.verify(json, null, emptyMap())
        assertEquals(expectValid, result.isSuccess)
        assertEquals(!expectValid, result.isFailure)
    }


    companion object {
        var serverStarted = false

        @JvmStatic
        fun verify(): Stream<Arguments> = Stream.of(
            arguments(
                getHolderCredential(
                    "12199",
                    StatusCredentialTestServer.credentialId,
                    StatusCredentialTestServer.serverPort
                ), true
            ),
            arguments(
                getHolderCredential(
                    "5476",
                    StatusCredentialTestServer.credentialId,
                    StatusCredentialTestServer.serverPort
                ), false
            ),
        )

        private fun getHolderCredential(index: String, id: String, port: Int) =
            """
                {
                    "vc":
                    {
                        "credentialStatus":
                        {
                            "id": "http://localhost:$port/${String.format(StatusCredentialTestServer.statusCredentialPath, id)}#$index",
                            "type": "StatusList2021Entry",
                            "statusPurpose": "revocation",
                            "statusListIndex": "$index",
                            "statusSize": 1,
                            "statusListCredential": "http://localhost:$port/${String.format(StatusCredentialTestServer.statusCredentialPath, id)}",
                            "statusMessage":
                            [
                                {
                                    "status": "0x0",
                                    "message": "unset"
                                },
                                {
                                    "status": "0x1",
                                    "message": "set"
                                }
                            ]
                        }
                    }
                }
            """.trimIndent()
    }

    object StatusCredentialTestServer {
        const val serverPort = 8080
        const val credentialId = "50cbfa9f587aac1da69de8dd82213193"
        const val statusCredentialPath = "credentials/%s"

        //statusList2021
        private val credential = """
            eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImlkIjoiaHR0cDovL2xvY2FsaG9zdDo3MDA0L2NyZWRlbnRpYWxzLzUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlN0YXR1c0xpc3QyMDIxQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKUFMxQWlMQ0pqY25ZaU9pSkZaREkxTlRFNUlpd2lhMmxrSWpvaVRWbzRVR1pVTjBscVIyTlFNaTFKT1haVWVXRndkalprYUhabGRHWm9VVWxrYzJkWFRFSXlaVGRLUlNJc0luZ2lPaUl0YUZZM2RsSnNOWFowTlZOdWFGVjFOR3hwYjNwc2RuUnJaRGhtZEdrNE5EQmxVV1UzTkRacE1VWk5JbjAiLCJpc3N1YW5jZURhdGUiOiIyMDI1LTAxLTA5VDIxOjM4OjE1LjAxNzM4NDQwMCIsImV4cGlyYXRpb25EYXRlIjoiMjAzNS0wMS0wOVQyMTozODoxNS4wMTczODQ0MDAiLCJjcmVkZW50aWFsU3ViamVjdCI6eyJpZCI6IjUwY2JmYTlmNTg3YWFjMWRhNjlkZThkZDgyMjEzMTkzIiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxIiwic3RhdHVzUHVycG9zZSI6InJldm9jYXRpb24iLCJlbmNvZGVkTGlzdCI6Ikg0c0lBQUFBQUFBQS8yTmdHQVdqWUdnQUFRQ0tHVmt3clFJQUFBPT0ifX19.fpfckDYaTMXsONUKTKUB3xqfy8erbt68t0edM8ZW--BOkRIYS2Q-nEKYQ8uhiOhBo6l-xSzasjpLiMhGg-5iCQ
        """.trimIndent()
        private val environment = applicationEngineEnvironment {
            envConfig()
        }
        val server: ApplicationEngine by lazy {
            println("Initializing embedded webserver...")
            embeddedServer(Netty, environment)
        }

        private fun Application.module() {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                get("credentials/{id}") {
                    call.respond<String>(credential)
                }
            }
        }

        private fun ApplicationEngineEnvironmentBuilder.envConfig() {
            module {
                module()
            }
            connector {
                port = serverPort
            }
        }

    }
}