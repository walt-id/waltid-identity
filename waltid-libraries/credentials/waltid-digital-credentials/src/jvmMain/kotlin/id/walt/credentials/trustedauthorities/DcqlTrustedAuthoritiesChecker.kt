package id.walt.credentials.trustedauthorities

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.dcql.DcqlCredential
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.TrustedAuthoritiesQuery
import id.walt.dcql.models.TrustedAuthorityType
import id.walt.x509.authorityKeyIdentifier
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * DCQL trusted authorities checker for the `aki` type (Authority Key Identifier).
 *
 * Per OID4VP §6.1.1: the base64url-encoded AKI byte value from a `trusted_authorities`
 * query entry of type `"aki"` must match the AKI extension of at least one X.509
 * certificate in the credential's certificate chain.
 *
 * Returns a [DcqlMatcher-compatible callback][checker] that can be passed to
 * [DcqlMatcher.match] as the `trustedAuthoritiesChecker` parameter.
 */
object DcqlTrustedAuthoritiesChecker {

    private val log = KotlinLogging.logger {}
    private val certFactory = CertificateFactory.getInstance("X.509")

    /**
     * Returns a checker function suitable for [id.walt.dcql.DcqlMatcher.match].
     *
     * Only [TrustedAuthorityType.AKI] is currently implemented.
     * [TrustedAuthorityType.ETSI_TL] and [TrustedAuthorityType.OPENID_FEDERATION] log a
     * warning and return `false` (not satisfied) since trust list / federation lookups
     * require external infrastructure.
     */
    val checker: (DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean = { credential, authoritiesQuery ->
        matchesTrustedAuthorities(credential, authoritiesQuery)
    }

    private fun matchesTrustedAuthorities(
        credential: DcqlCredential,
        authoritiesQuery: List<TrustedAuthoritiesQuery>,
    ): Boolean {
        // A credential matches if it satisfies at least one entry in the trusted_authorities array.
        return authoritiesQuery.any { query -> matchesOneAuthority(credential, query) }
    }

    private fun matchesOneAuthority(credential: DcqlCredential, query: TrustedAuthoritiesQuery): Boolean =
        when (query.type) {
            TrustedAuthorityType.AKI -> matchesAki(credential, query.values)
            TrustedAuthorityType.ETSI_TL -> {
                log.warn { "trusted_authorities type 'etsi_tl' is not yet implemented — credential ${credential.id} will not match" }
                false
            }
            TrustedAuthorityType.OPENID_FEDERATION -> {
                log.warn { "trusted_authorities type 'openid_federation' is not yet implemented — credential ${credential.id} will not match" }
                false
            }
        }

    /**
     * Check if any certificate in the credential's chain has an AKI matching one of [akiValues].
     *
     * [akiValues] are base64url-encoded raw AKI byte values as specified in OID4VP §6.1.1.
     */
    private fun matchesAki(credential: DcqlCredential, akiValues: List<String>): Boolean {
        val certChain = extractCertChain(credential)
        if (certChain.isEmpty()) {
            log.debug { "No certificate chain found for credential ${credential.id} — AKI check fails" }
            return false
        }

        return certChain.any { cert ->
            val aki = cert.authorityKeyIdentifier ?: return@any false
            val akiBase64Url = aki.toByteArray().encodeToBase64Url()
            akiValues.any { queryValue -> queryValue == akiBase64Url }.also { matched ->
                if (matched) log.debug { "AKI match for credential ${credential.id}: $akiBase64Url" }
            }
        }
    }

    /**
     * Extract the X.509 certificate chain from a credential.
     *
     * Sources (in priority order):
     * 1. [CoseCredentialSignature.x5cList] — mdoc / COSE-signed credentials
     * 2. JWS `x5c` header — JWT / SD-JWT credentials
     */
    private fun extractCertChain(credential: DcqlCredential): List<X509Certificate> {
        val originalCredential = (credential as? RawDcqlCredential)?.originalCredential as? DigitalCredential
            ?: return emptyList()

        return when (val sig = originalCredential.signature) {
            is CoseCredentialSignature -> sig.x5cList?.x5c?.mapNotNull { parseCert(it) } ?: emptyList()

            is JwtBasedSignature -> {
                val x5cArray = sig.jwtHeader?.get("x5c")?.jsonArray ?: return emptyList()
                x5cArray.mapNotNull { parseCert(X5CCertificateString(it.jsonPrimitive.content)) }
            }

            else -> emptyList()
        }
    }

    private fun parseCert(certString: X5CCertificateString): X509Certificate? = runCatching {
        val derBytes = Base64.getDecoder().decode(certString.base64Der)
        certFactory.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
    }.getOrElse { e ->
        log.debug { "Failed to parse certificate: ${e.message}" }
        null
    }
}
