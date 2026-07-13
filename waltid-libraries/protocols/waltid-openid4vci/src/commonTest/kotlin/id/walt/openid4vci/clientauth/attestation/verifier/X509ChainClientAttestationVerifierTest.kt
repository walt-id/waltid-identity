package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.clientauth.attestation.AttestationBasedClientAuthenticationMethod
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.clientauth.attestation.ClientAttestationJwtTypes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class X509ChainClientAttestationVerifierTest {

    @Test
    fun `authenticates client attestation backed by trusted x509 chain`() = runTest {
        val clientId = "wallet-client"
        val issuer = "https://issuer.example/openid4vci"
        val material = createClientAttestationMaterial(
            clientId = clientId,
            audience = issuer,
        )
        val method = AttestationBasedClientAuthenticationMethod(
            attestationVerifier = X509ChainClientAttestationVerifier(
                trustedRootCertificatesPem = listOf(TRUSTED_ROOT_CERTIFICATE_PEM),
            ),
        )

        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(material.attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(material.popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(result)
        assertEquals(clientId, authenticated.client.id)
    }

    @Test
    fun `rejects client attestation backed by untrusted x509 chain`() = runTest {
        val clientId = "wallet-client"
        val issuer = "https://issuer.example/openid4vci"
        val material = createClientAttestationMaterial(
            clientId = clientId,
            audience = issuer,
        )
        val method = AttestationBasedClientAuthenticationMethod(
            attestationVerifier = X509ChainClientAttestationVerifier(
                trustedRootCertificatesPem = listOf(UNTRUSTED_ROOT_CERTIFICATE_PEM),
            ),
        )

        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(material.attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(material.popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("Client attestation x5c chain is not trusted", failure.error.description)
    }

    private suspend fun createClientAttestationMaterial(
        clientId: String,
        audience: String,
    ): ClientAttestationMaterial {
        require(clientId == "wallet-client") {
            "Static attestation fixture is issued for wallet-client"
        }
        val clientInstanceKey = JWKKey.importJWK(CLIENT_INSTANCE_PRIVATE_JWK).getOrThrow()
        val now = Clock.System.now().epochSeconds

        val popJwt = clientInstanceKey.signJws(
            buildJsonObject {
                put("aud", audience)
                put("iat", now)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP),
            ),
        )

        return ClientAttestationMaterial(
            attestationJwt = CLIENT_ATTESTATION_JWT,
            popJwt = popJwt,
        )
    }

    private data class ClientAttestationMaterial(
        val attestationJwt: String,
        val popJwt: String,
    )

    private companion object {
        const val CLIENT_INSTANCE_PRIVATE_JWK =
            """{"kty":"OKP","crv":"Ed25519","x":"zdlUaIYVhns2xuWqfS3XKwh8P89dpxZ1Odk-6yCmsOk","d":"PdTQzs338Zn6CgyavXkKac6B92gOUxXX6UwZyQZ4QUY"}"""

        // OpenSSL-generated fixed x5c fixture. The PoP JWT is still generated per test with the cnf.jwk key.
        const val CLIENT_ATTESTATION_JWT =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWNsaWVudC1hdHRlc3RhdGlvbitqd3QiLCJ4NWMiOlsiTUlJQnNEQ0NBVmVnQXdJQkFnSVVRclBBVkNBKzYvWjNUUWtwTlZNckhxaEtNSUV3Q2dZSUtvWkl6ajBFQXdJd0t6RXBNQ2NHQTFVRUF3d2dUM0JsYmxOVFRDQlVjblZ6ZEdWa0lFRjBkR1Z6ZEdGMGFXOXVJRkp2YjNRd0lCY05Nall3TnpFek1UVXhPVEV3V2hnUE1qRXlOakEyTVRreE5URTVNVEJhTUNJeElEQWVCZ05WQkFNTUYwOXdaVzVUVTB3Z1EyeHBaVzUwSUVGMGRHVnpkR1Z5TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFZG9JL3QxK2w5cW5yYy80Q3dEdjY1bVJoYTV5Ulk5VDJVaUhJOTVPUkRIYWhvaTlwZTBIUEp4azNyTFJoNlQ1bGhxM2VkckhOUllBK0tBcFgxTVdoQjZOZ01GNHdEQVlEVlIwVEFRSC9CQUl3QURBT0JnTlZIUThCQWY4RUJBTUNCNEF3SFFZRFZSME9CQllFRk5QL1RRQ1dQWjBFUTFrWVgranhIV3A4aXlTck1COEdBMVVkSXdRWU1CYUFGSjV2Z2ROWVlvZ2RXb001SGdxRG5ER2J6bTN0TUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSUF2SWFqaGNib2JWdjUybTZEVXJtYXJTa1dEUXpiTE9IU3lDcVRSSWd1V3dBaUE3QzNnSjUzb0VPQ0hFZTQvRFJQTnIyaVZ2MUZQVUU4TG5ldjhIRG5Qci93PT0iLCJNSUlCdkRDQ0FXT2dBd0lCQWdJVUM2VTNiRzJjUUpyaXJaKzRBMjl6anVTTEZmMHdDZ1lJS29aSXpqMEVBd0l3S3pFcE1DY0dBMVVFQXd3Z1QzQmxibE5UVENCVWNuVnpkR1ZrSUVGMGRHVnpkR0YwYVc5dUlGSnZiM1F3SUJjTk1qWXdOekV6TVRVeE9URXdXaGdQTWpFeU5qQTJNVGt4TlRFNU1UQmFNQ3N4S1RBbkJnTlZCQU1NSUU5d1pXNVRVMHdnVkhKMWMzUmxaQ0JCZEhSbGMzUmhkR2x2YmlCU2IyOTBNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVja2pIVCt1RGNFalpXbnR5L1gzVHhHR3doVnk5ZExiYk9IZTdrRmpUQWVFU0NsVDVJTHZDZ3kxeUZ4dDBzbHJQK3hpU2JUMHc3TDRxZkNtL3hGWnRwcU5qTUdFd0hRWURWUjBPQkJZRUZKNXZnZE5ZWW9nZFdvTTVIZ3FEbkRHYnptM3RNQjhHQTFVZEl3UVlNQmFBRko1dmdkTllZb2dkV29NNUhncURuREdiem0zdE1BOEdBMVVkRXdFQi93UUZNQU1CQWY4d0RnWURWUjBQQVFIL0JBUURBZ0VHTUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSUdWcHI4OWZIL0Y2UzliU2xIQlFlaWliaGczSzZNZDlOZDJiNUMxRHRpSDRBaUI5TkFKa2d6eTRuc0dvajZZSkJ2alRMa1k2Q1VGc1NMSTkyMUoxdkJFODl3PT0iXX0.eyJzdWIiOiJ3YWxsZXQtY2xpZW50IiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDAsImNuZiI6eyJqd2siOnsia3R5IjoiT0tQIiwiY3J2IjoiRWQyNTUxOSIsIngiOiJ6ZGxVYUlZVmhuczJ4dVdxZlMzWEt3aDhQODlkcHhaMU9kay02eUNtc09rIn19fQ.VCFDQbEdRzQZwVjQq5fH3EIQvIHWsLASxR0hPcTLtu8nK3zU9iO6lrS9IClT0P2xzBzo0dBydlEVn5LsZ4Tltw"

        val TRUSTED_ROOT_CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBvDCCAWOgAwIBAgIUC6U3bG2cQJrirZ+4A29zjuSLFf0wCgYIKoZIzj0EAwIw
            KzEpMCcGA1UEAwwgT3BlblNTTCBUcnVzdGVkIEF0dGVzdGF0aW9uIFJvb3QwIBcN
            MjYwNzEzMTUxOTEwWhgPMjEyNjA2MTkxNTE5MTBaMCsxKTAnBgNVBAMMIE9wZW5T
            U0wgVHJ1c3RlZCBBdHRlc3RhdGlvbiBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0D
            AQcDQgAEckjHT+uDcEjZWnty/X3TxGGwhVy9dLbbOHe7kFjTAeESClT5ILvCgy1y
            Fxt0slrP+xiSbT0w7L4qfCm/xFZtpqNjMGEwHQYDVR0OBBYEFJ5vgdNYYogdWoM5
            HgqDnDGbzm3tMB8GA1UdIwQYMBaAFJ5vgdNYYogdWoM5HgqDnDGbzm3tMA8GA1Ud
            EwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0cAMEQCIGVp
            r89fH/F6S9bSlHBQeiibhg3K6Md9Nd2b5C1DtiH4AiB9NAJkgzy4nsGoj6YJBvjT
            LkY6CUFsSLI921J1vBE89w==
            -----END CERTIFICATE-----
        """.trimIndent()

        val UNTRUSTED_ROOT_CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBwDCCAWegAwIBAgIUGv6pRFTmIh/wu1wvqyPJL8b6GY0wCgYIKoZIzj0EAwIw
            LTErMCkGA1UEAwwiT3BlblNTTCBVbnRydXN0ZWQgQXR0ZXN0YXRpb24gUm9vdDAg
            Fw0yNjA3MTMxNTE5MTBaGA8yMTI2MDYxOTE1MTkxMFowLTErMCkGA1UEAwwiT3Bl
            blNTTCBVbnRydXN0ZWQgQXR0ZXN0YXRpb24gUm9vdDBZMBMGByqGSM49AgEGCCqG
            SM49AwEHA0IABNHPbzfkbr6wV0E+YF6xHC95G+4JUHjtWXdHzEbDIGgBNH4MF8vu
            teCbgeFm7L6IZM5JnRkcYyjkK6Iqus2RqVSjYzBhMB0GA1UdDgQWBBT370lFUdA6
            UBYWj9h2JEayRK1i6jAfBgNVHSMEGDAWgBT370lFUdA6UBYWj9h2JEayRK1i6jAP
            BgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAKBggqhkjOPQQDAgNHADBE
            AiBHVdxFdEx5rzD+o/LU3Y6P8ZHYUgAZ912d74z+AVsiawIgNf6uwAwZ9Ccu7M8Z
            0yxGVH/H4JDvSotTBVQHTjdiL3w=
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
