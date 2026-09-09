@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An `x509_hash` client identifier is not an independent setting: it *is* the base64url SHA-256 of the
 * DER encoding of the `x5c` leaf. A mismatch is invisible to the verifier - the request signs and is
 * served normally, and the wallet rejects it as `X509HashMismatch` - so it has to be caught at session
 * creation.
 */
class VerificationSessionCreatorX509HashTest {

    /** `verifier.example.com` self-signed P-256 leaf, the same certificate shipped in `verifier-service.conf`. */
    private val leafCertificateBase64 =
        "MIIBVzCB/aADAgECAggNKZAvUrtimzAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTAeFw0yNT" +
            "EwMTQwNjI0MjBaFw0yNjEwMTQwNjI0MjBaMB8xHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0C" +
            "AQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y" +
            "+sBzK0ZR7jPqMjMCEwHwYDVR0RBBgwFoIUdmVyaWZpZXIuZXhhbXBsZS5jb20wCgYIKoZIzj0EAwIDSQAwRgIhAOu0RGM6" +
            "BjVQUepeLBogw+ZD3MQ9vFppbPIGMPjtn/qdAiEAttfdfyXHfzJ2tr+Pczyckzv3NlM43461cvP96sIzOQA="

    /** Independently computed with `openssl x509 -outform DER | sha256sum`, then base64url-encoded. */
    private val leafCertificateHash = "OPpTDyXlg6WRu2-Qn4rpQcA9uVqSrNExCS8kCYUe09A"

    private val urlPrefix = "https://verifier.example.com/v1/org.tenant.verifier/verifier2-service-api"

    private fun signedSetup() = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(typeValues = listOf(listOf("OpenBadgeCredential"))),
                    )
                )
            ),
            signedRequest = true,
        )
    )

    private suspend fun createSession(clientId: String?, x5c: List<String>?) =
        VerificationSessionCreator.createVerificationSession(
            setup = signedSetup(),
            clientId = clientId,
            urlPrefix = urlPrefix,
            urlHost = "openid4vp://authorize",
            key = JWKKey.generate(KeyType.secp256r1),
            x5c = x5c,
        )

    @Test
    fun `the derivation matches the independently computed hash`() {
        assertEquals(leafCertificateHash, X509Hash.hashOfCertificate(Base64.decode(leafCertificateBase64)))
    }

    @Test
    fun `a matching x509_hash client id is accepted`() = runTest {
        val session = createSession("x509_hash:$leafCertificateHash", listOf(leafCertificateBase64))

        assertEquals("x509_hash:$leafCertificateHash", session.authorizationRequest.clientId)
        assertNotNull(session.signedAuthorizationRequestJwt)
    }

    @Test
    fun `a mismatching x509_hash client id is rejected and names the expected value`() = runTest {
        val wrongHash = "nPBr2E1pcPo5ga85kAngpdyzqK4EOLuG40lJSwL4Cdk"
        val failure = assertFailsWith<IllegalArgumentException> {
            createSession("x509_hash:$wrongHash", listOf(leafCertificateBase64))
        }

        assertTrue(failure.message!!.contains("x509_hash:$leafCertificateHash"), failure.message)
    }

    @Test
    fun `an x509_hash client id without a certificate chain is rejected`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            createSession("x509_hash:$leafCertificateHash", x5c = null)
        }

        assertTrue(failure.message!!.contains("x5c must be configured"), failure.message)
    }

    @Test
    fun `an x509_hash client id with a non-base64 certificate is rejected`() = runTest {
        val failure = assertFailsWith<IllegalArgumentException> {
            createSession("x509_hash:$leafCertificateHash", listOf("not base64!"))
        }

        assertTrue(failure.message!!.contains("not valid base64"), failure.message)
    }

    @Test
    fun `other client id prefixes are unaffected`() = runTest {
        val session = createSession("x509_san_dns:verifier.example.com", listOf(leafCertificateBase64))

        assertEquals("x509_san_dns:verifier.example.com", session.authorizationRequest.clientId)
    }
}
