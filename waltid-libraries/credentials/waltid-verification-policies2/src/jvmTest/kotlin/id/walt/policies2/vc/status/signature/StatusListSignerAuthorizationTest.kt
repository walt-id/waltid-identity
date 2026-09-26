package id.walt.policies2.vc.status.signature

import id.walt.credentials.formats.W3C11
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto2.keys.Key
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.encoding.Base64
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
    fun `direct trust accepts a matching x5c leaf on an external status list`() = runTest {
        authorizeStatusListSigner(
            x5cRequest(credentialLeaf = MATCHING_LEAF, statusListLeaf = MATCHING_LEAF),
            authorizer = null,
        )
    }

    @Test
    fun `direct trust accepts matching x5c even when a custom authorizer rejects`() = runTest {
        authorizeStatusListSigner(
            x5cRequest(credentialLeaf = MATCHING_LEAF, statusListLeaf = MATCHING_LEAF),
            authorizer = StatusListSignerAuthorizer { false },
        )
    }

    @Test
    fun `x5c signed status list with a different leaf is rejected`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                x5cRequest(credentialLeaf = MATCHING_LEAF, statusListLeaf = OTHER_LEAF),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "status-list x5c leaf does not match")
        assertContains(error.message!!, "source=X5C")
        assertContains(error.message!!, "same DID, https issuer, or x5c leaf")
    }

    @Test
    fun `x5c signed status list is rejected when the credential has no chain`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                request(
                    credentialIssuer = "did:example:issuer",
                    source = JwtKeyResolutionSource.X5C,
                    signerIdentifier = null,
                    statusListCertificates = listOf(MATCHING_LEAF),
                ),
                authorizer = null,
            )
        }
        assertContains(error.message!!, "status-list x5c leaf does not match")
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
        assertContains(error.message!!, "DID/https direct trust cannot match")
        assertContains(error.message!!, "credential issuer=none (no iss/issuer claim)")
        assertContains(error.message!!, "separate Status Provider")
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
    fun `custom authorizer rejection is mentioned when direct trust also fails`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            authorizeStatusListSigner(
                x5cRequest(credentialLeaf = MATCHING_LEAF, statusListLeaf = OTHER_LEAF),
                authorizer = StatusListSignerAuthorizer { false },
            )
        }
        assertContains(error.message!!, "status-list x5c leaf does not match")
        assertContains(error.message!!, "configured status-list signer authorizer also did not authorize")
        assertTrue(!error.message!!.contains("A separate Status Provider"))
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

    private fun x5cRequest(credentialLeaf: String, statusListLeaf: String) = request(
        credentialIssuer = null,
        source = JwtKeyResolutionSource.X5C,
        signerIdentifier = null,
        statusListCertificates = listOf(statusListLeaf),
        credentialCertificates = listOf(credentialLeaf),
    )

    private fun request(
        credentialIssuer: String?,
        source: JwtKeyResolutionSource,
        signerIdentifier: String?,
        credentialDataIssuer: String? = null,
        statusListCertificates: List<String> = emptyList(),
        credentialCertificates: List<String> = emptyList(),
    ): StatusListSignerAuthorizationRequest {
        val credentialData = buildJsonObject {
            credentialDataIssuer?.let {
                putJsonObject("issuer") { put("id", it) }
            }
        }
        val signature = credentialCertificates.takeIf { it.isNotEmpty() }?.let { chain ->
            JwtCredentialSignature(
                signature = "test",
                jwtHeader = buildJsonObject {
                    put("x5c", JsonArray(chain.map(::JsonPrimitive)))
                },
            )
        }
        return StatusListSignerAuthorizationRequest(
            referencedCredential = W3C11(
                credentialData = credentialData,
                signature = signature,
                signed = null,
                issuer = credentialIssuer,
            ),
            statusListUri = "https://status.example/list/1",
            signer = ResolvedJwtVerificationKey(
                key = mockk<Key>(),
                source = source,
                signerIdentifier = signerIdentifier,
                keyId = null,
                certificateChain = statusListCertificates,
            ),
        )
    }

    companion object {
        private val MATCHING_LEAF = Base64.encode(byteArrayOf(1, 2, 3, 4))
        private val OTHER_LEAF = Base64.encode(byteArrayOf(9, 9, 9, 9))
    }
}
