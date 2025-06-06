package id.walt.policies

import id.walt.policies.policies.RevocationPolicy
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
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
                    "4044",
                    StatusCredentialTestServer.waltidRevoked4044,
                    StatusCredentialTestServer.serverPort
                ), false
            ),
            arguments(
                getHolderCredential(
                    "4044",
                    StatusCredentialTestServer.waltidUnrevoked4044,
                    StatusCredentialTestServer.serverPort
                ), true
            ),
            arguments(
                getHolderCredential(
                    "7",
                    StatusCredentialTestServer.sampleRevoked07,
                    StatusCredentialTestServer.serverPort
                ), false
            ),
            arguments(
                getHolderCredential(
                    "42",
                    StatusCredentialTestServer.sampleRevoked42,
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
                            "id": "http://localhost:$port/${
                String.format(
                    StatusCredentialTestServer.statusCredentialPath,
                    id
                )
            }#$index",
                            "type": "StatusList2021Entry",
                            "statusPurpose": "revocation",
                            "statusListIndex": "$index",
                            "statusSize": 1,
                            "statusListCredential": "http://localhost:$port/${
                String.format(
                    StatusCredentialTestServer.statusCredentialPath,
                    id
                )
            }",
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
        const val waltidRevoked4044 = "widr4044"
        const val waltidUnrevoked4044 = "widu4044"
        const val sampleRevoked07 = "r07"
        const val sampleRevoked42 = "r42"
        const val statusCredentialPath = "credentials/%s"

        //statusList2021
        private val credentials = mapOf(
            waltidRevoked4044 to """
                eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCJ9.eyJpc3MiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJzdWIiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImVuY29kZWRMaXN0IjoidUg0c0lBQUFBQUFBQV8tM09JUUVBQUFnRHNFdjZKOGEtQVlJdHdSSmVtdXNBQUFBQUFBQUFBQUFBQUFBQUFBQUFiUUhWQjh4WUFFQUFBQSIsImlkIjoiNTBjYmZhOWY1ODdhYWMxZGE2OWRlOGRkODIyMTMxOTMiLCJzdGF0dXNQdXJwb3NlIjoicmV2b2NhdGlvbiIsInR5cGUiOiJTdGF0dXNMaXN0MjAyMSJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMzUtMDItMDVUMjE6NTM6MDAuMTA3NTc1MzAwIiwiaWQiOiJodHRwOi8vbG9jYWxob3N0OjcwMDQvY3JlZGVudGlhbHMvNTBjYmZhOWY1ODdhYWMxZGE2OWRlOGRkODIyMTMxOTMiLCJpc3N1YW5jZURhdGUiOiIyMDI1LTAyLTA1VDIxOjUzOjAwLjEwNzU3NTMwMCIsImlzc3VlciI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJTdGF0dXNMaXN0MjAyMUNyZWRlbnRpYWwiXX19.JyAwHzqS8FvG4HSXDTDkKZzKjimMdTJgcsXR3L8r3UdEnEbaOx5YhVi0D-2B_beXnJwDkDV7dZ1XCvjtNQ5GQg
            """.trimIndent(),
            waltidUnrevoked4044 to """
                eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCJ9.eyJpc3MiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJzdWIiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImVuY29kZWRMaXN0IjoidUg0c0lBQUFBQUFBQV8tM0JNUUVBQUFEQ29QVlBiUXdmb0FBQUFBQUFBQUFBQUFBQUFBQUFBSUMzQVliU1ZLc0FRQUFBIiwiaWQiOiI1MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInN0YXR1c1B1cnBvc2UiOiJyZXZvY2F0aW9uIiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxIn0sImV4cGlyYXRpb25EYXRlIjoiMjAzNS0wMi0wNVQyMTo1NzowNS4yNTAxMjUzMDAiLCJpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NzAwNC9jcmVkZW50aWFscy81MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDItMDVUMjE6NTc6MDUuMjQ5MDcyODAwIiwiaXNzdWVyIjoiZGlkOmp3azpleUpoYkdjaU9pSkZVekkxTmlJc0ltTnlkaUk2SWxBdE1qVTJJaXdpYTNSNUlqb2lSVU1pTENKNElqb2lPRzFXY25KVllsQjNURnAwWlMxSFdIaHlVazFqU1dsdGRscExWakYxWW5OWWNrZ3RVRzlNVjNSS2R5SXNJbmtpT2lKQ1MwdG5aWGxFY25kd2RHUTFaWEZCUVRaNWRuZFVkMjVtVFVsYWRUbDJlVWRFWVhObE56bE1UM2hCSW4wIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlN0YXR1c0xpc3QyMDIxQ3JlZGVudGlhbCJdfX0.swbjYMQXva8gfBwbV-pYfgfUPtSnVKVVSdMyeWuebB4s1CKEsvxql3q0EJojtdMzf7UUR_Dr9p5_AOduWmiKKg
            """.trimIndent(),
            sampleRevoked07 to """
                eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCJ9.eyJpc3MiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJzdWIiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImVuY29kZWRMaXN0IjoidUg0c0lBQUFBQUFBQUEtM0JJUUVBQUFBQ0lQMV8yaGtXb0FFQUFBQUFBQUFBQUFBQUFBQUFBQURlQmpuN3hUWUFRQUFBIiwiaWQiOiI1MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsInN0YXR1c1B1cnBvc2UiOiJyZXZvY2F0aW9uIiwidHlwZSI6IlN0YXR1c0xpc3QyMDIxIn0sImV4cGlyYXRpb25EYXRlIjoiMjAzNS0wMS0wOVQyMTozODoxNS4wMTczODQ0MDAiLCJpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NzAwNC9jcmVkZW50aWFscy81MGNiZmE5ZjU4N2FhYzFkYTY5ZGU4ZGQ4MjIxMzE5MyIsImlzc3VhbmNlRGF0ZSI6IjIwMjUtMDEtMDlUMjE6Mzg6MTUuMDE3Mzg0NDAwIiwiaXNzdWVyIjoiZGlkOmp3azpleUpoYkdjaU9pSkZVekkxTmlJc0ltTnlkaUk2SWxBdE1qVTJJaXdpYTNSNUlqb2lSVU1pTENKNElqb2lPRzFXY25KVllsQjNURnAwWlMxSFdIaHlVazFqU1dsdGRscExWakYxWW5OWWNrZ3RVRzlNVjNSS2R5SXNJbmtpT2lKQ1MwdG5aWGxFY25kd2RHUTFaWEZCUVRaNWRuZFVkMjVtVFVsYWRUbDJlVWRFWVhObE56bE1UM2hCSW4wIiwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlN0YXR1c0xpc3QyMDIxQ3JlZGVudGlhbCJdfX0.8hhbPJFl5tKrmCLAPdGnp0Ot3TbraKR7LjYNqbZ-4sfx1OTx2gxy8aV4D17Gz8vxUKPAjdwd9Io1tzhKu0iZ-A
            """.trimIndent(),
            sampleRevoked42 to """
                eyJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCJ9.eyJpc3MiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJzdWIiOiJkaWQ6andrOmV5SmhiR2NpT2lKRlV6STFOaUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhM1I1SWpvaVJVTWlMQ0o0SWpvaU9HMVdjbkpWWWxCM1RGcDBaUzFIV0hoeVVrMWpTV2x0ZGxwTFZqRjFZbk5ZY2tndFVHOU1WM1JLZHlJc0lua2lPaUpDUzB0blpYbEVjbmR3ZEdRMVpYRkJRVFo1ZG5kVWQyNW1UVWxhZFRsMmVVZEVZWE5sTnpsTVQzaEJJbjAiLCJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL2V4YW1wbGVzL3YxIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImVuY29kZWRMaXN0IjoidUg0c0lBQUFBQUFBQUEtM0JNUUVBQUFqQW9FV3hmMHB0NFFQVW1RQUFBQUFBQUFBQUFBQUFBQUFBQUlCSEN6T2E3VEFBUUFBQSIsImlkIjoiNTBjYmZhOWY1ODdhYWMxZGE2OWRlOGRkODIyMTMxOTMiLCJzdGF0dXNQdXJwb3NlIjoicmV2b2NhdGlvbiIsInR5cGUiOiJTdGF0dXNMaXN0MjAyMSJ9LCJleHBpcmF0aW9uRGF0ZSI6IjIwMzUtMDEtMDlUMjE6Mzg6MTUuMDE3Mzg0NDAwIiwiaWQiOiJodHRwOi8vbG9jYWxob3N0OjcwMDQvY3JlZGVudGlhbHMvNTBjYmZhOWY1ODdhYWMxZGE2OWRlOGRkODIyMTMxOTMiLCJpc3N1YW5jZURhdGUiOiIyMDI1LTAxLTA5VDIxOjM4OjE1LjAxNzM4NDQwMCIsImlzc3VlciI6ImRpZDpqd2s6ZXlKaGJHY2lPaUpGVXpJMU5pSXNJbU55ZGlJNklsQXRNalUySWl3aWEzUjVJam9pUlVNaUxDSjRJam9pT0cxV2NuSlZZbEIzVEZwMFpTMUhXSGh5VWsxalNXbHRkbHBMVmpGMVluTllja2d0VUc5TVYzUktkeUlzSW5raU9pSkNTMHRuWlhsRWNuZHdkR1ExWlhGQlFUWjVkbmRVZDI1bVRVbGFkVGwyZVVkRVlYTmxOemxNVDNoQkluMCIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJTdGF0dXNMaXN0MjAyMUNyZWRlbnRpYWwiXX19.FuIXJk-_B8B2_QZaNY6RNj4D7CGoOMEyDzPGd3zhOX18DGmIPZW04NOGGOA5s3DTPBfRMnkZ7Xgc0VQE0v7KwQ
            """.trimIndent(),
        )
        val server by lazy {
            println("Initializing embedded webserver...")
            embeddedServer(Netty, configure = {
                connector {
                    port = 8080
                }
            }, module = { module() })
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
}