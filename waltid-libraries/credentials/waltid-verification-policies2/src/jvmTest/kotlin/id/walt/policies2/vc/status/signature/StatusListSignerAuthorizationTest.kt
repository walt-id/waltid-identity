package id.walt.policies2.vc.status.signature

import id.walt.credentials.formats.W3C11
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.crypto2.keys.Key
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.test.assertTrue

class StatusListSignerAuthorizationTest {

    @Test
    fun `direct trust accepts a matching DID signer`() = runTest {
        authorizeStatusListSigner(
            request("did:example:issuer", JwtKeyResolutionSource.DID, "did:example:issuer"),
            authorizer = null,
        )
    }

    @Test
    fun `missing credential issuer explains that direct trust cannot match the signer`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(credentialIssuer = null, JwtKeyResolutionSource.DID, "did:example:status"),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "Status-list signer is not authorized")
        assertContains(error.message!!, "no issuer claim")
        assertContains(error.message!!, "direct trust cannot match")
        assertContains(error.message!!, "credential issuer=none (no iss/issuer claim)")
        assertContains(error.message!!, "separate Status Provider is not accepted")
    }

    @Test
    fun `x5c signed status lists explain that direct trust is required`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request("did:example:issuer", JwtKeyResolutionSource.X5C, signerIdentifier = null),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "x5c-signed status lists are not accepted under direct trust")
        assertContains(error.message!!, "source=X5C")
        assertContains(error.message!!, "same DID or https issuer")
    }

    @Test
    fun `mismatched DID signer names both identities`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request("did:example:issuer", JwtKeyResolutionSource.DID, "did:example:attacker"),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "does not match credential issuer")
        assertContains(error.message!!, "did:example:attacker")
        assertContains(error.message!!, "did:example:issuer")
    }

    @Test
    fun `inline JWK explains that it establishes no signer identity`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request("did:example:issuer", JwtKeyResolutionSource.INLINE_JWK, "did:example:issuer"),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "inline JWK does not establish a trusted status-list signer identity")
    }

    @Test
    fun `custom authorizer rejection does not mention direct trust`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request("did:example:issuer", JwtKeyResolutionSource.X5C, signerIdentifier = null),
                authorizer = StatusListSignerAuthorizer { false },
            )
        }
        assertContains(error.message!!, "configured status-list signer authorizer rejected this signer")
        assertTrue(!error.message!!.contains("Direct trust"))
        assertTrue(!error.message!!.contains("separate Status Provider"))
    }

    @Test
    fun `issuer can be read from credentialData issuer id`() = runTest {
        authorizeStatusListSigner(
            request(
                credentialIssuer = null,
                source = JwtKeyResolutionSource.DID,
                signerIdentifier = "did:example:issuer",
                credentialDataIssuer = "did:example:issuer",
            ),
            authorizer = null,
        )
    }

    private fun request(
        credentialIssuer: String?,
        source: JwtKeyResolutionSource,
        signerIdentifier: String?,
        credentialDataIssuer: String? = null,
    ): StatusListSignerAuthorizationRequest {
        val credentialData = buildJsonObject {
            credentialDataIssuer?.let {
                putJsonObject("issuer") { put("id", it) }
            }
        }
        return StatusListSignerAuthorizationRequest(
            referencedCredential = W3C11(
                credentialData = credentialData,
                signature = null,
                signed = null,
                issuer = credentialIssuer,
            ),
            statusListUri = "https://status.example/list/1",
            signer = ResolvedJwtVerificationKey(
                key = mockk<Key>(),
                source = source,
                signerIdentifier = signerIdentifier,
                keyId = null,
            ),
        )
    }
}
