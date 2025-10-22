import id.walt.openid4vp.clientidprefix.*
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SanDnsTests {

    private val authenticator = ClientIdPrefixAuthenticator
    private val validMetadataJson = """{ "vp_formats_supported": {} }"""

    @Test
    fun `x509_san_dns should succeed with valid signature and matching SAN`() = runTest {
        // 1. Setup: Certificate is for 'verifier.example.com'
        val signedJws =
            "eyJ4NWMiOlsiTUlJQlZqQ0IvYUFEQWdFQ0FnZzlKVTl5cUxUU2xEQUtCZ2dxaGtqT1BRUURBakFmTVIwd0d3WURWUVFEREJSMlpYSnBabWxsY2k1bGVHRnRjR3hsTG1OdmJUQWVGdzB5TlRFd01UUXdOVE0yTVRaYUZ3MHlOakV3TVRRd05UTTJNVFphTUI4eEhUQWJCZ05WQkFNTUZIWmxjbWxtYVdWeUxtVjRZVzF3YkdVdVkyOXRNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUV5K3l0d2hFYTMxL29zNmR6OUI1WkRMa0pwbmlnZWgyRkVocG9STy9hUHdCOFdQS0U2SGtSUUdsVnE0RnVlcjdNQTFXR2dvWmxzT1lEUVB3OXZybzYxS01qTUNFd0h3WURWUjBSQkJnd0ZvSVVkbVZ5YVdacFpYSXVaWGhoYlhCc1pTNWpiMjB3Q2dZSUtvWkl6ajBFQXdJRFNBQXdSUUloQUppY20vZWdENWZlSmdVdWNhNkZzYk1JcVV4UDZiYU9BTGtyRUtldzFHMzRBaUIwc2hDWWZRdGZTZzFrczVTRm85MDY2OWVBQ0E2c25tMjBIalJsSGMyWFBnPT0iXSwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJub25jZSI6IjEyMzQifQ.LCkadcCpkD4eYXe7tkv79IahDatHaMz8U1ZCbC0eykx9gxUpF3dR50bt6omqb3LfcnkB0CLS0nQuimMjLxArJw"

        // 2. Context: Client claims to be 'verifier.example.com'
        val context = RequestContext(
            clientId = "x509_san_dns:verifier.example.com",
            clientMetadataJson = validMetadataJson,
            requestObjectJws = signedJws
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()
        assertIs<X509SanDns>(clientId)

        // Authenticate
        val result = authenticator.authenticate(clientId, context)

        if (result is ClientValidationResult.Failure) {
            println(result.error)
        }

        assertIs<ClientValidationResult.Success>(result)
    }

    @Test
    fun `x509_san_dns should fail if SAN does not match`() = runTest {
        // 1. Setup: Certificate is for 'verifier.example.com'
        val signedJws =
            "eyJ4NWMiOlsiTUlJQlZ6Q0IvcUFEQWdFQ0Fna0E1dmdZV0pkK0Nna3dDZ1lJS29aSXpqMEVBd0l3SHpFZE1Cc0dBMVVFQXd3VWRtVnlhV1pwWlhJdVpYaGhiWEJzWlM1amIyMHdIaGNOTWpVeE1ERTBNRFV6TmpFM1doY05Nall4TURFME1EVXpOakUzV2pBZk1SMHdHd1lEVlFRRERCUjJaWEpwWm1sbGNpNWxlR0Z0Y0d4bExtTnZiVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEEwSUFCUHRjSWFTdWRzVGpmeHU1elJrczRQdE9mdEo3SEl3TEFwa3FrakxYVnE4M3VQS0F2V3FsbWw4UWdLZDRmMjVtSjNtRm9pbmxWN2tGa1Y2NUltTHFFdHlqSXpBaE1COEdBMVVkRVFRWU1CYUNGSFpsY21sbWFXVnlMbVY0WVcxd2JHVXVZMjl0TUFvR0NDcUdTTTQ5QkFNQ0EwZ0FNRVVDSVFDaDZraHo5K0V5THRIb0RXL3RnTll5S29QR2gyR2lFT1hVYnNsa2h3ZGJxd0lnTXVCYUY5bFNLa1JtWXQzbGdKY2JoNFRpOXM3Nkd3ZkRBMmp2SUJxRk1Bbz0iXSwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4ifQ.r17xxNyhGmn4S3G3WvGHWuC-RdWBjaOjoW0vh2IWpAtRoghXKJ33H1kTi1Ljv1RBfDj3ZDSkhDzkmPV7fvT5vw"

        // 2. Context: Client claims to be 'wrong.example.com'
        val context = RequestContext(
            clientId = "x509_san_dns:wrong.example.com",
            clientMetadataJson = validMetadataJson,
            requestObjectJws = signedJws
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        // 3. Authenticate
        val result = authenticator.authenticate(clientId, context)

        if (result is ClientValidationResult.Failure) {
            println(result.error)
        }

        assertIs<ClientValidationResult.Failure>(result)
        assertEquals(ClientIdError.SanDnsMismatch("wrong.example.com", listOf("verifier.example.com")).message, result.error.message)
    }

}
