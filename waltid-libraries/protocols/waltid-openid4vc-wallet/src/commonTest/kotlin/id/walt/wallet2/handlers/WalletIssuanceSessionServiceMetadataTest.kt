package id.walt.wallet2.handlers

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.Wallet
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletIssuanceSessionServiceMetadataTest {
    @Test
    fun `inline digital credentials metadata avoids well known discovery`() = runTest {
        var httpRequests = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    httpRequests++
                    error("Embedded metadata should avoid network discovery")
                }
            }
        }
        val service = WalletIssuanceSessionService(
            wallet = Wallet(
                id = "wallet",
                staticKey = JWKKey.generate(KeyType.Ed25519),
            ),
            httpClient = client,
        )

        val session = service.start(
            WalletIssuanceSessionRequest(
                offerJson = Json.parseToJsonElement(
                    """
                    {
                      "credential_issuer": "https://issuer.example",
                      "credential_configuration_ids": ["com.example.mdl"],
                      "grants": {
                        "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                          "pre-authorized_code": "TEST_CODE"
                        }
                      },
                      "authorization_server_metadata": {
                        "issuer": "https://issuer.example",
                        "authorization_endpoint": "https://issuer.example/authorize",
                        "token_endpoint": "https://issuer.example/token",
                        "grant_types_supported": ["urn:ietf:params:oauth:grant-type:pre-authorized_code"],
                        "response_types_supported": ["code", "token"]
                      },
                      "credential_issuer_metadata": {
                        "credential_issuer": "https://issuer.example",
                        "credential_endpoint": "https://issuer.example/credential",
                        "credential_configurations_supported": {
                          "com.example.mdl": {
                            "format": "mso_mdoc",
                            "doctype": "com.example.mdl",
                            "credential_signing_alg_values_supported": ["ES256"]
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                ).jsonObject,
            ),
        )

        assertEquals("https://issuer.example", session.offer.issuer.identifier)
        assertEquals(WalletIssuanceGrant.PRE_AUTHORIZED_CODE, session.offer.grant)
        assertEquals(0, httpRequests)
    }
}
