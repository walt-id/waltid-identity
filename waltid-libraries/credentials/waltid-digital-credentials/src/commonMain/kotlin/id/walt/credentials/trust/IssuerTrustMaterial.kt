package id.walt.credentials.trust

import id.walt.cose.protectedAlgorithm
import id.walt.cose.verify
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.document.models.verification.relationship.VerificationRelationshipType
import id.walt.w3c.schemes.JwsSignatureScheme
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

private val log = KotlinLogging.logger { }

/**
 * The material a trust source judges an issuer by, taken from the key that signed a [DigitalCredential]:
 * either the X.509 certificate chain whose leaf holds that key, or, for an issuer that identifies its key
 * through a DID or HTTPS issuer metadata instead of a certificate, the public key itself.
 *
 * Obtain it through [verifiedIssuerTrustMaterial], which verifies the credential signature with that key
 * first, so the material always belongs to the signer.
 */
sealed class IssuerTrustMaterial {
    /** PEM certificates, leaf first. The leaf certificate holds the key that verified the signature. */
    data class CertificateChain(val pemChain: List<String>) : IssuerTrustMaterial() {
        init {
            require(pemChain.isNotEmpty()) { "Certificate chain must not be empty" }
        }
    }

    /** The public JWK of the key that verified the signature, for an issuer key no certificate chain stands for. */
    data class PublicKey(val jwk: String) : IssuerTrustMaterial()
}

/** The issuer trust material could not be established: unsupported credential, unresolvable key or invalid signature. */
class IssuerTrustMaterialException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves the key that signed this credential, verifies the credential signature with it and returns the
 * material to decide trust on.
 *
 * - **mdoc:** the COSE `x5chain` of IssuerAuth (unprotected header, then protected, ISO/IEC 18013-5 9.1.2.4).
 *   The IssuerAuth `COSE_Sign1` signature is verified with the leaf certificate's key. This checks
 *   possession only: the document-signer certificate profile, the value digests and the MSO validity are
 *   left to the caller.
 * - **JWT-based (SD-JWT VC, W3C VC as JWT):** see [jwtTrustMaterial].
 *
 * Signer binding: a trust decision on this material is only sound if it covers the key the credential was
 * signed with. Otherwise an attacker could sign with a self-asserted key while trust is established for a
 * different, trust-listed key or certificate. That is why the signature is verified here, with the same key
 * whose chain or JWK is returned, and not left to a separate step.
 *
 * @throws IssuerTrustMaterialException if the credential format is not supported, the key cannot be
 *   resolved or the signature does not verify.
 */
suspend fun DigitalCredential.verifiedIssuerTrustMaterial(): IssuerTrustMaterial {
    val signature = signature
    return when {
        this is MdocsCredential && signature is CoseCredentialSignature -> mdocTrustMaterial(this)
        signature is JwtBasedSignature -> jwtTrustMaterial(signature, this)
        else -> throw IssuerTrustMaterialException(
            "Unsupported credential type for issuer trust resolution. " +
                "Supported: mDoc with COSE x5chain, SD-JWT VC / JWT VC with an x5c header or a resolvable " +
                "DID / HTTPS issuer key. " +
                "Got: ${this::class.simpleName} with ${signature?.let { it::class.simpleName } ?: "no signature"}"
        )
    }
}

private suspend fun mdocTrustMaterial(credential: MdocsCredential): IssuerTrustMaterial {
    val issuerSigned = try {
        credential.document.issuerSigned
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IssuerTrustMaterialException("mDoc credential could not be parsed: ${e.message}", e)
    }
    val parsed = try {
        issuerSigned.getParsedIssuerAuthCrypto2()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IssuerTrustMaterialException("mDoc credential has no usable x5c certificate chain in COSE header: ${e.message}", e)
    }
    if (parsed.x5c.isEmpty()) throw IssuerTrustMaterialException("mDoc x5c chain is empty")

    val verified = try {
        val issuerAuth = issuerSigned.issuerAuth
        issuerAuth.verify(parsed.signerKey, issuerAuth.protectedAlgorithm())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IssuerTrustMaterialException("mDoc IssuerAuth signature could not be verified: ${e.message}", e)
    }
    if (!verified) {
        throw IssuerTrustMaterialException("mDoc IssuerAuth signature does not verify with its x5chain leaf certificate key")
    }
    return IssuerTrustMaterial.CertificateChain(parsed.x5c.map { buildPemCertificate(it.decodeFromBase64()) })
}

/**
 * Extracts trust material for a JWT-based credential (SD-JWT VC, JWT VC): the key the
 * credential was signed with, resolved via [Crypto2JwtKeyResolver] (DID resolution for a DID
 * `iss`, then the `x5c` leaf certificate, then HTTPS well-known JWT VC issuer metadata).
 *
 * That key is represented by its `x5c` certificate chain when it was taken from `x5c`, or when
 * the `x5c` leaf certificate holds it (e.g. a DID `iss` signing with its certified key).
 * Otherwise — e.g. an issuer that signs with a `did:web` DID and identifies its signing key only
 * via `kid` + `iss` — the public key itself is returned, to be matched by JWK thumbprint.
 * An inline `jwk` header is intentionally not trusted here (`allowInlineJwk = false`): it is
 * self-asserted and establishes no issuer identity on its own.
 *
 * Signer binding: the key is resolved as the base `signature` policy resolves it:
 * `JwsSignatureScheme.getIssuerCrypto2KeyInfo` on the signed JWT, with `Crypto2JwtKeyResolver` with
 * `allowInlineJwk = false` and DID `assertionMethod` authorization, not on the parsed credential data,
 * whose SD-JWT disclosures can add claims (such as `iss`) that the signed payload lacks. Resolving
 * twice does not guarantee the same key, as a `did:web` document or HTTPS issuer metadata is
 * fetched again, so the signed JWT is verified with the resolved key before it is returned.
 * An `x5c` chain stands in for the key only when its leaf certificate holds it.
 */
private suspend fun jwtTrustMaterial(
    signature: JwtBasedSignature,
    credential: DigitalCredential,
): IssuerTrustMaterial {
    val signed = credential.signed
        ?: throw IssuerTrustMaterialException("JWT credential is not signed")

    val resolved = try {
        JwsSignatureScheme().getIssuerCrypto2KeyInfo(signed, keyResolver).resolved
    } catch (e: CancellationException) {
        throw e
    } catch (cause: Exception) {
        throw IssuerTrustMaterialException(
            "The JWT issuer's public key could not be resolved: ${cause.message}. Issuer trust resolution " +
                "requires an x5c header or a resolvable issuer key (DID or HTTPS well-known JWT VC issuer metadata).",
            cause
        )
    }
    try {
        CompactJws.verify(signed.substringBefore('~'), resolved.key, JwsAlgorithm.entries.toSet())
    } catch (e: CancellationException) {
        throw e
    } catch (cause: Exception) {
        throw IssuerTrustMaterialException(
            "The resolved JWT issuer key does not verify the credential signature: ${cause.message}",
            cause
        )
    }

    val x5c = signature.jwtHeader?.get("x5c") as? JsonArray
    val certificateChain = when {
        resolved.source == JwtKeyResolutionSource.X5C -> resolved.certificateChain
        x5c != null && x5cLeafHoldsKey(x5c, resolved.key) -> x5c.map { it.jsonPrimitive.content }
        else -> null
    }
    if (certificateChain != null) {
        log.debug { "Using the ${certificateChain.size}-certificate x5c chain of the ${resolved.source} issuer key" }
        return IssuerTrustMaterial.CertificateChain(
            certificateChain.map { buildPemCertificate(it.decodeFromBase64()) }
        )
    }

    log.debug { "Resolved issuer public key via ${resolved.source} for trust matching" }
    return IssuerTrustMaterial.PublicKey(exportIssuerJwk(resolved.key).data.toByteArray().decodeToString())
}

/** Whether the leaf certificate of [x5c] holds [key], compared by RFC 7638 JWK thumbprint. */
private suspend fun x5cLeafHoldsKey(x5c: JsonArray, key: Key): Boolean = runCatching {
    val leafKey = X5CKeyResolver.restoreKeyFromX5c(x5c, runtime)
    Jwk.sha256Thumbprint(exportIssuerJwk(leafKey)) == Jwk.sha256Thumbprint(exportIssuerJwk(key))
}.getOrDefault(false)

private suspend fun exportIssuerJwk(key: Key): EncodedKey.Jwk {
    val exporter = key.capabilities.publicKeyExporter
        ?: throw IssuerTrustMaterialException("Resolved issuer key does not support public key export")
    return exporter.exportPublicKey().toPublicJwk(key.spec)
}

private fun buildPemCertificate(derBytes: ByteArray): String {
    val base64 = derBytes.encodeToBase64()
    return buildString {
        appendLine("-----BEGIN CERTIFICATE-----")
        base64.chunked(64).forEach { appendLine(it) }
        appendLine("-----END CERTIFICATE-----")
    }
}

/**
 * Resolves the key a JWT credential was signed with, as the base signature policy does
 * (DID, x5c, HTTPS well-known JWT VC issuer metadata). Inline `jwk` headers are not
 * trusted, as they are self-asserted. DID keys must be authorized for assertionMethod,
 * including methods embedded directly in that relationship.
 */
private val keyResolver: Crypto2JwtKeyResolver by lazy {
    Crypto2JwtKeyResolver(
        allowInlineJwk = false,
        didVerificationRelationship = VerificationRelationshipType.AssertionMethod,
    )
}

/** Restores the x5c leaf certificate's key, to compare it with the resolved issuer key. */
private val runtime: CryptoRuntime by lazy { CryptoRuntime(defaultSoftwareKeyProviders()) }
