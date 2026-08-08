package id.walt.rpcert.wallet

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

class RegistrationCertificateVerificationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Wallet-side parsing and verification of Wallet-Relying Party Registration Certificates
 * (`rc-wrp+jwt`, ETSI TS 119 475).
 */
object RelyingPartyRegistrationCertificateVerifier {

    /** Tolerated clock skew for `iat` checks of 5 Minutes. */
    private const val ALLOWED_CLOCK_SKEW_SECONDS = 300L

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** A decoded (but not necessarily verified) registration certificate JWT. */
    data class DecodedRegistrationCertificate(
        val header: JsonObject,
        val certificate: RelyingPartyRegistrationCertificate,
        /** DER certificates from the `x5c` header, leaf first. */
        val certificateChain: List<CertificateDer>,
    ) {
        val leafCertificate: CertificateDer
            get() = certificateChain.first()
    }

    /**
     * Decode a registration certificate JWT without verifying it: checks the JWT structure,
     * `typ` header and `x5c` presence, and parses the payload. Use [verify] for full verification.
     */
    fun decode(certificateJwt: String): DecodedRegistrationCertificate {
        val jws = runCatching { certificateJwt.decodeJws() }.getOrElse {
            throw RegistrationCertificateVerificationException("Registration certificate is not a valid JWT", it)
        }

        val typ = jws.header["typ"]?.jsonPrimitive?.contentOrNull
        if (typ != RelyingPartyRegistrationCertificateIssuer.JWT_TYPE) {
            throw RegistrationCertificateVerificationException(
                "Registration certificate JWT typ must be '${RelyingPartyRegistrationCertificateIssuer.JWT_TYPE}', but was '$typ'"
            )
        }

        val x5c = jws.header["x5c"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.map { CertificateDer(it.decodeFromBase64()) }
        if (x5c.isNullOrEmpty()) {
            throw RegistrationCertificateVerificationException("Registration certificate JWT is missing the x5c header")
        }

        val certificate = runCatching {
            json.decodeFromJsonElement(RelyingPartyRegistrationCertificate.serializer(), jws.payload)
        }.getOrElse {
            throw RegistrationCertificateVerificationException(
                "Registration certificate payload is not a valid Wallet-Relying Party Registration Certificate: ${it.message}", it
            )
        }

        return DecodedRegistrationCertificate(
            header = jws.header,
            certificate = certificate,
            certificateChain = x5c,
        )
    }

    /**
     * Decode and fully verify a registration certificate JWT: JWT signature, `x5c` chain
     * validity, and `iat`/`exp` time validity.
     *
     * @param certificateJwt Registration certificate in JWT compact serialization.
     * @param trustAnchors DER-encoded trust roots the `x5c` chain must chain up to. If null/empty,
     * an included self-signed root is accepted only when [allowTrustedChainRoot] is set.
     * @param allowTrustedChainRoot Accept a self-signed root certificate within the `x5c` chain as
     * trust anchor (useful for demos/tests, insecure for production).
     * @param now Verification time.
     */
    suspend fun verify(
        certificateJwt: String,
        trustAnchors: List<CertificateDer>? = null,
        allowTrustedChainRoot: Boolean = false,
        now: Instant = Clock.System.now(),
    ): Result<DecodedRegistrationCertificate> = runCatching {
        val decoded = decode(certificateJwt)

        val leafKey = JWKKey.importFromDerCertificate(decoded.leafCertificate.bytes.toByteArray())
            .getOrElse {
                throw RegistrationCertificateVerificationException("Could not import public key from leaf x5c certificate", it)
            }
        leafKey.verifyJws(certificateJwt).getOrElse {
            throw RegistrationCertificateVerificationException("Registration certificate JWT signature is invalid", it)
        }

        runCatching {
            validateCertificateChain(
                leaf = decoded.leafCertificate,
                chain = decoded.certificateChain,
                trustAnchors = trustAnchors,
                enableTrustedChainRoot = allowTrustedChainRoot,
            )
        }.getOrElse {
            throw RegistrationCertificateVerificationException("x5c certificate chain validation failed: ${it.message}", it)
        }

        val nowEpochSeconds = now.epochSeconds
        if (decoded.certificate.iat > nowEpochSeconds + ALLOWED_CLOCK_SKEW_SECONDS) {
            throw RegistrationCertificateVerificationException("Registration certificate iat (${decoded.certificate.iat}) is in the future")
        }
        decoded.certificate.exp?.let { exp ->
            if (exp <= nowEpochSeconds) {
                throw RegistrationCertificateVerificationException("Registration certificate expired at $exp")
            }
        }

        // TODO: Need to add status list check once status list implementation is complete.

        decoded
    }
}
