package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.keyresolver.JwtKeyResolutionSource
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto2.keys.Key
import id.walt.w3c.schemes.JwsSignatureScheme
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The key that signed a credential and the `x5c` certificate chain it was taken from (base64 DER,
 * leaf first). [x5c] is empty when the key came from a DID or issuer metadata, even if the credential
 * also carries a chain.
 *
 * [CredentialSignaturePolicy] and [X509TrustPolicy] both resolve the key through [resolveIssuerKey],
 * so trust is always decided for the key that verified the signature.
 */
internal class CredentialIssuerKey(val key: Key, val x5c: List<String>)

internal suspend fun DigitalCredential.resolveIssuerKey(): CredentialIssuerKey {
    val signature = signature
    return when {
        this is MdocsCredential -> {
            val issuerAuth = document.issuerSigned.getParsedIssuerAuthCrypto2()
            CredentialIssuerKey(issuerAuth.signerKey, issuerAuth.x5c)
        }

        signature is JwtBasedSignature -> JwsSignatureScheme()
            .getIssuerCrypto2KeyInfo(requireNotNull(signed) { "Cannot resolve signer key for unsigned credential" })
            .resolved.toIssuerKey()

        // W3C VC secured with COSE_Sign1: same resolution as AbstractW3C.getSignerCrypto2Key()
        signature is CoseCredentialSignature -> {
            val x5c = signature.x5cList?.x5c
                ?: throw IllegalArgumentException("Failed to resolve the credential signer key: no x5c in COSE header")
            Crypto2JwtKeyResolver().resolveFromJwt(
                jwtHeader = buildJsonObject { put("x5c", JsonArray(x5c.map { JsonPrimitive(it.base64Der) })) },
                jwtPayload = credentialData,
            )?.toIssuerKey() ?: throw IllegalArgumentException("Failed to resolve the credential signer key")
        }

        else -> throw UnsupportedOperationException(
            "Signer-key resolution is not supported for ${this::class.simpleName} with " +
                    (signature?.let { it::class.simpleName } ?: "no signature")
        )
    }
}

private fun ResolvedJwtVerificationKey.toIssuerKey() =
    CredentialIssuerKey(key, if (source == JwtKeyResolutionSource.X5C) certificateChain else emptyList())
