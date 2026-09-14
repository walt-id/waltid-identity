package id.walt.wallet2.mobile.identity

import at.asitplus.signum.indispensable.CryptoPrivateKey
import at.asitplus.signum.indispensable.ECCurve
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Version 1 derivation shared by Android and iOS. See identity-recovery-format.md before changing it. */
internal object IdentityRecoveryMaterial {
    /** Generates 256 bits of OS-backed entropy. Callers must keep these bytes secret. */
    fun createSeed(): ByteArray = CryptographyRandom.nextBytes(32)

    /** Derives a P-256 private JWK for a versioned identity domain and nonnegative index. */
    suspend fun derive(seed: ByteArray, identityDomain: String, keyIndex: Int = 0): EncodedKey.Jwk {
        require(seed.size == 32) { "An identity seed must contain 32 bytes" }
        require(identityDomain.length in 1..128 && identityDomain.all { it.code in 0x21..0x7e }) {
            "Identity domain must be bounded printable ASCII"
        }
        require(keyIndex >= 0) { "Key index cannot be negative" }
        val extracted = hmac("id.walt.wallet.identity/recovery/v1".encodeToByteArray(), seed)
        try {
            for (attempt in 0..255) {
                val info = "P-256/signing/$identityDomain/$keyIndex/$attempt".encodeToByteArray()
                val scalar = hmac(extracted, info + byteArrayOf(1))
                try {
                    val value = BigInteger.fromByteArray(scalar, Sign.POSITIVE)
                    if (value == BigInteger.ZERO || value >= ORDER) continue
                    val key = CryptoPrivateKey.EC.WithPublicKey(value, ECCurve.SECP_256_R_1,
                        encodeCurve = true, encodePublicKey = true)
                    val point = key.publicKey.iosEncoded
                    val jwk = buildJsonObject {
                        put("kty", "EC"); put("crv", "P-256")
                        put("x", base64.encode(point.copyOfRange(1, 33)))
                        put("y", base64.encode(point.copyOfRange(33, 65)))
                        put("d", base64.encode(scalar))
                    }
                    return EncodedKey.Jwk(BinaryData(jwk.toString().encodeToByteArray()), privateMaterial = true)
                } finally { scalar.fill(0) }
            }
            error("P-256 scalar derivation exhausted its retry bound")
        } finally { extracted.fill(0) }
    }

    private suspend fun hmac(key: ByteArray, data: ByteArray): ByteArray = CryptographyProvider.Default.get(HMAC)
        .keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, key)
        .signatureGenerator().generateSignature(data)

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val ORDER = BigInteger.parseString("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)
}
