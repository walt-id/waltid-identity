package id.walt.openid4vp.clientidprefix

// Required for BouncyCastle certificate generation
// Required for JWS/JWT creation
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.*

/**
 * A helper for generating keys, self-signed certificates, and signing JWTs for testing.
 */
object CryptoTestUtils {

    /*fun generateEcKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }*/

    /**
     * Creates a self-signed X.509 certificate.
     */
    fun createSelfSignedCertificate(keyPair: KeyPair, dnsName: String): X509Certificate {
        val issuer = X500Name("CN=$dnsName")
        val subject = issuer // Self-signed
        val serial = BigInteger(64, SecureRandom())
        val now = Date()
        val expiry = Date(now.time + 365 * 24 * 60 * 60 * 1000L) // 1 year validity

        val pubKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)

        val certBuilder = X509v3CertificateBuilder(issuer, serial, now, expiry, subject, pubKeyInfo)

        val generalNames = GeneralNames(GeneralName(GeneralName.dNSName, dnsName))
        certBuilder.addExtension(Extension.subjectAlternativeName, false, generalNames)

        val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)

        return JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
    }

    /**
     * Signs a JWT and includes the certificate in the `x5c` header.
     */
    /*fun signJwtWithCert(payload: ByteArray, keyPair: KeyPair, derEncodedX509Cert: ByteArray): String {
        val signer = ECDSASigner(keyPair.private as java.security.interfaces.ECPrivateKey)


        JWTClaimsSet.
        val claimsSet = JWTClaimsSet.Builder().apply {
            payload.forEach { (k, v) -> claim(k, v) }
        }.build()

        val header = JWSHeader.Builder(JWSAlgorithm.ES256)
            .x509CertChain(listOf(com.nimbusds.jose.util.Base64.encode(derEncodedX509Cert)))
            .build()

        val signedJWT = SignedJWT(header, claimsSet)
        signedJWT.sign(signer)
        return signedJWT.serialize()
    }*/

    /**
     * Signs a JWT with a given key and includes the key's KID in the header.
     */
    fun signJwtWithKid(payload: Map<String, Any>, ecKey: ECKey): String {
        val signer = ECDSASigner(ecKey)
        val claimsSet = JWTClaimsSet.Builder().apply {
            payload.forEach { (k, v) -> claim(k, v) }
        }.build()

        val signedJWT = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecKey.keyID).build(),
            claimsSet
        )
        signedJWT.sign(signer)
        return signedJWT.serialize()
    }
}

suspend fun a() {
    // 1. Setup real crypto objects
    val keyPair = JWKKey.generate(KeyType.secp256r1) //CryptoTestUtils.generateEcKeyPair()

    println("Key: ${KeySerialization.serializeKey(keyPair)}")

    val dnsName = "verifier.example.com"
    val certificate = CryptoTestUtils.createSelfSignedCertificate(keyPair._internalJwk.toECKey().toKeyPair(), dnsName)

    println("Certificate DER base64: ${certificate.encoded.encodeToBase64()}")

    val payload = mapOf("response_type" to "vp_token", "nonce" to "1234")

    val signedJws = keyPair.signJws(
        Json.encodeToJsonElement(payload).toString().encodeToByteArray(),
        mapOf("x5c" to JsonArray(listOf(JsonPrimitive(certificate.encoded.encodeToBase64()))))
    )
    //val signedJws = CryptoTestUtils.signJwtWithCert(payload, keyPair, certificate)
    //println("Real jws:")
    //println("dns name = $dnsName")
    println("Signed JWS: $signedJws")
}

/*fun b() {
    val keyPair = CryptoTestUtils.generateEcKeyPair()
    val certDnsName = "verifier.example.com"
    val certificate = CryptoTestUtils.createSelfSignedCertificate(keyPair, certDnsName)
    val payload = mapOf("response_type" to "vp_token")
    val signedJws = CryptoTestUtils.signJwtWithCert(payload, keyPair, certificate)
    println("Real jws:")
    println("dns name = $certDnsName")
    println(signedJws)
}*/

/*suspend fun c() {
    WaltidServices.minimalInit()
    val key = JWKKey.generate(KeyType.secp256r1)
    println(KeySerialization.serializeKey(key))
    val did = DidService.registerByKey("jwk", key)
    println(did.did)

    val payload = mapOf("response_type" to "vp_token", "nonce" to "xyz")
    val s = CryptoTestUtils.signJwtWithKid(payload, key._internalJwk.toECKey())
    println(s)
}*/

suspend fun main() {
    a()
   // b()
}
