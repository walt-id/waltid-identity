package id.walt.openid4vp.clientidprefix

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.x509.CertificateDer
import id.walt.x509.platformSupportsPkixCertificatePathValidation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class X509AuthenticationFailureTest {

    private val clientId = X509Hash("hash", "x509_hash:hash")

    @Test
    fun `x509 hash reports malformed and missing certificate headers precisely`() = runTest {
        assertFailure(ClientIdError.InvalidJws, "not-a-jws")
        assertFailure(ClientIdError.MissingX5cHeader, jws("""{"alg":"ES256"}"""))
        assertFailure(ClientIdError.EmptyX5cHeader, jws("""{"alg":"ES256","x5c":[]}"""))
    }

    @Test
    fun `x509 hash requires wallet controlled trust policy`() = runTest {
        assertFailure(
            ClientIdError.MissingX509TrustPolicy,
            jws("""{"alg":"ES256","x5c":["AQID"]}"""),
        )
    }

    @Test
    fun `HAIP policy rejects trust anchor in x5c and leaf trust anchor`() = runTest {
        val anchor = CertificateDer(byteArrayOf(1, 2, 3))
        val requestObject = jws("""{"alg":"ES256","x5c":["AQID"]}""")

        assertFailure(
            ClientIdError.TrustAnchorIncludedInX5c,
            requestObject,
            X509TrustPolicy(
                trustAnchors = listOf(anchor),
                requireTrustAnchorOmittedFromX5c = true,
            ),
        )
        assertFailure(
            ClientIdError.SelfSignedLeafCertificate,
            requestObject,
            X509TrustPolicy(
                trustAnchors = listOf(anchor),
                rejectLeafTrustAnchor = true,
            ),
        )
    }

    @Test
    fun `trust policy rejects request object algorithm outside ecosystem profile`() = runTest {
        assertFailure(
            ClientIdError.UnsupportedRequestObjectAlgorithm("ES384"),
            jws("""{"alg":"ES384","x5c":["AQID"]}"""),
            X509TrustPolicy(
                trustAnchors = listOf(CertificateDer(byteArrayOf(4, 5, 6))),
                allowedRequestObjectAlgorithms = setOf("ES256"),
            ),
        )
    }

    private suspend fun assertFailure(
        expected: ClientIdError,
        requestObject: String,
        trustPolicy: X509TrustPolicy? = null,
    ) {
        val result = clientId.authenticateX509Hash(
            clientId,
            RequestContext(
                clientId = clientId.rawValue,
                requestObjectJws = requestObject,
                x509TrustPolicy = trustPolicy,
            ),
        )
        val failure = assertIs<ClientValidationResult.Failure>(result)
        // JS and iOS reject X.509 authentication at the platform capability boundary;
        // JVM continues through the detailed validation branch exercised by these cases.
        val expectedError = if (platformSupportsPkixCertificatePathValidation) {
            expected
        } else {
            ClientIdError.UnsupportedPlatformX509Validation
        }
        // Kotlin/JS does not preserve referential equality for serialized object subclasses,
        // so compare the stable public error contract rather than object identity.
        assertEquals(expectedError.message, failure.error.message)
    }

    private fun jws(header: String): String =
        "${header.encodeToByteArray().encodeToBase64Url()}.${"{}".encodeToByteArray().encodeToBase64Url()}.signature"
}
