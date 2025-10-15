package id.walt.openid4vp.clientidprefix.prefix

import id.walt.did.helpers.WaltidServices
import id.walt.openid4vp.clientidprefix.*
import id.walt.openid4vp.clientidprefix.prefixes.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PrefixTests {

    // Examples from https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-5.9.3

    val redirectUriExample = "redirect_uri:https://client.example.org/cb"

    val openidFederationExample = "openid_federation:https://federation-verifier.example.com"

    val decentralizedIdentifierExample = "decentralized_identifier:did:example:123"

    val verifierAttestationExample = "verifier_attestation:verifier.example"

    val x509SanDnsExample = "x509_san_dns:client.example.org"

    val x509HashExample = "x509_hash:Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk"

    //val originExample = "origin:https://verifier.example.com/"

    private val authenticator = ClientIdPrefixAuthenticator
    private val validMetadataJson = """{ "vp_formats_supported": {} }"""

    inline fun <reified T : ClientId> parseClientIdPrefixTest(clientIdString: String): T {
        val parsed = ClientIdPrefixParser.parse(clientIdString).getOrThrow()
        println("Parsed $parsed from $clientIdString")
        assertIs<T>(parsed, "Invalid type for $clientIdString, expected ${T::class.simpleName} but found ${parsed::class.simpleName}")
        return parsed
    }

    @Test
    fun parseRedirectUriPrefixExample() {
        val redirectUri = parseClientIdPrefixTest<RedirectUri>(redirectUriExample)
        assertEquals(Url("https://client.example.org/cb"), redirectUri.uri)
    }

    @Test
    fun `redirect_uri should succeed for unsigned request with metadata`() = runTest {
        val context = RequestContext(
            clientId = "redirect_uri:https://app.example.com/callback",
            clientMetadataJson = validMetadataJson,
            requestObjectJws = null // Unsigned
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        val result = authenticator.authenticate(clientId, context)

        assertIs<ClientValidationResult.Success>(result)
        assertNotNull(result.clientMetadata.vpFormatsSupported)
    }

    @Test
    fun `redirect_uri should fail if request is signed`() = runTest {
        val context = RequestContext(
            clientId = "redirect_uri:https://app.example.com/callback",
            clientMetadataJson = validMetadataJson,
            requestObjectJws = "dummy.jws.string" // Signed request is not allowed
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        val result = authenticator.authenticate(clientId, context)

        assertIs<ClientValidationResult.Failure>(result)
        assertEquals(ClientIdError.DoesNotSupportSignature.message, result.error.message)
    }

    @Test
    fun parseOpenidFederationPrefixExample() {
        val openIdFederation = parseClientIdPrefixTest<OpenIdFederation>(openidFederationExample)
        assertEquals("https://federation-verifier.example.com", openIdFederation.entityId)
    }

    @Test
    fun parseDecentralizedIdentifierPrefixExample() {
        val decentralizedIdentifier = parseClientIdPrefixTest<DecentralizedIdentifier>(decentralizedIdentifierExample)
        assertEquals("did:example:123", decentralizedIdentifier.did)
    }

    @Test
    fun `did should succeed with valid signature from a resolved key`() = runTest {
        WaltidServices.minimalInit()

        // Setup realistic DID
        val signedJws = "eyJraWQiOiJQQnYzVHh2NnRhWE5zMTBZNUcyOW1kUmFiMzBMVWljN21ubFNSOUxqaVVNIiwiYWxnIjoiRVMyNTYifQ.eyJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJub25jZSI6Inh5eiJ9.MLhDuXg5uOvgWkRJoTwnWZY7Ump9-TeGLyMWDlxGNCOVBgS6mPyKKd2H8jrcDGvW3BASKo4jJi4MhHgrvCcUsA"

        // Create context and authenticate
        val context = RequestContext(
            clientId = "decentralized_identifier:did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiUEJ2M1R4djZ0YVhOczEwWTVHMjltZFJhYjMwTFVpYzdtbmxTUjlMamlVTSIsIngiOiJXeGREdFJJVHYxdW9LU2V5bTg3d3FyTnRtV2ZuNEptVkhsdHNCMEctUFRzIiwieSI6IjBPUmZjNGwyYV9wSWJtUXlTTU13eDF2cVNoRW9lVmpnQnpsUWRJQW5IbkkifQ",
            clientMetadataJson = validMetadataJson,
            requestObjectJws = signedJws
        )
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()
        assertIs<DecentralizedIdentifier>(clientId)

        val result = authenticator.authenticate(clientId, context)

        if (result is ClientValidationResult.Failure) {
            println(result.error)
        }

        assertIs<ClientValidationResult.Success>(result)
    }

    @Test
    fun parseVerifierAttestationPrefixExample() {
        val parsed = parseClientIdPrefixTest<VerifierAttestation>(verifierAttestationExample)
        assertEquals("verifier.example", parsed.sub)
    }

    @Test
    fun parseX509SanDnsPrefixExample() {
        val parsed = parseClientIdPrefixTest<X509SanDns>(x509SanDnsExample)
        assertEquals("client.example.org", parsed.dnsName)
    }

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

    @Test
    fun parseX509HashPrefixExample() {
        val parsed = parseClientIdPrefixTest<X509Hash>(x509HashExample)
        assertEquals("Uvo3HtuIxuhC92rShpgqcT3YXwrqRxWEviRiA0OZszk", parsed.hash)
    }

    @Test
    fun `pre_registered should succeed if provider finds metadata`() = runTest {
        val context = RequestContext("my-registered-app")
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        // The caller provides the lookup logic
        val metadataProvider: suspend (String) -> String? = { id ->
            if (id == "my-registered-app") validMetadataJson else null
        }

        val result = authenticator.authenticate(clientId, context, metadataProvider)

        assertIs<ClientValidationResult.Success>(result)
    }

    @Test
    fun `pre_registered should fail if provider returns null`() = runTest {
        val context = RequestContext("unknown-app")
        val clientId = ClientIdPrefixParser.parse(context.clientId).getOrThrow()

        // This provider will not find the client
        val metadataProvider: suspend (String) -> String? = { null }

        val result = authenticator.authenticate(clientId, context, metadataProvider)

        assertIs<ClientValidationResult.Failure>(result)
        assertIs<ClientIdError.PreRegisteredClientNotFound>(result.error)
    }

}
