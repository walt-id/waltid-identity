package id.walt.wallet2.handlers

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlin.time.Clock

/** A wallet-provider service, separate from the credential holder's proof-signing key. */
interface KeyAttestationProvider {
    /** Public verification capability for the JWT returned by [attest]. Issuer trust is independent. */
    val verificationKey: Key

    /** The provider must only assert properties it can substantiate for [request.proofKey]. */
    suspend fun attest(request: KeyAttestationRequest): String
}

data class KeyAttestationRequest(
    val credentialIssuer: String,
    val proofKey: EncodedKey.Jwk,
    val nonce: String?,
    val requirements: KeyAttestationsRequired,
)

internal suspend fun keyAttestationForProof(
    provider: KeyAttestationProvider?,
    requirements: KeyAttestationsRequired?,
    proofKey: Key,
    credentialIssuer: String,
    nonce: String?,
    acceptedAlgorithms: Set<String>?,
): String? = requirements?.let { required ->
    val attester = requireNotNull(provider) {
        "Issuer requires key attestation but no wallet-provider service is configured"
    }
    val publicKey = requireNotNull(proofKey.capabilities.publicKeyExporter) {
        "Credential proof key cannot export public material for attestation"
    }.exportPublicKey().toPublicJwk(proofKey.spec)
    attester.validatedAttestation(KeyAttestationRequest(credentialIssuer, publicKey, nonce, required))
        .also { jwt ->
            require(acceptedAlgorithms == null || CompactJws.decodeUnverified(jwt).algorithm.identifier in acceptedAlgorithms) {
                "Issuer does not support the key-attestation algorithm"
            }
        }
}

/** Validate the provider's signed answer before embedding it in a credential proof. */
internal suspend fun KeyAttestationProvider.validatedAttestation(request: KeyAttestationRequest): String {
    val jwt = attest(request)
    val decoded = CompactJws.decodeUnverified(jwt)
    require(decoded.protectedHeader["typ"]?.jsonPrimitive?.content == "key-attestation+jwt") {
        "Key attestation has an invalid JWT type"
    }
    val verified = CompactJws.verify(jwt, verificationKey, decoded.algorithm)
    (decoded.protectedHeader["jwk"] as? JsonObject)?.let { declaredKey ->
        val verificationPublicKey = requireNotNull(verificationKey.capabilities.publicKeyExporter) {
            "Attester verification key cannot be compared with the JWT header"
        }.exportPublicKey().toPublicJwk(verificationKey.spec)
        val declared = EncodedKey.Jwk(
            BinaryData(Json.encodeToString(JsonObject.serializer(), declaredKey).encodeToByteArray()),
            privateMaterial = false,
        )
        require(Jwk.sha256Thumbprint(declared) == Jwk.sha256Thumbprint(verificationPublicKey)) {
            "Key attestation header does not identify the configured attester"
        }
    }
    val payload = Json.parseToJsonElement(verified.payload.decodeToString()) as? JsonObject
        ?: throw IllegalArgumentException("Key attestation payload must be a JSON object")
    val now = Clock.System.now().toEpochMilliseconds() / 1000
    val issuedAt = payload["iat"]?.jsonPrimitive?.content?.toLongOrNull()
        ?: throw IllegalArgumentException("Key attestation is missing iat")
    val expiresAt = payload["exp"]?.jsonPrimitive?.content?.toLongOrNull()
        ?: throw IllegalArgumentException("Key attestation is missing exp for JWT proof")
    require(payload["iat"]?.jsonPrimitive?.isString == false && payload["exp"]?.jsonPrimitive?.isString == false) {
        "Key attestation iat and exp must be numbers"
    }
    require(issuedAt <= now + 60 && expiresAt > now && expiresAt > issuedAt) {
        "Key attestation is not currently valid"
    }
    if (request.nonce != null) {
        require(payload["nonce"]?.jsonPrimitive?.content == request.nonce) {
            "Key attestation nonce does not match the issuer nonce"
        }
    }
    val attestedKeys = payload["attested_keys"] as? JsonArray
        ?: throw IllegalArgumentException("Key attestation is missing attested_keys")
    require(attestedKeys.isNotEmpty()) { "Key attestation has no attested keys" }
    require(attestedKeys.all { it is JsonObject && !Jwk.containsPrivateMaterial(it) }) {
        "Key attestation must contain only public JWKs"
    }
    val proofThumbprint = Jwk.sha256Thumbprint(request.proofKey)
    require(attestedKeys.any { element ->
        val jwk = element as? JsonObject ?: return@any false
        runCatching {
                Jwk.sha256Thumbprint(
                    EncodedKey.Jwk(BinaryData(Json.encodeToString(JsonObject.serializer(), jwk).encodeToByteArray()), false)
                ) == proofThumbprint
            }.getOrDefault(false)
    }) { "Key attestation does not contain the credential proof key" }
    requireMatchesRequirement(payload, "key_storage", request.requirements.keyStorage)
    requireMatchesRequirement(payload, "user_authentication", request.requirements.userAuthentication)
    return jwt
}

private fun requireMatchesRequirement(payload: JsonObject, claim: String, accepted: Set<String>?) {
    if (accepted == null) return
    val values = payload[claim] as? JsonArray
    require(values != null && values.any { it.jsonPrimitive.content in accepted }) {
        "Key attestation does not meet the issuer's $claim requirement"
    }
}
