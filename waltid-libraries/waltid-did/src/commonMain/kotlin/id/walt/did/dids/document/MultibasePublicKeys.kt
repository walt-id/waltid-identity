package id.walt.did.dids.document

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.MontgomeryCurve
import id.walt.crypto2.keys.toStoredSoftwareKey

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.XDH
import id.walt.crypto2.serialization.BinaryData
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Decodes multibase/multicodec-encoded public keys, the form `did:key` identifiers and `publicKeyMultibase`
 * verification methods use.
 *
 * Only the public-key encoding lives here - no DID semantics - so this stays usable for any multicodec consumer.
 * Elliptic-curve points arrive compressed, and rather than decompressing them by hand they are handed to the
 * platform's own SEC1 decoder ([EC.PublicKey.Format.RAW.Compressed]), which every cryptography-kotlin provider
 * implements even where the SPKI path rejects compressed points.
 */
public object MultibasePublicKeys {

    public data class DecodedPublicKey(val spec: KeySpec, val jwk: EncodedKey.Jwk)

    /**
     * Decodes a multibase value, with or without a `did:key:` prefix and with any `#fragment` removed.
     * Accepts the base58-btc form (`z…`) that `did:key` mandates.
     */
    public suspend fun decode(
        value: String,
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): DecodedPublicKey {
        val identifier = value.removePrefix("did:key:").substringBefore('#')
        require(identifier.startsWith(MULTIBASE_BASE58_BTC)) {
            "Unsupported multibase encoding, expected base58-btc ('z'): $value"
        }
        val decoded = decodeBase58(identifier.substring(1))
        val (codec, offset) = decodeVarInt(decoded)
        val keyBytes = decoded.copyOfRange(offset, decoded.size)

        return when (codec) {
            CODEC_ED25519 -> okp(KeySpec.Edwards(EdwardsCurve.ED25519), keyBytes, provider)
            CODEC_X25519 -> okp(KeySpec.Montgomery(MontgomeryCurve.X25519), keyBytes, provider)
            CODEC_P256 -> ec(EcCurve.P256, keyBytes, provider)
            CODEC_P384 -> ec(EcCurve.P384, keyBytes, provider)
            CODEC_P521 -> ec(EcCurve.P521, keyBytes, provider)
            CODEC_SECP256K1 -> ec(EcCurve.SECP256K1, keyBytes, provider)
            CODEC_RSA -> rsa(keyBytes)
            CODEC_JWK_JCS -> jwkJcs(keyBytes)
            else -> throw IllegalArgumentException("Unsupported multicodec key code: 0x${codec.toString(16)}")
        }
    }

    /**
     * Providers disagree on the optional JWK hints: Web Crypto emits `alg` (e.g. `Ed25519`) where the JDK omits
     * it, and crypto2 rejects a key whose `alg` contradicts the signature algorithm. A did:key identifier carries
     * no algorithm, so these members are dropped rather than invented.
     */
    private fun EncodedKey.Jwk.withoutAlgorithmHints(): EncodedKey.Jwk {
        val members = Json.parseToJsonElement(data.toByteArray().decodeToString()).jsonObject
        val filtered = members.filterKeys { it !in droppedJwkMembers }
        if (filtered.size == members.size) return this
        return EncodedKey.Jwk(
            BinaryData(Json.encodeToString(JsonObject(filtered)).encodeToByteArray()),
            privateMaterial = privateMaterial,
        )
    }

    private suspend fun ec(
        curve: EcCurve,
        point: ByteArray,
        provider: CryptographyProvider,
    ): DecodedPublicKey {
        // did:key carries compressed points, but accept uncompressed too - both are valid SEC1.
        val format = when (point.firstOrNull()) {
            0x02.toByte(), 0x03.toByte() -> EC.PublicKey.Format.RAW.Compressed
            0x04.toByte() -> EC.PublicKey.Format.RAW
            else -> throw IllegalArgumentException("Not a SEC1 encoded point for $curve")
        }
        val jwk = provider.get(ECDSA)
            .publicKeyDecoder(EC.Curve(curve.name))
            .decodeFromByteArray(format, point)
            .encodeToByteArray(EC.PublicKey.Format.JWK)
        return DecodedPublicKey(
            KeySpec.Ec(curve),
            EncodedKey.Jwk(BinaryData(jwk), privateMaterial = false).withoutAlgorithmHints(),
        )
    }

    private suspend fun okp(
        spec: KeySpec,
        raw: ByteArray,
        provider: CryptographyProvider,
    ): DecodedPublicKey {
        val jwk = when (spec) {
            is KeySpec.Edwards -> provider.get(EdDSA)
                .publicKeyDecoder(EdDSA.Curve.Ed25519)
                .decodeFromByteArray(EdDSA.PublicKey.Format.RAW, raw)
                .encodeToByteArray(EdDSA.PublicKey.Format.JWK)
            is KeySpec.Montgomery -> provider.get(XDH)
                .publicKeyDecoder(XDH.Curve.X25519)
                .decodeFromByteArray(XDH.PublicKey.Format.RAW, raw)
                .encodeToByteArray(XDH.PublicKey.Format.JWK)
            else -> error("Unsupported OKP specification: $spec")
        }
        return DecodedPublicKey(
            spec,
            EncodedKey.Jwk(BinaryData(jwk), privateMaterial = false).withoutAlgorithmHints(),
        )
    }

    /**
     * `RSA`: the payload is a PKCS#1 `RSAPublicKey` (`SEQUENCE { modulus INTEGER, publicExponent INTEGER }`).
     * Both integers become JWK members verbatim, so no bignum arithmetic is needed - only the DER leading zero
     * that keeps an INTEGER positive has to go, because JWK values are unsigned.
     */
    private fun rsa(der: ByteArray): DecodedPublicKey {
        val reader = DerReader(der)
        val body = reader.readSequenceBody()
        val inner = DerReader(body)
        val modulus = inner.readIntegerAsUnsigned()
        val exponent = inner.readIntegerAsUnsigned()
        val jwk = buildJsonObject {
            put("kty", JsonPrimitive("RSA"))
            put("n", JsonPrimitive(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(modulus)))
            put("e", JsonPrimitive(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(exponent)))
        }
        return DecodedPublicKey(
            KeySpec.Rsa(modulus.size * Byte.SIZE_BITS),
            EncodedKey.Jwk(BinaryData(Json.encodeToString(jwk).encodeToByteArray()), privateMaterial = false),
        )
    }

    /** Minimal DER reader: only what a PKCS#1 RSA public key needs. */
    private class DerReader(private val bytes: ByteArray) {
        private var offset = 0

        fun readSequenceBody(): ByteArray {
            require(next() == 0x30.toByte()) { "Expected a DER SEQUENCE" }
            val length = readLength()
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun readIntegerAsUnsigned(): ByteArray {
            require(next() == 0x02.toByte()) { "Expected a DER INTEGER" }
            val length = readLength()
            val value = bytes.copyOfRange(offset, offset + length).also { offset += length }
            return if (value.size > 1 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value
        }

        private fun next(): Byte = bytes[offset++]

        private fun readLength(): Int {
            val first = next().toInt() and 0xFF
            if (first and 0x80 == 0) return first
            val byteCount = first and 0x7F
            require(byteCount in 1..4) { "Unsupported DER length" }
            var length = 0
            repeat(byteCount) { length = (length shl 8) or (next().toInt() and 0xFF) }
            return length
        }
    }

    /** `jwk_jcs-pub`: the payload is the JCS-serialised JWK itself, so the key specification comes from it. */
    private fun jwkJcs(bytes: ByteArray): DecodedPublicKey {
        val jwk = EncodedKey.Jwk(BinaryData(bytes), privateMaterial = false)
        return DecodedPublicKey(jwk.toStoredSoftwareKey(KeyId("multibase"), setOf(KeyUsage.VERIFY)).spec, jwk)
    }

    /** Unsigned LEB128 as multicodec uses it; returns the code and the offset of the key bytes. */
    private fun decodeVarInt(bytes: ByteArray): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = 0
        while (index < bytes.size) {
            val byte = bytes[index].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            index++
            if (byte and 0x80 == 0) return result to index
            shift += 7
            require(shift <= 28) { "Multicodec varint is too long" }
        }
        throw IllegalArgumentException("Truncated multicodec varint")
    }

    /**
     * Base58-btc decoding. Each character multiplies the running value by 58 and adds the digit; the byte buffer
     * is kept little-endian and reversed at the end. (Dividing by 58 instead is the *encoding* direction.)
     */
    private fun decodeBase58(value: String): ByteArray {
        require(value.isNotEmpty()) { "Empty base58 value" }
        val bytes = IntArray(value.length)
        var length = 0
        value.forEach { character ->
            var carry = BASE58_ALPHABET.indexOf(character)
            require(carry >= 0) { "Invalid base58 character: $character" }
            for (position in 0 until length) {
                carry += bytes[position] * 58
                bytes[position] = carry and 0xFF
                carry = carry shr 8
            }
            while (carry > 0) {
                bytes[length++] = carry and 0xFF
                carry = carry shr 8
            }
        }
        val leadingZeros = value.takeWhile { it == BASE58_ALPHABET[0] }.length
        return ByteArray(leadingZeros + length) { index ->
            if (index < leadingZeros) 0 else bytes[length - 1 - (index - leadingZeros)].toByte()
        }
    }

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val MULTIBASE_BASE58_BTC = "z"

    // https://github.com/multiformats/multicodec/blob/master/table.csv
    private const val CODEC_ED25519 = 0xed
    private const val CODEC_X25519 = 0xec
    private const val CODEC_SECP256K1 = 0xe7
    private const val CODEC_P256 = 0x1200
    private const val CODEC_P384 = 0x1201
    private const val CODEC_P521 = 0x1202
    private val droppedJwkMembers = setOf("alg", "use", "key_ops", "ext", "kid")

    private const val CODEC_RSA = 0x1205
    private const val CODEC_JWK_JCS = 0xeb51
}
