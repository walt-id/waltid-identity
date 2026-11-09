package id.walt.openid4vci.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.clientauth.*
import id.walt.openid4vci.clientauth.attestation.*
import id.walt.openid4vci.clientauth.attestation.verifier.Crypto2KeyBasedClientAttestationVerifier
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AttestationStatusRegressionTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val start = Instant.fromEpochSeconds(1_800_000_000)
    private val wiaUri = "https://attester.example/wia"
    private val kaUri = "https://attester.example/ka"

    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun listJwt(signer: Key, uri: String, lifetime: Long = 3600, ttl: Long = 60, status: Byte = 0) = CompactJws.sign(
        buildJsonObject {
            put("sub", uri); put("iat", start.epochSeconds); put("exp", start.epochSeconds + lifetime); put("ttl", ttl)
            putJsonObject("status_list") { put("bits", 8); put("lst", TokenStatusListCodec.encode(byteArrayOf(status))) }
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") },
    )

    @Test fun allListsMustRemainFreshAfterTheLastLookup() = runTest {
        val signer = key("attester")
        val publicJwk = signer.exportPublicJwkObject()
        for (boundary in listOf("expiry", "maximum-age", "ttl", "still-valid")) {
            var now = start
            val first = listJwt(signer, wiaUri, lifetime = if (boundary == "expiry") 1 else 3600,
                ttl = if (boundary == "ttl") 1 else 60)
            val second = listJwt(signer, kaUri)
            val validator = AttestationStatusValidator(AttestationStatusResolver { uri ->
                if (uri == kaUri) { now += 2.seconds; second } else first
            }, now = { now })
            fun evidence(uri: String) = VerifiedAttestationStatus(capturedAt = start.epochSeconds,
                policy = AttestationStatusPolicy(true, if (uri == wiaUri && boundary == "maximum-age") 1 else 300),
                reference = AttestationStatusReference(uri, 0), attesterPublicJwk = publicJwk)
            val result = validator.validate(listOf(evidence(wiaUri) to false, evidence(kaUri) to false))
            assertEquals(if (boundary == "still-valid") AttestationStatusOutcome.VALID else AttestationStatusOutcome.UNVERIFIABLE,
                result.outcome, boundary)
            assertFalse(result.permanentlyDenied, boundary)
        }
    }

    @Test fun freshnessFailureDoesNotOverrideConfirmedRevocationFromAnotherList() = runTest {
        val signer = key("attester")
        val publicJwk = signer.exportPublicJwkObject()
        var now = start
        val first = listJwt(signer, wiaUri, lifetime = 1)
        val revoked = listJwt(signer, kaUri, status = 1)
        val validator = AttestationStatusValidator(AttestationStatusResolver { uri ->
            if (uri == kaUri) { now += 2.seconds; revoked } else first
        }, now = { now })
        val evidence = listOf(wiaUri, kaUri).map { uri -> VerifiedAttestationStatus(capturedAt = start.epochSeconds,
            policy = AttestationStatusPolicy(true), reference = AttestationStatusReference(uri, 0), attesterPublicJwk = publicJwk) to false }
        assertEquals(AttestationStatusOutcome.INVALID, validator.validate(evidence).outcome)
    }

    @Test fun relativeAndMalformedStatusUrisAreRejectedBeforeUrlNormalization() {
        for (uri in listOf("statuslists/1", "/statuslists/1", "//attester.example/statuslists/1",
            "https:/attester.example/list", "https:///list", "https://", "https://?query", "https://#fragment",
            " https://attester.example/list", "https://attester.example/list#", "https://attester.example/a b",
            "https://attester.example\\list", "ftp://attester.example/list")) {
            assertFailsWith<IllegalArgumentException>(uri) {
                captureAttestationStatus(referencePayload(uri), null, AttestationStatusPolicy(), start)
            }
        }
    }

    @Test fun absoluteStatusUrisKeepTheExactSignedReference() {
        for (uri in listOf(wiaUri, "HTTPS://attester.example/status/1?version=2", "http://localhost:8080/list", "http://[::1]:8080/list")) {
            assertEquals(uri, captureAttestationStatus(referencePayload(uri), null, AttestationStatusPolicy(), start).reference!!.uri)
        }
    }

    @Test fun optionalStatusOmissionDoesNotRequireOrInvokePublicKeyExport() = runTest {
        val signer = key("attester")
        val holder = key("holder")
        for (exporter in listOf(null, PublicKeyExporter { error("An omitted optional status must not invoke public export") })) {
            val verificationOnly = object : Key by signer {
                override val capabilities = signer.capabilities.copy(publicKeyExporter = exporter)
            }
            assertIs<ClientAuthenticationResult.Authenticated>(authenticate(verificationOnly, signer, holder))
        }
    }

    @Test fun missingStatusSignerContextIsRetryableAndNeverAnInvalidSignature() = runTest {
        val signer = key("attester")
        val holder = key("holder")
        for (exporter in listOf(null, PublicKeyExporter { error("Public export service unavailable") })) {
            val verificationOnly = object : Key by signer {
                override val capabilities = signer.capabilities.copy(publicKeyExporter = exporter)
            }
            val result = assertIs<ClientAuthenticationResult.Failure>(authenticate(verificationOnly, signer, holder, suppliedStatus = true))
            assertEquals("temporarily_unavailable", result.error.error)
        }
    }

    @Test fun requiredStatusOmissionStillFailsForVerificationOnlyKeys() = runTest {
        val signer = key("attester")
        val holder = key("holder")
        val verificationOnly = object : Key by signer {
            override val capabilities = signer.capabilities.copy(publicKeyExporter = null)
        }
        val result = assertIs<ClientAuthenticationResult.Failure>(authenticate(verificationOnly, signer, holder, requiredStatus = true))
        assertEquals("invalid_client", result.error.error)
        assertEquals("Attestation status reference is required", result.error.description)
    }

    @Test fun cancellationDuringRequiredSignerExportPropagates() = runTest {
        val signer = key("attester")
        val holder = key("holder")
        val cancelled = object : Key by signer {
            override val capabilities = signer.capabilities.copy(publicKeyExporter = PublicKeyExporter { throw CancellationException("cancelled") })
        }
        assertFailsWith<CancellationException> { authenticate(cancelled, signer, holder, suppliedStatus = true) }
    }

    private fun referencePayload(uri: String) = buildJsonObject {
        putJsonObject("status") { putJsonObject("status_list") { put("idx", 0); put("uri", uri) } }
    }

    private suspend fun authenticate(trustedKey: Key, signer: Key, holder: Key, suppliedStatus: Boolean = false, requiredStatus: Boolean = false): ClientAuthenticationResult {
        val holderJwk = holder.exportPublicJwkObject()
        val wia = CompactJws.sign(buildJsonObject {
            put("sub", "wallet"); put("iat", start.epochSeconds); put("exp", start.epochSeconds + 3600)
            putJsonObject("cnf") { put("jwk", holderJwk) }
            if (suppliedStatus) put("status", referencePayload(wiaUri).getValue("status"))
        }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "oauth-client-attestation+jwt") })
        val pop = CompactJws.sign(buildJsonObject {
            put("aud", "https://issuer.example"); put("iat", start.epochSeconds); put("jti", "pop")
        }.toString().encodeToByteArray(), holder, JwsAlgorithm.ES256, buildJsonObject { put("typ", "oauth-client-attestation-pop+jwt") })
        var fetches = 0
        val method = AttestationBasedClientAuthenticationMethod(
            Crypto2KeyBasedClientAttestationVerifier { _, _ -> listOf(trustedKey) }, clock = { start },
            statusPolicy = AttestationStatusPolicy(requiredStatus),
            statusResolver = AttestationStatusResolver { fetches++; error("Signer context is unavailable; no fetch should be attempted") },
        )
        val result = method.authenticate(ClientAuthenticationEndpoint.TOKEN, emptyMap(), mapOf(
            ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(wia), ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(pop),
        ), ClientAuthenticationContext(authorizationServerIssuer = "https://issuer.example"))
        assertEquals(0, fetches)
        return result
    }
}
