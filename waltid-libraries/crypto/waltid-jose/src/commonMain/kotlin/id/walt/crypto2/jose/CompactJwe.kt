@file:OptIn(CryptographyProviderApi::class)

package id.walt.crypto2.jose

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.AeadAlgorithm
import id.walt.crypto2.keys.AeadCiphertext
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

enum class JweContentEncryption(val identifier: String, val keySizeBytes: Int) {
    A128GCM("A128GCM", 16),
    A256GCM("A256GCM", 32),
    ;

    companion object {
        fun parse(identifier: String): JweContentEncryption = entries.firstOrNull { it.identifier == identifier }
            ?: throw IllegalArgumentException("Unsupported JWE content encryption: $identifier")
    }
}

data class DecryptedJwe(
    val protectedHeader: JsonObject,
    val plaintext: ByteArray,
    val contentEncryption: JweContentEncryption,
)

object CompactJwe {
    private const val KEY_MANAGEMENT_ALGORITHM = "ECDH-ES"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BYTES = 16
    private val json = Json { explicitNulls = true }
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    suspend fun encrypt(
        plaintext: ByteArray,
        recipientPublicKey: EncodedKey.Jwk,
        contentEncryption: JweContentEncryption,
        protectedHeader: JsonObject = JsonObject(emptyMap()),
        agreementPartyUInfo: ByteArray = byteArrayOf(),
        agreementPartyVInfo: ByteArray = byteArrayOf(),
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): String {
        val recipientSpec = ecSpec(recipientPublicKey)
        require(!recipientPublicKey.privateMaterial) { "JWE recipient key must contain public material only" }
        val curve = recipientSpec.toCryptographyCurve()
        val recipientKey = validateEcPoint(recipientPublicKey, recipientSpec, provider)
        validateAgreementMetadata(recipientPublicKey)
        require(
            agreementPartyUInfo.isEmpty() ||
                agreementPartyVInfo.isEmpty() ||
                !agreementPartyUInfo.contentEquals(agreementPartyVInfo),
        ) { "JWE apu and apv values must be distinct" }
        require(protectedHeader.keys.none { it in reservedHeaderNames }) { "JWE protected header overrides reserved members" }
        val ephemeral = provider.get(ECDH).keyPairGenerator(curve).generateKey()
        val exportedEphemeral = Jwk.parse(
            EncodedKey.Jwk(
                BinaryData(ephemeral.publicKey.encodeToByteArray(EC.PublicKey.Format.JWK)),
                privateMaterial = false,
            ),
        )
        val ephemeralPublicJwk = EncodedKey.Jwk(
            BinaryData(
                json.encodeToString(
                    JsonObject(exportedEphemeral.filterKeys { it == "kty" || it == "crv" || it == "x" || it == "y" }),
                ).encodeToByteArray(),
            ),
            privateMaterial = false,
        )
        val header = JsonObject(
            buildMap {
                put("alg", JsonPrimitive(KEY_MANAGEMENT_ALGORITHM))
                put("enc", JsonPrimitive(contentEncryption.identifier))
                put("epk", Jwk.parse(ephemeralPublicJwk))
                if (agreementPartyUInfo.isNotEmpty()) put("apu", JsonPrimitive(base64Url.encode(agreementPartyUInfo)))
                if (agreementPartyVInfo.isNotEmpty()) put("apv", JsonPrimitive(base64Url.encode(agreementPartyVInfo)))
                putAll(protectedHeader)
            },
        )
        val encodedHeader = base64Url.encode(json.encodeToString(header).encodeToByteArray())
        val sharedSecret = ephemeral.privateKey.sharedSecretGenerator()
            .generateSharedSecretToByteArray(recipientKey)
        val contentEncryptionKey = concatKdf(
            sharedSecret,
            contentEncryption,
            agreementPartyUInfo,
            agreementPartyVInfo,
            provider,
        )
        val aesKey = provider.get(AES.GCM).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, contentEncryptionKey)
        val encrypted = aesKey.cipher().encrypt(plaintext, encodedHeader.encodeToByteArray())
        require(encrypted.size >= IV_SIZE_BYTES + TAG_SIZE_BYTES)
        val aeadCiphertext = AeadCiphertext(
            algorithm = AeadAlgorithm.AesGcm(contentEncryption.keySizeBytes * Byte.SIZE_BITS),
            nonce = BinaryData(encrypted.copyOfRange(0, IV_SIZE_BYTES)),
            ciphertext = BinaryData(encrypted.copyOfRange(IV_SIZE_BYTES, encrypted.size - TAG_SIZE_BYTES)),
            authenticationTag = BinaryData(encrypted.copyOfRange(encrypted.size - TAG_SIZE_BYTES, encrypted.size)),
        )
        return listOf(
            encodedHeader,
            "",
            base64Url.encode(aeadCiphertext.nonce.toByteArray()),
            base64Url.encode(aeadCiphertext.ciphertext.toByteArray()),
            base64Url.encode(aeadCiphertext.authenticationTag.toByteArray()),
        )
            .joinToString(".")
    }

    suspend fun decrypt(
        compactJwe: String,
        recipientKey: Key,
        allowedContentEncryptions: Set<JweContentEncryption>,
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): DecryptedJwe {
        val parts = compactJwe.split('.')
        require(parts.size == 5) { "Compact JWE must have exactly five parts" }
        require(parts[0].isNotEmpty()) { "JWE protected header cannot be empty" }
        require(parts[1].isEmpty()) { "ECDH-ES direct agreement requires an empty encrypted-key part" }
        require(parts[2].isNotEmpty()) { "JWE initialization vector cannot be empty" }
        require(parts[4].isNotEmpty()) { "JWE authentication tag cannot be empty" }
        require(parts.none { '=' in it }) { "Compact JWE must use unpadded base64url" }

        val header = json.parseToJsonElement(
            base64Url.decode(parts[0]).decodeToString(throwOnInvalidSequence = true),
        ) as? JsonObject ?: throw IllegalArgumentException("JWE protected header must be a JSON object")
        require(stringHeader(header, "alg") == KEY_MANAGEMENT_ALGORITHM) { "Unsupported JWE key-management algorithm" }
        require("crit" !in header && "zip" !in header) { "Critical and compressed JWE content is not supported" }
        val contentEncryption = JweContentEncryption.parse(stringHeader(header, "enc"))
        require(contentEncryption in allowedContentEncryptions) { "JWE content-encryption algorithm is not allowed" }
        val ephemeralJwk = header["epk"] as? JsonObject
            ?: throw IllegalArgumentException("JWE ephemeral public key is missing or invalid")
        require("d" !in ephemeralJwk) { "JWE ephemeral key must not contain private material" }
        val encodedEphemeral = EncodedKey.Jwk(
            BinaryData(json.encodeToString(ephemeralJwk).encodeToByteArray()),
            privateMaterial = false,
        )
        validateAgreementMetadata(encodedEphemeral)
        val ephemeralSpec = ecSpec(encodedEphemeral)
        require(ephemeralSpec == recipientKey.spec) { "JWE ephemeral and recipient curves do not match" }
        validateEcPoint(encodedEphemeral, ephemeralSpec, provider)
        require(recipientKey.capabilities.supportsKeyAgreementAlgorithm(KeyAgreementAlgorithm.Ecdh)) {
            "JWE recipient key does not support ECDH"
        }
        val agreement = requireNotNull(recipientKey.capabilities.keyAgreement) { "JWE recipient key cannot perform agreement" }
        val partyUInfo = optionalBase64Header(header, "apu")
        val partyVInfo = optionalBase64Header(header, "apv")
        require("apu" !in header || "apv" !in header || !partyUInfo.contentEquals(partyVInfo)) {
            "JWE apu and apv values must be distinct"
        }
        val sharedSecret = agreement.generateSharedSecret(encodedEphemeral, KeyAgreementAlgorithm.Ecdh).toByteArray()
        val contentEncryptionKey = concatKdf(
            sharedSecret,
            contentEncryption,
            partyUInfo,
            partyVInfo,
            provider,
        )
        val iv = base64Url.decode(parts[2])
        require(iv.size == IV_SIZE_BYTES) { "JWE AES-GCM IV must be 96 bits" }
        val ciphertext = base64Url.decode(parts[3])
        val tag = base64Url.decode(parts[4])
        require(tag.size == TAG_SIZE_BYTES) { "JWE AES-GCM tag must be 128 bits" }
        val aeadCiphertext = AeadCiphertext(
            algorithm = AeadAlgorithm.AesGcm(contentEncryption.keySizeBytes * Byte.SIZE_BITS),
            nonce = BinaryData(iv),
            ciphertext = BinaryData(ciphertext),
            authenticationTag = BinaryData(tag),
        )
        val encrypted = aeadCiphertext.nonce.toByteArray() +
            aeadCiphertext.ciphertext.toByteArray() +
            aeadCiphertext.authenticationTag.toByteArray()
        val aesKey = provider.get(AES.GCM).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, contentEncryptionKey)
        val plaintext = aesKey.cipher().decrypt(encrypted, parts[0].encodeToByteArray())
        return DecryptedJwe(header, plaintext, contentEncryption)
    }

    private suspend fun concatKdf(
        sharedSecret: ByteArray,
        contentEncryption: JweContentEncryption,
        partyUInfo: ByteArray,
        partyVInfo: ByteArray,
        provider: CryptographyProvider,
    ): ByteArray {
        val algorithmId = contentEncryption.identifier.encodeToByteArray()
        val otherInfo = lengthPrefixed(algorithmId) +
            lengthPrefixed(partyUInfo) +
            lengthPrefixed(partyVInfo) +
            uint32(contentEncryption.keySizeBytes * Byte.SIZE_BITS)
        val digestInput = uint32(1) + sharedSecret + otherInfo
        return provider.get(SHA256).hasher().hash(digestInput).copyOf(contentEncryption.keySizeBytes)
    }

    private fun ecSpec(jwk: EncodedKey.Jwk): KeySpec.Ec {
        val parsed = Jwk.parse(jwk)
        require(stringHeader(parsed, "kty") == "EC") { "JWE agreement key must be an EC JWK" }
        val (spec, coordinateSize) = when (stringHeader(parsed, "crv")) {
            "P-256" -> KeySpec.Ec(EcCurve.P256) to 32
            "P-384" -> KeySpec.Ec(EcCurve.P384) to 48
            "P-521" -> KeySpec.Ec(EcCurve.P521) to 66
            else -> throw IllegalArgumentException("Unsupported JWE elliptic curve")
        }
        validateCoordinate(stringHeader(parsed, "x"), coordinateSize)
        validateCoordinate(stringHeader(parsed, "y"), coordinateSize)
        return spec
    }

    private fun validateAgreementMetadata(jwk: EncodedKey.Jwk) {
        val metadata = Jwk.metadata(jwk)
        require(metadata.use == null || metadata.use == JwkUse.ENCRYPTION) {
            "JWE agreement key use must be enc"
        }
        require(
            metadata.operations == null ||
                JwkOperation.DERIVE_KEY in metadata.operations ||
                JwkOperation.DERIVE_BITS in metadata.operations,
        ) { "JWE agreement key does not permit derivation" }
        require(metadata.algorithm == null || metadata.algorithm == KEY_MANAGEMENT_ALGORITHM) {
            "JWE agreement key algorithm must be ECDH-ES"
        }
    }

    private fun validateCoordinate(value: String, expectedSize: Int) {
        require('=' !in value) { "JWE EC coordinates must use unpadded base64url" }
        val decoded = try {
            base64Url.decode(value)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid JWE EC coordinate", cause)
        }
        require(decoded.size == expectedSize) { "JWE EC coordinate has an invalid size" }
        require(base64Url.encode(decoded) == value) { "JWE EC coordinate is not canonical base64url" }
    }

    private suspend fun validateEcPoint(
        jwk: EncodedKey.Jwk,
        spec: KeySpec.Ec,
        provider: CryptographyProvider,
    ): ECDH.PublicKey {
        return provider.get(ECDH).publicKeyDecoder(spec.toCryptographyCurve())
            .decodeFromByteArray(EC.PublicKey.Format.JWK, jwk.data.toByteArray())
    }

    private fun KeySpec.Ec.toCryptographyCurve(): EC.Curve = when (curve) {
        EcCurve.P256 -> EC.Curve.P256
        EcCurve.P384 -> EC.Curve.P384
        EcCurve.P521 -> EC.Curve.P521
        EcCurve.SECP256K1 -> throw IllegalArgumentException("secp256k1 is not supported for JWE")
        else -> EC.Curve(curve.name)
    }

    private fun stringHeader(header: JsonObject, name: String): String {
        val value = header[name] as? JsonPrimitive
            ?: throw IllegalArgumentException("JWE $name header is missing or invalid")
        require(value.isString) { "JWE $name header must be a string" }
        return value.content
    }

    private fun optionalBase64Header(header: JsonObject, name: String): ByteArray =
        header[name]?.let { base64Url.decode(stringHeader(header, name)) } ?: byteArrayOf()

    private fun lengthPrefixed(value: ByteArray): ByteArray = uint32(value.size) + value

    private fun uint32(value: Int): ByteArray {
        require(value >= 0)
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    private val reservedHeaderNames = setOf("alg", "enc", "epk", "apu", "apv", "crit", "zip")
}
