package id.walt.openid4vci.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.*
import id.walt.openid4vci.clientauth.attestation.VerifiedClientAttestation
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialProtocolException
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInputProvider
import id.walt.openid4vci.metadata.issuer.*
import id.walt.openid4vci.proofs.*
import id.walt.openid4vci.proofs.attestation.*
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock

class InitialAttestationStatusTest {
    private val time = Clock.System.now()
    private val issuer = "https://issuer.example"
    private val uri = "https://attester.example/list"
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    @Test fun tokenWriterUses400ForTemporaryStatusWithoutChangingOtherEndpoints() {
        val provider = buildOAuth2Provider(createTestConfig())
        val error = OAuthError(OAuthErrorCodes.TEMPORARILY_UNAVAILABLE, "Status unavailable")
        val response = provider.writeAccessTokenError(error)
        assertEquals(400, response.status)
        assertEquals("temporarily_unavailable", response.payload.getValue("error").jsonPrimitive.content)
        assertFalse("access_token" in response.payload)
        assertEquals(500, provider.writePushedAuthorizationError(error).status)
        assertEquals(500, provider.writeAccessTokenError(OAuthError(OAuthErrorCodes.SERVER_ERROR)).status)
    }

    @Test fun suspendedOrUnavailableStatusStillCapturesEveryVerifiedProof() = runTest {
        for (unavailable in listOf(false, true)) {
            val fixture = fixture()
            fixture.unavailable = unavailable
            if (!unavailable) fixture.states[1] = 2
            val accepted = fixture.accept()
            assertEquals(3, accepted.bindings.size)
            assertEquals(3, accepted.proofs!!.size)
            assertEquals((1..3).toList(), accepted.proofs!!.map { it.keyAttestation!!.status!!.reference!!.index })
            assertEquals(0, accepted.clientAttestation!!.status!!.reference!!.index)
            assertNull(accepted.request.proofs)
            assertEquals(1, fixture.fetches, "The complete accepted batch shares one fresh list lookup")
            assertEquals(accepted, Json.decodeFromString<AcceptedCredentialRequest>(Json.encodeToString(accepted)))
        }
    }

    @Test fun temporaryStatusCannotHideAnInvalidLaterProofSignature() = runTest {
        val fixture = fixture()
        fixture.unavailable = true
        val failure = assertFailsWith<CredentialProtocolException> {
            fixture.accept(fixture.request(invalidSignatureIndex = 2))
        }
        assertEquals("invalid_proof", failure.error.error)
        assertEquals(0, fixture.fetches, "All proof signatures must be verified before status authorization")
    }

    @Test fun temporaryStatusCannotHideAnInvalidLaterNonceOrMissingRequiredReference() = runTest {
        for (missingReference in listOf(false, true)) {
            val fixture = fixture()
            fixture.unavailable = true
            val failure = assertFailsWith<CredentialProtocolException> {
                fixture.accept(fixture.request(
                    invalidNonceIndex = if (missingReference) null else 2,
                    missingReferenceIndex = if (missingReference) 2 else null,
                ))
            }
            assertEquals(if (missingReference) "invalid_proof" else "invalid_nonce", failure.error.error)
            assertEquals(0, fixture.fetches)
        }
    }

    @Test fun confirmedRevocationWinsOverSuspensionInTheInitialBatch() = runTest {
        val fixture = fixture()
        fixture.states[1] = 2
        fixture.states[3] = 1
        val failure = assertFailsWith<CredentialProtocolException> { fixture.accept() }
        assertEquals("credential_request_denied", failure.error.error)
        assertNull(failure.error.retryAfterSeconds)
        assertEquals(1, fixture.fetches)
    }

    @Test fun initialStatusCancellationPropagates() = runTest {
        val fixture = fixture()
        fixture.cancelLookup = true
        assertFailsWith<CancellationException> { fixture.accept() }
    }

    @Test fun directProofVerificationStillRequiresValidStatus() = runTest {
        val fixture = fixture()
        fixture.states[1] = 2
        assertFailsWith<CredentialProtocolException> {
            DefaultCredentialProofVerifier(now = { time }).verify(fixture.request(), fixture.configuration, fixture.context)
        }
        assertEquals(1, fixture.fetches)
    }

    @Test fun nonDeferredStatusFailureReturnsDefinedDenialBeforeSigning() = runTest {
        val fixture = fixture()
        fixture.unavailable = true
        var requestedInputs = false
        val result = fixture.provider.createCredentialResponse(
            fixture.request(), fixture.configuration, Crypto2CredentialSigningKey.select(fixture.signer, fixture.configuration), issuer,
            CredentialIssuanceInputProvider { requestedInputs = true; emptyList() }, proofValidationContext = fixture.context,
        )
        val failure = assertIs<CredentialResponseResult.Failure>(result)
        val http = fixture.provider.writeCredentialError(failure.error)
        assertEquals(400, http.status)
        assertEquals("credential_request_denied", http.payload.getValue("error").jsonPrimitive.content)
        assertFalse("transaction_id" in http.payload)
        assertFalse("Retry-After" in http.headers)
        assertFalse(requestedInputs)
    }

    private suspend fun fixture() = Fixture(key("attester"), List(3) { key("holder-$it") }, key("other"))

    private inner class Fixture(val signer: Key, val holders: List<Key>, val other: Key) {
        val states = mutableListOf(0, 0, 0, 0)
        var unavailable = false
        var cancelLookup = false
        var fetches = 0
        val resolver = AttestationStatusResolver {
            fetches++
            if (cancelLookup) throw CancellationException("Cancelled initial status lookup")
            if (unavailable) error("Status unavailable")
            CompactJws.sign(buildJsonObject {
                put("sub", uri); put("iat", time.epochSeconds); put("exp", time.epochSeconds + 3600)
                putJsonObject("status_list") {
                    put("bits", 8); put("lst", TokenStatusListCodec.encode(states.map(Int::toByte).toByteArray()))
                }
            }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") })
        }
        val provider = buildOAuth2Provider(createTestConfig().copy(attestationStatusResolver = resolver))
        val configuration = CredentialConfiguration(
            CredentialFormat.SD_JWT_VC, vct = "identity",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf("jwt" to ProofTypeMetadata(setOf("ES256"), KeyAttestationsRequired())),
        )
        private val nonceService = object : CredentialNonceService {
            override suspend fun issue(binding: CredentialNonceBinding) = IssuedCredentialNonce("nonce")
            override suspend fun validate(nonce: String, binding: CredentialNonceBinding) =
                if (nonce == "nonce") CredentialNonceValidationResult.VALID else CredentialNonceValidationResult.INVALID
        }
        val context = CredentialProofValidationContext(
            issuer, clientId = "wallet", batchCredentialIssuance = BatchCredentialIssuance(3),
            nonceValidation = CredentialNonceValidationContext(nonceService, CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")),
            keyAttestation = KeyAttestationVerificationOptions(
                KeyAttestationTrustResolver { _, _, _ -> listOf(signer) },
                statusPolicy = AttestationStatusPolicy(requireStatusReference = true), statusResolver = resolver,
            ),
        )

        private fun reference(index: Int) = buildJsonObject {
            putJsonObject("status_list") { put("uri", uri); put("idx", index) }
        }

        suspend fun request(
            invalidSignatureIndex: Int? = null,
            invalidNonceIndex: Int? = null,
            missingReferenceIndex: Int? = null,
        ): CredentialRequest {
            val proofs = holders.mapIndexed { index, holder ->
                val attestation = CompactJws.sign(buildJsonObject {
                    put("iat", time.epochSeconds); put("exp", time.epochSeconds + 3600); put("nonce", "nonce")
                    put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
                    if (index != missingReferenceIndex) put("status", reference(index + 1))
                }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "key-attestation+jwt") })
                CompactJws.sign(buildJsonObject {
                    put("iss", "wallet"); put("aud", issuer); put("iat", time.epochSeconds)
                    put("nonce", if (index == invalidNonceIndex) "invalid" else "nonce")
                }.toString().encodeToByteArray(), if (index == invalidSignatureIndex) other else holder, JwsAlgorithm.ES256, buildJsonObject {
                    put("typ", "openid4vci-proof+jwt"); put("jwk", holder.exportPublicJwkObject()); put("key_attestation", attestation)
                })
            }
            val wia = VerifiedClientAttestation(
                "wallet", holders.first().exportPublicJwkObject(),
                VerifiedAttestationLifetime(time.epochSeconds, time.epochSeconds + 3600, clockSkewSeconds = 60),
                captureAttestationStatus(buildJsonObject { put("status", reference(0)) }, signer.exportPublicJwkObject(), AttestationStatusPolicy(true), time),
            )
            return AuthenticatedCredentialRequest(DefaultCredentialRequest(
                client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = null, credentialConfigurationId = "identity", proofs = Proofs(jwt = proofs),
                credentialResponseEncryption = null, accessTokenClientId = "wallet",
            ), CredentialRequestAuthentication("wallet", false, wia, true))
        }

        suspend fun accept(request: CredentialRequest? = null) = provider.acceptCredentialRequest(
            request ?: this.request(), configuration, "credential", "identity", context,
        )
    }
}
