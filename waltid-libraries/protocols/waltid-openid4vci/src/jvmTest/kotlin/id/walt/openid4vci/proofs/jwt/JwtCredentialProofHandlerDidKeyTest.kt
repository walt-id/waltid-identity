package id.walt.openid4vci.proofs.jwt

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.CredentialProofValidationContext
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.VerifiedJwtProof
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class JwtCredentialProofHandlerDidKeyTest {
    @Test
    fun `did key proof with verification method kid is accepted`() = runTest {
        DidService.minimalInit()
        val holderKey = JWKKey.generate(KeyType.Ed25519)
        val holderDid = DidService.registerByKey("key", holderKey).did
        val methodId = "$holderDid#${holderDid.removePrefix("did:key:")}"

        val evidence = assertIs<VerifiedJwtProof>(verify(holderKey, methodId).evidence)

        assertEquals(holderDid, evidence.holderDid)
    }

    @Test
    fun `did key proof with legacy thumbprint kid is accepted by the deprecated fallback`() = runTest {
        DidService.minimalInit()
        val holderKey = JWKKey.generate(KeyType.Ed25519)
        val holderDid = DidService.registerByKey("key", holderKey).did

        val evidence = assertIs<VerifiedJwtProof>(verify(holderKey, "$holderDid#${holderKey.getKeyId()}").evidence)

        assertEquals(holderDid, evidence.holderDid)
    }

    private suspend fun verify(holderKey: JWKKey, kid: String) = JwtCredentialProofHandler().verify(
        JsonPrimitive(
            holderKey.signJws(
                plaintext = buildJsonObject {
                    put("aud", ISSUER)
                    put("iat", Clock.System.now().epochSeconds)
                }.toString().encodeToByteArray(),
                headers = mapOf("kid" to JsonPrimitive(kid), "typ" to JsonPrimitive("openid4vci-proof+jwt")),
            )
        ),
        METADATA,
        CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            vct = "test",
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did("key")),
            proofTypesSupported = mapOf(ProofType.JWT.value to METADATA),
        ),
        CredentialProofValidationContext(credentialIssuer = ISSUER),
    )

    private companion object {
        const val ISSUER = "https://issuer.example"
        val METADATA = ProofTypeMetadata(setOf("EdDSA"))
    }
}
