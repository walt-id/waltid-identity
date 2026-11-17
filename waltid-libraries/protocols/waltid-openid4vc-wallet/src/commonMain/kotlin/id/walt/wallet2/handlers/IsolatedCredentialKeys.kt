package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.openid4vci.proofs.Proofs
import id.walt.wallet2.data.PublicKeyThumbprint
import id.walt.wallet2.data.holderKeyThumbprints
import id.walt.wallet2.data.publicKeyThumbprint
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/**
 * Wallet response policy for isolated JWT-proof requests. Expected public keys are evidence from
 * the submitted proofs, independently of any keys selected for storage. Parsing a nested KA does
 * not authenticate it: the issuer owns trust, signatures, claims, and nonce validation. Storage
 * preflight checks readable key evidence before issuance; response validation follows issuer success.
 *
 * This wallet profile accepts fewer/reordered credentials, at most one per distinct expected key.
 * These response limits are wallet policy, not a universal credential-count rule for OpenID4VCI.
 * Proofless responses and deferred claim-time validation keep their existing behavior.
 */
internal suspend fun validateIsolatedCredentialKeys(rawCredentials: List<String>, proofs: Proofs) {
    val credentials = rawCredentials.map { CredentialParser.detectAndParse(it).second }
    val allowedDids = proofs.holderDids()
    val expected = proofs.jwt.orEmpty().flatMap { expectedProofKeys(it, credentials, allowedDids) }.toSet()
    require(credentials.size <= expected.size) { "Issuer returned more credentials than distinct expected holder keys" }
    val remaining = expected.toMutableSet()
    credentials.forEach { credential ->
        require(credential.permitsHolderDid(allowedDids)) { "Issued credential is bound to an unexpected holder DID" }
        val identities = credential.holderKeyThumbprints(
            includeSubjectDid = credential.subject?.substringBefore('#') in allowedDids,
        )
        require(identities.isNotEmpty()) { "Issued credential contains no usable holder binding" }
        val match = remaining.firstOrNull { it in identities }
        require(match != null) { "Issued credential is bound to an unexpected or repeated holder key" }
        remaining.remove(match)
    }
}

/** Prevent an incomplete storage selection from consuming an otherwise valid issuance. */
internal suspend fun validateIsolatedStorageBindings(proofs: Proofs, bindings: List<ResolvedCredentialHolderBinding>) {
    val selected = bindings.map { it.material.publicKeyThumbprint() }.toSet()
    for (jwt in proofs.jwt.orEmpty()) {
        try {
            CompactJws.decodeUnverified(jwt)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // An unreadable outer proof belongs to the issuer's structured rejection. Readable
            // key evidence must resolve before issuance; this does not authenticate attestations.
            continue
        }
        val expected = expectedProofKeys(jwt, emptyList(), emptySet(), bindings)
        require(expected.isNotEmpty() && selected.containsAll(expected)) {
            "Selected wallet bindings do not cover every expected holder key"
        }
    }
}

private fun Proofs.holderDids(): Set<String> = jwt.orEmpty().mapNotNull {
    CompactJws.decodeUnverified(it).protectedHeader["kid"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { kid -> kid.startsWith("did:") }?.substringBefore('#')
}.toSet()

// cnf.kid can cause a network lookup even when subject-DID fallback is disabled.
private fun DigitalCredential.permitsHolderDid(allowedDids: Set<String>): Boolean {
    val kid = (credentialData["cnf"] as? JsonObject)?.get("kid")?.jsonPrimitive?.contentOrNull
    return kid == null || kid.substringBefore('#') in allowedDids
}

private suspend fun expectedProofKeys(
    jwt: String,
    credentials: List<DigitalCredential>,
    allowedDids: Set<String>,
    selectedBindings: List<ResolvedCredentialHolderBinding>? = null,
): Set<PublicKeyThumbprint> {
    val header = CompactJws.decodeUnverified(jwt).protectedHeader
    header["key_attestation"]?.let { value ->
        val attestation = requireNotNull((value as? JsonPrimitive)?.takeIf { it.isString }?.content) {
            "JWT proof key_attestation must be a compact JWT"
        }
        val payload = Json.parseToJsonElement(CompactJws.decodeUnverified(attestation).payload.decodeToString()).jsonObject
        val keys = requireNotNull(payload["attested_keys"] as? JsonArray) { "Key attestation must contain attested_keys" }
        require(keys.isNotEmpty()) { "Key attestation attested_keys must not be empty" }
        return keys.map { it.jsonObject.expectedThumbprint() }.toSet()
    }
    (header["jwk"] as? JsonObject)?.let { return setOf(it.expectedThumbprint()) }
    (header["x5c"] as? JsonArray)?.let { chain ->
        val certificate = requireNotNull(chain.firstOrNull()?.jsonPrimitive?.contentOrNull) { "Proof x5c must contain a certificate" }
        // Extract the leaf's public key; this is not a certificate-chain trust decision.
        val key = JWKKey.importDERorPEM(certificate).getOrThrow()
        return setOf(key.getPublicKey().exportJWKObject().expectedThumbprint())
    }
    val kid = requireNotNull((header["kid"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
        "JWT proof contains no usable holder-key identification"
    }
    if (kid.startsWith("did:")) return didProofKeys(jwt, kid)

    if (selectedBindings != null) return selectedBindings.mapNotNull { binding ->
        val material = binding.material
        val publicKey = material.crypto2Key?.let {
            JWKKey.importJWK(it.exportPublicJwkObject().toString()).getOrThrow()
        } ?: requireNotNull(material.legacyKey).getPublicKey()
        if (publicKey.verifyJws(jwt).isSuccess) material.publicKeyThumbprint() else null
    }.toSet()

    // Opaque/registered kids do not carry public material. Identify a returned key through the
    // submitted proof's signature, without needing a stored wallet key or guessing from the kid.
    // An omitted credential is allowed: not every proof needs to match a returned credential.
    return credentials.flatMap { credential ->
        try {
            if (!credential.permitsHolderDid(allowedDids)) return@flatMap emptySet()
            val key = credential.getHolderKey()
            when {
                key != null && key.verifyJws(jwt).isSuccess -> setOf(key.getPublicKey().exportJWKObject().expectedThumbprint())
                key == null && credential.subject?.substringBefore('#') in allowedDids -> didProofKeys(jwt, credential.subject!!)
                else -> emptySet()
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            emptySet()
        }
    }.toSet()
}

private suspend fun didProofKeys(jwt: String, kid: String): Set<PublicKeyThumbprint> {
    val native = DidService.resolveToCrypto2Keys(kid.substringBefore('#'))
    if (native.isSuccess) return native.getOrThrow().mapNotNull { key ->
        try {
            CompactJws.verify(jwt, key, CompactJws.decodeUnverified(jwt).algorithm)
            val publicJwk = key.exportPublicJwkObject()
            publicJwk.expectedThumbprint()
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
    }.toSet()
    // Custom legacy resolvers remain a fallback when no crypto2 resolver is available.
    @Suppress("DEPRECATION")
    val legacyKeys = DidService.resolveToKeys(kid.substringBefore('#'))
    return legacyKeys.getOrThrow().filter { it.verifyJws(jwt).isSuccess }
        .map { it.getPublicKey().exportJWKObject().expectedThumbprint() }.toSet()
}

private suspend fun JsonObject.expectedThumbprint(): PublicKeyThumbprint = PublicKeyThumbprint(
    value = Jwk.sha256Thumbprint(EncodedKey.Jwk(BinaryData(toString().encodeToByteArray()), privateMaterial = false)),
)
