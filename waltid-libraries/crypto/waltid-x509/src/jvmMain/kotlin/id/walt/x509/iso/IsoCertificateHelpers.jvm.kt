package id.walt.x509.iso

import id.walt.crypto.keys.KeyType
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*


internal actual fun generateCertificateSerialNo(): ByteString {
    val random = SecureRandom()
    val randomBytes = ByteArray(ISO_CERT_SERIAL_NUMBER_REQUIRED_LENGTH)
    random.nextBytes(randomBytes)
    return BigInteger(randomBytes).abs().toByteArray().toByteString()
}

//TODO: FIX THIS HERE SO THAT WE ONLY DO SIGNING WITH KEYS THAT ARE ALLOWED BY THE PROFILE
//TODO: ECDSA ONLY FOR IACA
//TODO: ECDSA AND EDDSA ALLOWED FOR DOCUMENT SIGNERS
internal fun getJcaSigningAlgorithmNameFromKeyType(
    keyType: KeyType,
) = when (keyType) {
    KeyType.secp256r1 -> DefaultSignatureAlgorithmIdentifierFinder()
        .find("SHA256withECDSA")

    KeyType.Ed25519 -> DefaultSignatureAlgorithmIdentifierFinder()
        .find("ED25519")

    else -> throw IllegalArgumentException("Unsupported key type $keyType for ISO certificate signing operation")
}

internal actual fun isValidIsoCountryCode(countryCode: String): Boolean {
    return Locale.getISOCountries().find { it == countryCode }?.let {
        true
    } ?: false
}