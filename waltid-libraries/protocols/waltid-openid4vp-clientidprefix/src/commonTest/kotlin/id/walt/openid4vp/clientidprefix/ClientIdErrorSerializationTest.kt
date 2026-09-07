package id.walt.openid4vp.clientidprefix

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ClientValidationResult] is `@Serializable` so that a client-authentication failure can be reported
 * over the wire. kotlinx.serialization does not inherit `@Serializable` from a sealed parent, so the
 * parameterless [ClientIdError] subtypes used to lack a serializer and threw at runtime - for exactly
 * the errors the X.509 prefixes raise most often.
 */
class ClientIdErrorSerializationTest {

    private val errors = listOf(
        ClientIdError.MissingRequestObject,
        ClientIdError.InvalidSignature,
        ClientIdError.DoesNotSupportSignature,
        ClientIdError.InvalidJws,
        ClientIdError.MissingX5cHeader,
        ClientIdError.EmptyX5cHeader,
        ClientIdError.MissingClientMetadata,
        ClientIdError.CannotExtractSanDnsNamesFromDer,
        ClientIdError.X509HashMismatch,
        ClientIdError.MissingX509TrustAnchors,
        ClientIdError.RedirectUriHostMismatch("verifier.example.com", "other.example.com"),
        ClientIdError.DidResolutionFailed("not resolvable"),
        ClientIdError.AttestationError("expired"),
        ClientIdError.FederationError("no trust chain"),
        ClientIdError.PreRegisteredClientNotFound("client-1"),
        ClientIdError.UnsupportedPrefix("openid_federation"),
        ClientIdError.InvalidMetadata("no verification keys"),
        ClientIdError.SanDnsMismatch("verifier.example.com", listOf("other.example.com")),
    )

    @Test
    fun `every ClientIdError round-trips through JSON`() {
        errors.forEach { error ->
            val encoded = Json.encodeToString(ClientIdError.serializer(), error)
            assertEquals(error, Json.decodeFromString(ClientIdError.serializer(), encoded), "for $encoded")
        }
    }

    @Test
    fun `a failure result round-trips through JSON`() {
        errors.forEach { error ->
            val failure = ClientValidationResult.Failure(error)
            val encoded = Json.encodeToString(ClientValidationResult.serializer(), failure)
            assertEquals(failure, Json.decodeFromString(ClientValidationResult.serializer(), encoded), "for $encoded")
        }
    }
}
