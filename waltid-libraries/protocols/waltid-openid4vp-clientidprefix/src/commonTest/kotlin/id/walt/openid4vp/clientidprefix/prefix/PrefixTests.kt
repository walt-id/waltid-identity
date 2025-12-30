package id.walt.openid4vp.clientidprefix.prefix

import id.walt.did.dids.DidService
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
        DidService.minimalInit()

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
