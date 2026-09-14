package id.walt.crypto2.signum

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.asn1.Asn1Time
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificate
import at.asitplus.signum.indispensable.pki.X509Certificate
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.toSpkiDer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

internal fun SignumKeyPolicy.androidSettings(): SignumPlatformPolicy.AndroidKeystore = when (val settings = platform) {
    SignumPlatformPolicy.Default -> SignumPlatformPolicy.AndroidKeystore()
    is SignumPlatformPolicy.AndroidKeystore -> settings
    else -> error("iOS key settings cannot be applied to Android")
}

internal fun SignumKeyPolicy.supportsAndroidSettings(importing: Boolean): Boolean {
    if (platform is SignumPlatformPolicy.IosKeychain) return false
    val settings = androidSettings()
    if (importing && (attestationChallenge != null || settings.attestKeyAlias != null)) return false
    if (Build.VERSION.SDK_INT < 31 && (settings.maxUsageCount != null || settings.attestKeyAlias != null)) return false
    if (Build.VERSION.SDK_INT < 30 && authentication is SignumAuthenticationPolicy.UserPresence) return false
    return true
}

internal suspend fun importAndroidPrivateKey(alias: String, material: EncodedKey.Jwk,
                                             spec: KeySpec, policy: SignumKeyPolicy) {
    val store = androidKeyStore()
    require(!store.containsAlias(alias)) { "Key alias already exists" }
    val jwk = Json.parseToJsonElement(material.data.toByteArray().decodeToString()).jsonObject
    val scalar = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        .decode(jwk.getValue("d").jsonPrimitive.content)
    try {
        val parameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val key = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, scalar), parameters))
        val certificate = importCertificate(key, material.toSpkiDer(spec).data.toByteArray())
        val settings = policy.androidSettings()
        fun protection(strongBox: Boolean): KeyProtection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .setUnlockedDeviceRequired(settings.unlockedDeviceRequired)
            .setUserConfirmationRequired(settings.userConfirmationRequired)
            .setUserPresenceRequired(settings.userPresenceRequired)
            .apply {
                settings.validFromEpochMillis?.let { setKeyValidityStart(Date(it)) }
                settings.validUntilEpochMillis?.let { setKeyValidityEnd(Date(it)) }
                if (Build.VERSION.SDK_INT >= 31) settings.maxUsageCount?.let(::setMaxUsageCount)
                (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.let {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(it.timeoutSeconds, it.androidAuthenticationTypes())
                    setInvalidatedByBiometricEnrollment(it.biometric && !it.allowNewBiometrics)
                }
            }.build()
        val strongBox = settings.strongBox != SignumHardwarePolicy.DISCOURAGED
        try {
            store.setEntry(alias, KeyStore.PrivateKeyEntry(key, arrayOf(certificate)), protection(strongBox))
        } catch (cause: android.security.keystore.StrongBoxUnavailableException) {
            if (settings.strongBox != SignumHardwarePolicy.PREFERRED) throw cause
            // Only a classified lack of StrongBox permits falling back to the requested TEE policy.
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            store.setEntry(alias, KeyStore.PrivateKeyEntry(key, arrayOf(certificate)), protection(false))
        }
    } finally { scalar.fill(0) }
}

internal fun SignumAuthenticationPolicy.UserPresence.androidAuthenticationTypes(): Int =
    (if (biometric) KeyProperties.AUTH_BIOMETRIC_STRONG else 0) or
        (if (deviceCredential) KeyProperties.AUTH_DEVICE_CREDENTIAL else 0)

internal fun generateAndroidP256Key(alias: String, policy: SignumKeyPolicy) {
    require(!androidKeyStore().containsAlias(alias)) { "Key alias already exists" }
    val settings = policy.androidSettings()
    fun generate(strongBox: Boolean) {
        val parameters = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .setUnlockedDeviceRequired(settings.unlockedDeviceRequired)
            .setUserConfirmationRequired(settings.userConfirmationRequired)
            .setUserPresenceRequired(settings.userPresenceRequired)
            .apply {
                settings.validFromEpochMillis?.let { setKeyValidityStart(Date(it)) }
                settings.validUntilEpochMillis?.let { setKeyValidityEnd(Date(it)) }
                if (Build.VERSION.SDK_INT >= 31) {
                    settings.maxUsageCount?.let(::setMaxUsageCount)
                    settings.attestKeyAlias?.let(::setAttestKeyAlias)
                }
                policy.attestationChallenge?.let { setAttestationChallenge(it.toByteArray()) }
                (policy.authentication as? SignumAuthenticationPolicy.UserPresence)?.let {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(it.timeoutSeconds, it.androidAuthenticationTypes())
                    setInvalidatedByBiometricEnrollment(it.biometric && !it.allowNewBiometrics)
                }
            }.build()
        KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply { initialize(parameters) }.generateKeyPair()
    }
    try { generate(settings.strongBox != SignumHardwarePolicy.DISCOURAGED) }
    catch (cause: android.security.keystore.StrongBoxUnavailableException) {
        if (settings.strongBox != SignumHardwarePolicy.PREFERRED) throw cause
        if (androidKeyStore().containsAlias(alias)) androidKeyStore().deleteEntry(alias)
        generate(false)
    }
}

/** Local self-signed certificate required by KeyStore.PrivateKeyEntry; never attestation evidence. */
private fun importCertificate(key: PrivateKey, spki: ByteArray): java.security.cert.Certificate {
    val name = listOf(RelativeDistinguishedName(AttributeTypeAndValue.CommonName(Asn1String.UTF8("Wallet signing key"))))
    val now = Clock.System.now()
    val tbs = TbsCertificate(serialNumber = byteArrayOf(1), signatureAlgorithm = X509SignatureAlgorithm.ES256,
        issuerName = name, subjectName = name, validFrom = Asn1Time(now - 1.days),
        validUntil = Asn1Time(now + 36500.days), publicKey = CryptoPublicKey.decodeFromDer(spki))
    val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(tbs.encodeToDer()) }.sign()
    val certificate = X509Certificate(tbs, X509SignatureAlgorithm.ES256, CryptoSignature.EC.decodeFromDer(signature))
    return CertificateFactory.getInstance("X.509").generateCertificate(certificate.encodeToDer().inputStream())
}
