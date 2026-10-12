package id.walt.openid4vci.proofs.jwt

import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.resolver.DidDocumentCrypto2KeyResolver
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.CredentialProofHandlerResult
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.CredentialProofValidationException
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.VerifiedJwtProof
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.openid4vci.proofs.attestation.toVerificationOptions
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Instant

class JwtCredentialProofHandlerTest {
    @Test
    fun `DID proof resolves the signing method beside an encryption method`() = runTest {
        val fixture = didProofFixture()

        val evidence = assertIs<VerifiedJwtProof>(fixture.verify(SIGNING_METHOD_ID).evidence)

        assertEquals(KeyId(SIGNING_METHOD_ID), evidence.holderKey.id)
    }

    @Test
    fun `DID proof rejects encryption kid even when signed by the authentication key`() = runTest {
        val fixture = didProofFixture()

        val failure = assertFailsWith<CredentialProofValidationException> {
            fixture.verify(ENCRYPTION_METHOD_ID)
        }

        assertEquals(CredentialErrorCodes.INVALID_PROOF, failure.errorCode)
    }

    @Test
    fun `DID proof accepts signature method not referenced by authentication`() = runTest {
        val fixture = didProofFixture(authenticationMethodId = AUTHENTICATION_METHOD_ID)

        val evidence = assertIs<VerifiedJwtProof>(fixture.verify(SIGNING_METHOD_ID).evidence)

        assertEquals(KeyId(SIGNING_METHOD_ID), evidence.holderKey.id)
        assertNotEquals(KeyId(AUTHENTICATION_METHOD_ID), evidence.holderKey.id)
    }

    @Test
    fun `DID proof rejects kid that matches no verification method`() = runTest {
        val fixture = didProofFixture(includeAuthenticationMethod = false)

        val failure = assertFailsWith<CredentialProofValidationException> {
            fixture.verify("$DID#legacy-thumbprint")
        }

        assertEquals(CredentialErrorCodes.INVALID_PROOF, failure.errorCode)
    }

    @Test
    fun `DID proof resolves a verification method embedded in a relationship`() = runTest {
        val fixture = didProofFixture(
            includeAuthenticationMethod = false,
            embedSigningMethod = true,
        )

        val evidence = assertIs<VerifiedJwtProof>(fixture.verify(SIGNING_METHOD_ID).evidence)

        assertEquals(KeyId(SIGNING_METHOD_ID), evidence.holderKey.id)
    }

    @Test
    fun `DID proof resolves a verification method DID URL with a path`() = runTest {
        val methodId = "$DID/keys/signing"
        val fixture = didProofFixture(
            signingMethodId = methodId,
            includeAuthenticationMethod = false,
        )

        val evidence = assertIs<VerifiedJwtProof>(fixture.verify(methodId).evidence)

        assertEquals(DID, evidence.holderDid)
        assertEquals(KeyId(methodId), evidence.holderKey.id)
    }

    @Test
    fun `plain JWT proof requires the issuer identifier as a string audience`() = runTest {
        checkAudienceValidation(withAttestation = false)
    }

    @Test
    fun `JWT proof with nested attestation requires the issuer identifier as a string audience`() = runTest {
        checkAudienceValidation(withAttestation = true)
    }

    private suspend fun checkAudienceValidation(withAttestation: Boolean) {
        val now = Instant.fromEpochSeconds(1_800_000_000)
        val issuer = "https://issuer.example"
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val holder = key("holder")
        val attester = if (withAttestation) key("attester") else null
        val attestation = attester?.let {
            CompactJws.sign(buildJsonObject {
                put("iat", now.epochSeconds)
                put("exp", now.epochSeconds + 300)
                put("attested_keys", JsonArray(listOf(holder.exportPublicJwkObject())))
            }.toString().encodeToByteArray(), it, JwsAlgorithm.ES256,
                buildJsonObject { put("typ", "key-attestation+jwt") })
        }
        val context = CredentialProofValidationContext(
            credentialIssuer = issuer,
            keyAttestation = attester?.let {
                KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(it.exportPublicJwkObject())).toVerificationOptions()
            },
        )
        val metadata = ProofTypeMetadata(setOf("ES256"))
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            vct = "test",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(ProofType.JWT.value to metadata),
        )
        val handler = JwtCredentialProofHandler(now = { now })
        suspend fun verify(audience: JsonElement?) = handler.verify(
            proof = JsonPrimitive(CompactJws.sign(buildJsonObject {
                audience?.let { put("aud", it) }
                put("iat", now.epochSeconds)
            }.toString().encodeToByteArray(), holder, JwsAlgorithm.ES256, buildJsonObject {
                put("typ", "openid4vci-proof+jwt")
                put("jwk", holder.exportPublicJwkObject())
                attestation?.let { put("key_attestation", it) }
            })),
            proofMetadata = metadata,
            configuration = configuration,
            context = context,
        )

        val evidence = assertIs<VerifiedJwtProof>(verify(JsonPrimitive(issuer)).evidence)
        assertEquals(JsonPrimitive(issuer), evidence.payload["aud"])
        assertEquals(withAttestation, evidence.keyAttestation != null)
        for (audience in listOf(
            null, JsonNull, JsonPrimitive(""), JsonPrimitive(" "), JsonPrimitive(123), JsonPrimitive(true),
            JsonPrimitive("https://other.example"), JsonPrimitive("$issuer/"), JsonPrimitive("https://ISSUER.example"),
            JsonObject(emptyMap()), JsonArray(emptyList()),
            JsonArray(listOf(JsonPrimitive(issuer))),
            JsonArray(listOf(JsonPrimitive("https://other.example"), JsonPrimitive(issuer))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonPrimitive(123))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonPrimitive(true))),
            JsonArray(listOf(JsonPrimitive(issuer), JsonNull)),
        )) {
            val failure = assertFailsWith<CredentialProofValidationException>("aud=$audience") { verify(audience) }
            assertEquals(CredentialErrorCodes.INVALID_PROOF, failure.errorCode)
        }
    }

    private suspend fun didProofFixture(
        signingMethodId: String = SIGNING_METHOD_ID,
        authenticationMethodId: String = SIGNING_METHOD_ID,
        includeAuthenticationMethod: Boolean = true,
        embedSigningMethod: Boolean = false,
    ): DidProofFixture {
        val now = Instant.fromEpochSeconds(1_800_000_000)
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun key(id: String) = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                KeyId(id),
                KeySpec.Ec(EcCurve.P256),
                setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val signingKey = key("signing-private")
        val encryptionKey = key("encryption-private")
        val authenticationKey = if (includeAuthenticationMethod && authenticationMethodId != SIGNING_METHOD_ID) {
            key("authentication-private")
        } else {
            null
        }
        val encryptionJwk = JsonObject(
            encryptionKey.exportPublicJwkObject().filterKeys { it !in setOf("use", "key_ops", "alg") } +
                mapOf(
                    "use" to JsonPrimitive("enc"),
                    "alg" to JsonPrimitive("ECDH-ES+A256KW"),
                )
        )
        val document = buildJsonObject {
            put("id", DID)
            put("verificationMethod", buildJsonArray {
                if (!embedSigningMethod) {
                    add(verificationMethod(signingMethodId, signingKey.exportPublicJwkObject()))
                }
                add(verificationMethod(ENCRYPTION_METHOD_ID, encryptionJwk))
                authenticationKey?.let {
                    add(verificationMethod(AUTHENTICATION_METHOD_ID, it.exportPublicJwkObject()))
                }
            })
            if (embedSigningMethod) {
                put("assertionMethod", buildJsonArray {
                    add(verificationMethod(signingMethodId, signingKey.exportPublicJwkObject()))
                })
            }
            if (includeAuthenticationMethod) {
                put("authentication", buildJsonArray { add(JsonPrimitive(authenticationMethodId)) })
            }
            put("keyAgreement", buildJsonArray { add(JsonPrimitive(ENCRYPTION_METHOD_ID)) })
        }
        val didResolver = DidDocumentCrypto2KeyResolver(
            resolveDocument = { requestedDid ->
                require(requestedDid == DID) { "Unexpected DID resolution input: $requestedDid" }
                Result.success(document)
            },
            runtime = runtime,
        )
        val metadata = ProofTypeMetadata(setOf("ES256"))
        return DidProofFixture(
            now = now,
            signingKey = signingKey,
            handler = JwtCredentialProofHandler(
                now = { now },
                didKeyResolver = Crypto2JwtKeyResolver(didResolver = didResolver),
            ),
            metadata = metadata,
            configuration = CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                vct = "test",
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("example")),
                proofTypesSupported = mapOf(ProofType.JWT.value to metadata),
            ),
            context = CredentialProofValidationContext(credentialIssuer = ISSUER),
        )
    }

    private fun verificationMethod(id: String, jwk: JsonObject): JsonObject = buildJsonObject {
        put("id", id)
        put("type", "JsonWebKey2020")
        put("controller", DID)
        put("publicKeyJwk", jwk)
    }

    private data class DidProofFixture(
        val now: Instant,
        val signingKey: Key,
        val handler: JwtCredentialProofHandler,
        val metadata: ProofTypeMetadata,
        val configuration: CredentialConfiguration,
        val context: CredentialProofValidationContext,
    ) {
        suspend fun verify(kid: String): CredentialProofHandlerResult {
            val jwt = CompactJws.sign(
                buildJsonObject {
                    put("aud", ISSUER)
                    put("iat", now.epochSeconds)
                }.toString().encodeToByteArray(),
                signingKey,
                JwsAlgorithm.ES256,
                buildJsonObject {
                    put("typ", "openid4vci-proof+jwt")
                    put("kid", kid)
                },
            )
            return handler.verify(JsonPrimitive(jwt), metadata, configuration, context)
        }
    }

    private companion object {
        const val DID = "did:example:holder"
        const val SIGNING_METHOD_ID = "$DID#sig"
        const val ENCRYPTION_METHOD_ID = "$DID#enc"
        const val AUTHENTICATION_METHOD_ID = "$DID#auth"
        const val ISSUER = "https://issuer.example"
    }
}
