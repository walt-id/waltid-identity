package id.walt.crypto

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsHeader
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal fun KeyType.toPlatformKeyStoreCurve(): ECCurve? = when (this) {
    KeyType.secp256r1 -> ECCurve.SECP_256_R_1
    KeyType.secp384r1 -> ECCurve.SECP_384_R_1
    KeyType.secp521r1 -> ECCurve.SECP_521_R_1
    KeyType.RSA -> null
    else -> error("Unsupported platform key type: $this")
}

internal fun KeyType.toPlatformJwsAlgorithm(): JwsAlgorithm.Signature = when (this) {
    KeyType.secp256r1 -> JwsAlgorithm.Signature.EC.ES256
    KeyType.secp384r1 -> JwsAlgorithm.Signature.EC.ES384
    KeyType.secp521r1 -> JwsAlgorithm.Signature.EC.ES512
    KeyType.RSA -> JwsAlgorithm.Signature.RSA.PS256
    else -> error("Unsupported key type for JWS: $this")
}

internal fun KeyType.toPlatformSignatureAlgorithm(): SignatureAlgorithm = when (this) {
    KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
    KeyType.secp384r1 -> SignatureAlgorithm.ECDSAwithSHA384
    KeyType.secp521r1 -> SignatureAlgorithm.ECDSAwithSHA512
    KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPSSPadding
    else -> error("Unsupported key type for verification: $this")
}

// Mobile EC verification expects raw R||S signatures; callers may pass DER-encoded signatures.
internal fun KeyType.toCryptoSignature(signatureBytes: ByteArray): CryptoSignature = when (this) {
    KeyType.RSA -> CryptoSignature.RSA(signatureBytes)
    in KeyTypes.EC_KEYS -> CryptoSignature.EC.fromRawBytes(EccUtils.convertDERtoIEEEP1363(signatureBytes))
    else -> error("Unsupported key type: $this")
}

internal suspend fun signJwsWithPlatformSigner(
    keyType: KeyType,
    plaintext: ByteArray,
    headers: Map<String, JsonElement>,
    protectedKeyUse: Boolean = false,
    sign: suspend (ByteArray) -> SignatureResult<*>,
): String {
    val jwkHeader = headers["jwk"]?.let { jwkElement ->
        runCatching { joseCompliantSerializer.decodeFromString<JsonWebKey>(jwkElement.toString()) }.getOrNull()
    }

    val header = JwsHeader(
        algorithm = keyType.toPlatformJwsAlgorithm(),
        keyId = headers["kid"]?.let { (it as? JsonPrimitive)?.content },
        type = headers["typ"]?.let { (it as? JsonPrimitive)?.content },
        contentType = headers["cty"]?.let { (it as? JsonPrimitive)?.content },
        jsonWebKey = jwkHeader,
    )

    return JwsCompact(
        protectedHeader = header,
        payload = plaintext,
        signer = { data ->
            sign(data).signatureBytesOrThrow(
                protectedKeyUse = protectedKeyUse,
                legacyFailurePrefix = "JWS signing failed",
            )
        }
    ).toString()
}

internal fun SignatureResult<*>.signatureBytesOrThrow(
    protectedKeyUse: Boolean,
    legacyFailurePrefix: String = "Signing failed",
): ByteArray {
    if (!protectedKeyUse) {
        check(this is SignatureResult.Success<*>) { "$legacyFailurePrefix: $this" }
        return signature.rawByteArray
    }
    return when (this) {
        is SignatureResult.Success<*> -> signature.rawByteArray
        is SignatureResult.Failure -> throw KeyUseAuthorizationException(
            failure = KeyUseAuthorizationFailure.AuthorizationFailed,
            message = "Key-use authorization failed",
            cause = problem,
        )
        is SignatureResult.Error -> throw exception
    }
}

internal fun verifyRawWithPlatformSigner(
    keyType: KeyType,
    publicKey: CryptoPublicKey,
    signatureBytes: ByteArray,
    detachedPlaintext: ByteArray?,
): Result<ByteArray> = runCatching {
    val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
    val verifier = keyType.toPlatformSignatureAlgorithm().verifierFor(publicKey).getOrThrow()
    verifier.verify(SignatureInput(plaintext), keyType.toCryptoSignature(signatureBytes)).getOrThrow()
    plaintext
}

internal fun verifyJwsWithPlatformSigner(
    keyType: KeyType,
    publicKey: CryptoPublicKey,
    signedJws: String,
): Result<JsonElement> = runCatching {
    val parsed = JwsCompact(signedJws)
    val verifier = keyType.toPlatformSignatureAlgorithm().verifierFor(publicKey).getOrThrow()
    verifier.verify(SignatureInput(parsed.signatureInput), keyType.toCryptoSignature(parsed.plainSignature)).getOrThrow()
    Json.parseToJsonElement(parsed.plainPayload.decodeToString())
}
