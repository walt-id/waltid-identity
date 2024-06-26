package id.walt.crypto.keys

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.JWKGenerator
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKeyCreator
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto.utils.encodeToBase58String
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.ECPointUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;


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

    override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?): Key =
        JWKKey(
            when (type) {
                KeyType.Ed25519 -> OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(rawPublicKey)).build()
                KeyType.secp256k1 -> ecRawToJwk(rawPublicKey, Curve.SECP256K1)
                KeyType.secp256r1 -> ecRawToJwk(rawPublicKey, Curve.P_256)
                else -> TODO("Not yet implemented: $type for key")
            }
        )

    override suspend fun importJWK(jwk: String): Result<JWKKey> =
        runCatching { JWKKey(JWK.parse(jwk)) }

    override suspend fun importPEM(pem: String): Result<JWKKey> =
        runCatching { JWKKey(JWK.parseFromPEMEncodedObjects(pem)) }

    private fun ecRawToJwk(rawPublicKey: ByteArray, curve: Curve): JWK {
        val spec: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec(curve.name)
        val params: ECNamedCurveSpec = ECNamedCurveSpec(spec.name, spec.curve, spec.g, spec.n)
        val point: ECPoint? = ECPointUtil.decodePoint(params.curve, rawPublicKey)
        val pubKeySpec: ECPublicKeySpec = ECPublicKeySpec(point, params)
        val kf: KeyFactory = KeyFactory.getInstance("ECDH", BouncyCastleProvider())
        val pubKey: ECPublicKey = kf.generatePublic(pubKeySpec) as ECPublicKey
        return ECKey.Builder(
            curve,
            Base64URL.encode(pubKey.params.generator.affineX.toByteArray()),
            Base64URL.encode(pubKey.params.generator.affineY.toByteArray())
        ).build()
    }
}

fun main() = runBlocking {
//    val identifier =
//        "zWmu9RCrS3hBhGSyhKfNSywuTWFCMjMxJeKhMPt6jyYidkimvaipCryGCiM61EAjAL1zY6iha35QsbSsahzyXQZEJu4XuUshQmGEPeKwzRwCCGmvMQjKSP7os7tzWoYh"
//    val raw = MultiBaseUtils.convertMultiBase58BtcToRawKey(identifier)
//    val code = MultiCodecUtils.getMultiCodecKeyCode(identifier)
//    val type = MultiCodecUtils.getKeyTypeFromKeyCode(code)
//    val key = JWKKey.importRawPublicKey(type, raw, null)
//    println(key.exportJWK())
}