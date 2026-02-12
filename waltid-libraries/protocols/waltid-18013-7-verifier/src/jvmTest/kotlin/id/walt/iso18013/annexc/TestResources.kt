package id.walt.iso18013.annexc

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.jwk.JWKKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.util.encoders.Hex
import java.math.BigInteger

object TestResources {


    /**
     * Creates a P-256 JWKKey from a raw hex private key scalar.
     * Used for converting test vectors into JWK objects.
     */
    fun createJwkKeyFromRawHex(hexPrivateKey: String): JWKKey {
        // 1. Parse the private scalar 'd'
        val d = BigInteger(1, Hex.decode(hexPrivateKey))

        // 2. Derive the Public Point Q (Q = d * G)
        val ecSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val Q = ecSpec.g.multiply(d).normalize()

        // 3. Extract affine coordinates
        val xBytes = Q.affineXCoord.encoded
        val yBytes = Q.affineYCoord.encoded

        // 4. Build the Nimbus ECKey
        // We use Base64URL.encode to handle the wrapping correctly
        val ecKey = ECKey.Builder(Curve.P_256, Base64URL.encode(xBytes), Base64URL.encode(yBytes))
            .d(Base64URL.encode(d)) // Handles BigInteger to Base64URL conversion (stripping sign bit if needed)
            .build()

        // 5. Wrap in your JWKKey class
        return JWKKey(ecKey)
    }

}
