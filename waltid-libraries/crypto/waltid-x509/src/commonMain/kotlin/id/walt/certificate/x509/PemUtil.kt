package id.walt.certificate.x509

import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64

object PemUtil {

    val CERTIFICATE_CHAIN_PEM_REGEX = "-----\\s*BEGIN\\s+([^-]+)-----([^-]+)-----\\s*END\\s+([^-]+)-----".toRegex()

    fun spitPemChain(pemChainString: String): Collection<String> =
        CERTIFICATE_CHAIN_PEM_REGEX.findAll(pemChainString)
            .map { normalizePem(it.value) } // Clean up trailing line breaks
            .toList()

    fun normalizePem(pem: String): String {
        val match = requireNotNull(CERTIFICATE_CHAIN_PEM_REGEX.find(pem)) {
            "Invalid PEM: '$pem'"
        }
        val beginType = match.groupValues[1].trim()
        val base64Payload = match.groupValues[2].replace("\\s".toRegex(), "")
        val endType = match.groupValues[3].trim()
        require(beginType == endType) { "Begin type '$beginType' does not match end type '$endType' in PEM: '$pem'" }
        return base64ToPem(base64Payload, beginType)
    }

    fun byteStringToBase64Pem(bytes: ByteString, type: String): String =
        byteArrayToBase64Pem(bytes.toByteArray(), type)

    fun byteArrayToBase64Pem(bytes: ByteArray, type: String): String =
        base64ToPem(Base64.Default.encode(bytes), type)

    fun base64ToPem(rawBase64: String, type: String): String {
        val body = rawBase64.chunked(64).joinToString(separator = "\n")
        return "-----BEGIN $type-----\n$body\n-----END $type-----"
    }

}