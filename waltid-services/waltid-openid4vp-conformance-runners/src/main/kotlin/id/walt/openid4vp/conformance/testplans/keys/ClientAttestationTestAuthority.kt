package id.walt.openid4vp.conformance.testplans.keys

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.clientauth.attestation.ClientAttestationJwtTypes
import id.walt.openid4vci.tokens.jwt.JwtConfirmationClaims
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.x509.CertificateDer
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import id.walt.x509.X509KeyUsage
import id.walt.x509.X509ValidityPeriod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Test-only wallet attester, standing in for the service that would vouch for a wallet instance
 * under OAuth 2.0 Attestation-Based Client Authentication.
 *
 * The conformance suite verifies the attestation JWT strictly, and two of its rules decide the shape
 * of this class:
 *
 * - `ValidateClientAttestationSignature` verifies the JWT against the public key of the **leaf
 *   certificate in the `x5c` header**, not against any JWKS in the test configuration. An attester
 *   that signs with a bare key therefore fails however correct its claims are, which is why this
 *   mints a certificate rather than reusing one of the static keys in [TestKeyMaterial].
 * - `AbstractValidateX5cCertificateChain` requires the leaf not to be self-signed and the trust
 *   anchor not to appear in the chain, so the chain is a CA plus a separate leaf, `x5c` carries the
 *   leaf alone, and the CA is published to the suite as [trustAnchorPem].
 *
 * Both certificates are generated per run rather than committed: nothing here needs to be stable
 * across runs, generated material cannot expire in-place, and the private key of a committed CA
 * would have to be committed with it.
 */
class ClientAttestationTestAuthority private constructor(
    /** Published to the suite as `client_attestation.issuer`; the attestation's `iss`. */
    val issuer: String,
    /** The `sub` of every attestation; must equal the suite's configured `client.client_id`. */
    private val clientId: String,
    private val attesterKey: Key,
    private val leafCertificate: CertificateDer,
    /** Published to the suite as `client_attestation.trust_anchor`. */
    val trustAnchorPem: String,
) {

    /**
     * Issue an attestation binding [instancePublicJwk] to [clientId].
     *
     * `nbf` is deliberately omitted: the suite treats it as optional, and a `nbf` in the future is a
     * common source of spurious failures under clock skew.
     */
    suspend fun issueAttestationJwt(instancePublicJwk: JsonObject): String {
        val now = Clock.System.now()
        val header = buildJsonObject {
            put(JwtHeaderParams.TYPE, ClientAttestationJwtTypes.CLIENT_ATTESTATION)
            // Leaf only - the suite rejects a chain that includes the trust anchor.
            put(X509_CERTIFICATE_CHAIN, buildJsonArray {
                add(JsonPrimitive(Base64.encode(leafCertificate.bytes.toByteArray())))
            })
        }
        val payload = buildJsonObject {
            put(JwtPayloadClaims.ISSUER, issuer)
            put(JwtPayloadClaims.SUBJECT, clientId)
            put(JwtPayloadClaims.ISSUED_AT, now.epochSeconds)
            put(JwtPayloadClaims.EXPIRATION, (now + ATTESTATION_LIFETIME).epochSeconds)
            put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                put(JwtConfirmationClaims.JWK, instancePublicJwk)
            })
        }
        return CompactJws.sign(
            payload = payload.toString().encodeToByteArray(),
            key = attesterKey,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = header,
        )
    }

    companion object {
        private const val X509_CERTIFICATE_CHAIN = "x5c"

        /**
         * Identifier of this test attester, published to the suite as `client_attestation.issuer`.
         *
         * Never dereferenced by either side; the suite only checks that the attestation's `iss` equals
         * what the configuration declares.
         */
        const val DEFAULT_ISSUER = "https://wallet.test.attester.example"

        private val ATTESTATION_LIFETIME = 5.minutes

        /**
         * `notBefore` is backdated so that a certificate minted moments before the suite validates it
         * is not rejected for being not-yet-valid.
         */
        private val CLOCK_SKEW_ALLOWANCE = 5.minutes

        private val signatureAlgorithm = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )

        suspend fun create(
            clientId: String,
            issuer: String = DEFAULT_ISSUER,
        ): ClientAttestationTestAuthority {
            val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            val caKey = runtime.generateAttestationKey("client-attestation-test-ca")
            val attesterKey = runtime.generateAttestationKey("client-attestation-test-attester")

            val caName = X509DistinguishedName(
                commonName = "walt.id Conformance Wallet Attester CA",
                organizationName = "walt.id",
            )
            val validity = X509ValidityPeriod(
                notBefore = Clock.System.now() - CLOCK_SKEW_ALLOWANCE,
                notAfter = Clock.System.now() + 1.days,
            )
            val builder = GenericX509CertificateBuilder()

            val caCertificate = builder.buildDer(
                profileData = GenericX509CertificateProfileData(
                    subjectName = caName,
                    validityPeriod = validity,
                    isCertificateAuthority = true,
                    pathLengthConstraint = 0,
                    // PKIX path validation requires keyCertSign on anything that certifies another cert.
                    keyUsage = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
                ),
                subjectPublicKey = caKey,
                signingKey = caKey,
                signatureAlgorithm = signatureAlgorithm,
            )
            val leafCertificate = builder.buildDer(
                profileData = GenericX509CertificateProfileData(
                    subjectName = X509DistinguishedName(
                        commonName = "walt.id Conformance Wallet Attester",
                        organizationName = "walt.id",
                    ),
                    issuerName = caName,
                    validityPeriod = validity,
                    keyUsage = setOf(X509KeyUsage.DigitalSignature),
                ),
                subjectPublicKey = attesterKey,
                signingKey = caKey,
                signatureAlgorithm = signatureAlgorithm,
            )

            return ClientAttestationTestAuthority(
                issuer = issuer,
                clientId = clientId,
                attesterKey = attesterKey,
                leafCertificate = leafCertificate,
                trustAnchorPem = caCertificate.toPEMEncodedString(),
            )
        }

        private suspend fun CryptoRuntime.generateAttestationKey(id: String) = generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId(id),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
    }
}
