package id.walt.crypto.keys

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.JWKGenerator
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKeyCreator
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.jce.ECNamedCurveTable
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

object JvmJWKKeyCreator : JWKKeyCreator {

    override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey {
        val keyGenerator: JWKGenerator<out JWK> = when (type) {
            KeyType.Ed25519 -> OctetKeyPairGenerator(Curve.Ed25519)
            KeyType.secp256r1 -> ECKeyGenerator(Curve.P_256)
            KeyType.secp256k1 -> ECKeyGenerator(Curve.SECP256K1)
            KeyType.RSA -> RSAKeyGenerator(metadata?.keySize ?: 2048)
        }.run {
            if (type == KeyType.secp256k1) {
                provider(BouncyCastleProviderSingleton.getInstance())
            } else this
        }.keyIDFromThumbprint(true)

        val jwk = keyGenerator.generate()

        return JWKKey(jwk)
    }

    override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?): Key = JWKKey(
        when (type) {
            KeyType.Ed25519 -> OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(rawPublicKey)).build()
            KeyType.secp256k1 -> ecRawToJwk(rawPublicKey, Curve.SECP256K1)
            KeyType.secp256r1 -> ecRawToJwk(rawPublicKey, Curve.P_256)
            KeyType.RSA -> rawRsaToJwk(rawPublicKey)
        }
    )

    override suspend fun importJWK(jwk: String): Result<JWKKey> =
        runCatching { JWKKey(JWK.parse(jwk)) }

    override suspend fun importPEM(pem: String): Result<JWKKey> =
        runCatching { JWKKey(JWK.parseFromPEMEncodedObjects(pem)) }

    private fun ecRawToJwk(rawPublicKey: ByteArray, curve: Curve): JWK =
        ECNamedCurveTable.getParameterSpec(curve.name).curve.decodePoint(rawPublicKey).let {
            val x: ByteArray = it.xCoord.encoded
            val y: ByteArray = it.yCoord.encoded
            return ECKey.Builder(curve, Base64URL.encode(x), Base64URL.encode(y)).build()
        }

    private fun rawRsaToJwk(rawPublicKey: ByteArray): JWK {
        val keySequence = ASN1Sequence.getInstance(rawPublicKey)
        val modulus = ASN1Integer.getInstance(keySequence.getObjectAt(0))
        val exp = ASN1Integer.getInstance(keySequence.getObjectAt(1))
        val keySpec = RSAPublicKeySpec(modulus.positiveValue, exp.positiveValue)
        val keyFactory = KeyFactory.getInstance("RSA", "BC")
        val recoveredPublicKey = keyFactory.generatePublic(keySpec)
        val recoveredRsaKey = recoveredPublicKey as RSAPublicKey
        return RSAKey.Builder(recoveredRsaKey).build()
    }
}
