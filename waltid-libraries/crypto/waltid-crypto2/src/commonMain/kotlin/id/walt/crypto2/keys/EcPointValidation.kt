package id.walt.crypto2.keys

/**
 * Requires the affine point ([x], [y]) to lie on this Weierstrass curve: both coordinates are canonical field elements
 * (below p, encoded big-endian in the curve's coordinate size) and y² = x³ + ax + b (mod p).
 *
 * crypto2 runs this itself because the platforms disagree: the JDK's EC KeyFactory stores any coordinates it is given,
 * while WebCrypto and OpenSSL refuse an off-curve point at import. Without it an invalid point restored on the JVM and
 * reached ECDSA verification or ECDH as a peer key, where only the backend's own checks stood in the way.
 *
 * Every supported curve has cofactor 1, so a point on the curve is also in the prime-order subgroup. The coordinates
 * are public, so the arithmetic is not constant-time.
 */
internal fun EcCurve.requirePointOnCurve(x: ByteArray, y: ByteArray) {
    val equation = curveEquations[this] ?: throw IllegalArgumentException("Unsupported EC curve: $name")
    require(equation.contains(x, y)) { "EC public key is not a point on curve $name" }
}

/** [requirePointOnCurve] for a SEC1 uncompressed point, `0x04 || x || y`. */
internal fun EcCurve.requireUncompressedPointOnCurve(point: ByteArray) {
    require(point.size % 2 == 1 && point.first() == UNCOMPRESSED_POINT) {
        "EC public key is not an uncompressed point"
    }
    val size = point.size / 2
    requirePointOnCurve(point.copyOfRange(1, 1 + size), point.copyOfRange(1 + size, point.size))
}

private const val UNCOMPRESSED_POINT: Byte = 0x04

// Domain parameters from SEC 2 (NIST curves, secp256k1) and RFC 5639 (Brainpool).
private val curveEquations: Map<EcCurve, CurveEquation> by lazy {
    mapOf(
        EcCurve.P256 to CurveEquation(
            prime = "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
            a = "ffffffff00000001000000000000000000000000fffffffffffffffffffffffc",
            b = "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
        ),
        EcCurve.P384 to CurveEquation(
            prime = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000ffffffff",
            a = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000fffffffc",
            b = "b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875ac656398d8a2ed19d2a85c8edd3ec2aef",
        ),
        EcCurve.P521 to CurveEquation(
            prime = "01" + "ff".repeat(65),
            a = "01" + "ff".repeat(64) + "fc",
            b = "0051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef109e156193951ec7e937b1652c0bd3bb1bf07357" +
                "3df883d2c34f1ef451fd46b503f00",
        ),
        EcCurve.SECP256K1 to CurveEquation(
            prime = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f",
            a = "00",
            b = "07",
        ),
        EcCurve.BRAINPOOL_P256R1 to CurveEquation(
            prime = "a9fb57dba1eea9bc3e660a909d838d726e3bf623d52620282013481d1f6e5377",
            a = "7d5a0975fc2c3057eef67530417affe7fb8055c126dc5c6ce94a4b44f330b5d9",
            b = "26dc5c6ce94a4b44f330b5d9bbd77cbf958416295cf7e1ce6bccdc18ff8c07b6",
        ),
        EcCurve.BRAINPOOL_P384R1 to CurveEquation(
            prime = "8cb91e82a3386d280f5d6f7e50e641df152f7109ed5456b412b1da197fb71123acd3a729901d1a71874700133107ec53",
            a = "7bc382c63d8c150c3c72080ace05afa0c2bea28e4fb22787139165efba91f90f8aa5814a503ad4eb04a8c7dd22ce2826",
            b = "04a8c7dd22ce28268b39b55416f0447c2fb77de107dcd2a62e880ea53eeb62d57cb4390295dbc9943ab78696fa504c11",
        ),
        EcCurve.BRAINPOOL_P512R1 to CurveEquation(
            prime = "aadd9db8dbe9c48b3fd4e6ae33c9fc07cb308db3b3c9d20ed6639cca703308717d4d9b009bc66842aecda12ae6a380e6288" +
                "1ff2f2d82c68528aa6056583a48f3",
            a = "7830a3318b603b89e2327145ac234cc594cbdd8d3df91610a83441caea9863bc2ded5d5aa8253aa10a2ef1c98b9ac8b57f111" +
                "7a72bf2c7b9e7c1ac4d77fc94ca",
            b = "3df91610a83441caea9863bc2ded5d5aa8253aa10a2ef1c98b9ac8b57f1117a72bf2c7b9e7c1ac4d77fc94cadc083e679840" +
                "50b75ebae5dd2809bd638016f723",
        ),
    )
}

/**
 * The equation y² = x³ + ax + b over the prime field p, evaluated in Montgomery form with 32-bit limbs
 * (little-endian, one limb per Long so products and carries stay within 64 unsigned bits).
 */
private class CurveEquation(prime: String, a: String, b: String) {
    private val primeBytes = prime.hexToByteArray()
    val coordinateSize = primeBytes.size
    private val limbCount = (coordinateSize + 3) / 4
    private val modulus = limbs(primeBytes)

    // -p⁻¹ mod 2³², by Newton's iteration: an odd p is its own inverse mod 8, and each step doubles the correct bits.
    private val negativeInverse: Long = run {
        val low = modulus[0].toInt()
        var inverse = low
        repeat(4) { inverse *= 2 - low * inverse }
        (-inverse).toLong() and LIMB_MASK
    }

    // R² mod p with R = 2^(32 · limbCount): 1 doubled 2 · 32 · limbCount times.
    private val rSquared = LongArray(limbCount).also { value ->
        value[0] = 1
        repeat(2 * Int.SIZE_BITS * limbCount) { addModulo(value, value, value) }
    }
    private val aMontgomery = toMontgomery(limbs(a.hexToByteArray()))
    private val bMontgomery = toMontgomery(limbs(b.hexToByteArray()))

    fun contains(x: ByteArray, y: ByteArray): Boolean {
        if (x.size != coordinateSize || y.size != coordinateSize) return false
        val xLimbs = limbs(x)
        val yLimbs = limbs(y)
        if (compare(xLimbs, modulus) >= 0 || compare(yLimbs, modulus) >= 0) return false
        val xm = toMontgomery(xLimbs)
        val ym = toMontgomery(yLimbs)
        val left = multiply(ym, ym)
        // Horner: x³ + ax + b = (x² + a)·x + b
        val right = multiply(xm, xm)
        addModulo(right, aMontgomery, right)
        val cubic = multiply(right, xm)
        addModulo(cubic, bMontgomery, cubic)
        return left.contentEquals(cubic)
    }

    private fun toMontgomery(value: LongArray) = multiply(value, rSquared)

    private fun limbs(bigEndian: ByteArray): LongArray {
        require(bigEndian.size <= limbCount * 4) { "Field element is wider than the curve's prime" }
        val result = LongArray(limbCount)
        bigEndian.forEachIndexed { index, byte ->
            val position = bigEndian.size - 1 - index
            result[position / 4] = result[position / 4] or ((byte.toLong() and 0xff) shl (8 * (position % 4)))
        }
        return result
    }

    /** Montgomery product a·b·R⁻¹ mod p (CIOS) for a, b < p; the result is fully reduced. */
    private fun multiply(a: LongArray, b: LongArray): LongArray {
        val n = limbCount
        val t = LongArray(n + 2)
        for (i in 0 until n) {
            var carry = 0L
            for (j in 0 until n) {
                // At most (2³² − 1)² + 2·(2³² − 1) = 2⁶⁴ − 1, so the unsigned sum fits; ushr reads it unsigned.
                val sum = t[j] + a[j] * b[i] + carry
                t[j] = sum and LIMB_MASK
                carry = sum ushr 32
            }
            val top = t[n] + carry
            t[n] = top and LIMB_MASK
            t[n + 1] = top ushr 32

            val m = (t[0] * negativeInverse) and LIMB_MASK
            carry = (t[0] + m * modulus[0]) ushr 32
            for (j in 1 until n) {
                val sum = t[j] + m * modulus[j] + carry
                t[j - 1] = sum and LIMB_MASK
                carry = sum ushr 32
            }
            val shifted = t[n] + carry
            t[n - 1] = shifted and LIMB_MASK
            t[n] = t[n + 1] + (shifted ushr 32)
        }
        val result = t.copyOf(n)
        if (t[n] != 0L || compare(result, modulus) >= 0) subtractModulus(result)
        return result
    }

    /** target = (left + right) mod p for left, right < p; target may alias either operand. */
    private fun addModulo(left: LongArray, right: LongArray, target: LongArray) {
        var carry = 0L
        for (i in 0 until limbCount) {
            val sum = left[i] + right[i] + carry
            target[i] = sum and LIMB_MASK
            carry = sum ushr 32
        }
        if (carry != 0L || compare(target, modulus) >= 0) subtractModulus(target)
    }

    private fun subtractModulus(value: LongArray) {
        var borrow = 0L
        for (i in 0 until limbCount) {
            val difference = value[i] - modulus[i] - borrow
            value[i] = difference and LIMB_MASK
            borrow = if (difference < 0) 1 else 0
        }
    }

    private fun compare(left: LongArray, right: LongArray): Int {
        for (i in limbCount - 1 downTo 0) {
            if (left[i] != right[i]) return left[i].compareTo(right[i])
        }
        return 0
    }

    private companion object {
        const val LIMB_MASK = 0xffffffffL
    }
}
