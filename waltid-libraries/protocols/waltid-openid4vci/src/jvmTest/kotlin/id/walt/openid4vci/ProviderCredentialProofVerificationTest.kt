package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.DefaultCredentialProofVerifier
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProviderCredentialProofVerificationTest {

    @Test
    fun `createCredentialResponse rejects invalid proof signature before issuing credential`() = runTest {
        val provider = buildOAuth2Provider(
            createTestConfig(
                credentialProofVerifier = DefaultCredentialProofVerifier(now = { NOW }),
            ),
        )
        val holderKey = JWKKey.generate(KeyType.secp256r1)
        val proofJwt = tamperSignature(createProof(holderKey))
        val proofParam = buildJsonObject {
            put("jwt", JsonArray(listOf(JsonPrimitive(proofJwt))))
        }.toString()

        val requestResult = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf(CREDENTIAL_CONFIGURATION_ID),
                "proofs" to listOf(proofParam),
            ),
        )
        assertTrue(requestResult is CredentialRequestResult.Success)

        val responseResult = provider.createCredentialResponse(
            request = requestResult.request,
            configuration = credentialConfiguration(),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "did:example:issuer",
            credentialData = buildJsonObject {
                put("given_name", "Alice")
            },
            proofValidationContext = CredentialProofValidationContext(
                credentialIssuer = CREDENTIAL_ISSUER,
                clientId = "client",
            ),
        )

        assertTrue(responseResult is CredentialResponseResult.Failure)
        assertEquals(CredentialErrorCodes.INVALID_PROOF, responseResult.error.error)
    }

    private suspend fun createProof(key: JWKKey): String = key.signJws(
        plaintext = buildJsonObject {
            put(JwtPayloadClaims.AUDIENCE, CREDENTIAL_ISSUER)
            put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
        }.toString().encodeToByteArray(),
        headers = mapOf(
            JwtHeaderParams.TYPE to JsonPrimitive("openid4vci-proof+jwt"),
            JwtHeaderParams.JSON_WEB_KEY to key.getPublicKey().exportJWKObject(),
        ),
    )

    private fun credentialConfiguration() = CredentialConfiguration(
        format = CredentialFormat.SD_JWT_VC,
        vct = CREDENTIAL_CONFIGURATION_ID,
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
        proofTypesSupported = mapOf(
            "jwt" to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
        ),
    )

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split(".")
        check(parts.size == 3)
        val replacement = if (parts[2].first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${parts[2].drop(1)}"
    }

    private companion object {
        const val CREDENTIAL_ISSUER = "https://issuer.example"
        const val CREDENTIAL_CONFIGURATION_ID = "identity_credential"
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
    }
}
