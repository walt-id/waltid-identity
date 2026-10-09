package id.walt.verifier2.sdjwt

import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.crypto2.jose.InvalidJwsSignatureException
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SdJwtVcTestFixtureTest {
    @Test
    fun `fixture timestamps follow issuance and only the corrupted signature fails`() = runTest {
        DidService.apply {
            registerResolver(LocalResolver())
            updateResolversForMethods()
        }
        val signaturePolicy = CredentialSignaturePolicy()
        for (issuedAt in listOf(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2040-01-01T00:00:00Z"))) {
            val credential = issueSdJwtVcForHolder(
                holderDid = "did:key:zDnaeYb7DakQWmYkrLkmsVERAazF5Ya1G5nxbSnQcLJZ8Cr17",
                issuedAt = issuedAt,
            )
            assertEquals(issuedAt.epochSeconds, credential.credentialData["iat"]!!.jsonPrimitive.long)
            assertEquals(issuedAt.epochSeconds, credential.credentialData["nbf"]!!.jsonPrimitive.long)
            assertEquals((issuedAt + 365.days).epochSeconds, credential.credentialData["exp"]!!.jsonPrimitive.long)
            assertTrue(signaturePolicy.verify(credential, PolicyExecutionContext.Empty).isSuccess)

            val invalidCredential = credential.withInvalidIssuerSignature()
            assertEquals(credential.credentialData, invalidCredential.credentialData)
            assertEquals(credential.originalCredentialData, invalidCredential.originalCredentialData)
            assertEquals(credential.disclosures, invalidCredential.disclosures)
            assertEquals(credential.signed!!.substringBeforeLast('.'), invalidCredential.signed!!.substringBeforeLast('.'))
            assertNotNull(invalidCredential.getSignerCrypto2Key())
            assertIs<InvalidJwsSignatureException>(
                signaturePolicy.verify(invalidCredential, PolicyExecutionContext.Empty).exceptionOrNull()
            )
        }
    }
}
