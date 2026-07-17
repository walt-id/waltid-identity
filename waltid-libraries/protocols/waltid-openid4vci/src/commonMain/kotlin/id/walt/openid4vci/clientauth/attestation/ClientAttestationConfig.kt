package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.Key
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifier
import id.walt.openid4vci.clientauth.attestation.verifier.KeyBasedClientAttestationVerifier
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

class ClientAttestationConfig(
    private val attestationVerifier: ClientAttestationVerifier,
    private val acceptedAttestationSigningAlgorithms: Set<String> =
        ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
    private val acceptedPopSigningAlgorithms: Set<String> =
        ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
    private val clock: () -> Instant = { Clock.System.now() },
    private val clockSkewSeconds: Long = 60,
    private val popMaxAgeSeconds: Long = 300,
) {
    constructor(
        trustedAttesterKeys: suspend (
            header: JsonObject,
            payload: JsonObject,
        ) -> List<Key>,
        acceptedAttestationSigningAlgorithms: Set<String> =
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
        acceptedPopSigningAlgorithms: Set<String> =
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
        clock: () -> Instant = { Clock.System.now() },
        clockSkewSeconds: Long = 60,
        popMaxAgeSeconds: Long = 300,
    ) : this(
        attestationVerifier = KeyBasedClientAttestationVerifier(trustedAttesterKeys),
        acceptedAttestationSigningAlgorithms = acceptedAttestationSigningAlgorithms,
        acceptedPopSigningAlgorithms = acceptedPopSigningAlgorithms,
        clock = clock,
        clockSkewSeconds = clockSkewSeconds,
        popMaxAgeSeconds = popMaxAgeSeconds,
    )

    fun toAuthenticationMethod(): AttestationBasedClientAuthenticationMethod =
        AttestationBasedClientAuthenticationMethod(
            attestationVerifier = attestationVerifier,
            acceptedAttestationSigningAlgorithms = acceptedAttestationSigningAlgorithms,
            acceptedPopSigningAlgorithms = acceptedPopSigningAlgorithms,
            clock = clock,
            clockSkewSeconds = clockSkewSeconds,
            popMaxAgeSeconds = popMaxAgeSeconds,
        )
}
