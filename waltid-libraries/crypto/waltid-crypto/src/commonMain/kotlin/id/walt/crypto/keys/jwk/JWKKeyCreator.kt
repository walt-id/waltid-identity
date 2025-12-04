package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.matchesBase64
import io.github.oshai.kotlinlogging.KotlinLogging
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class JWKKeyCreator {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun generate(type: KeyType, metadata: JwkKeyMeta? = null): JWKKey

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun importRawPublicKey(
        type: KeyType,
        rawPublicKey: ByteArray,
        metadata: JwkKeyMeta? = null
    ): Key

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun importJWK(jwk: String): Result<JWKKey>

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun importPEM(pem: String): Result<JWKKey>

    fun wrapAsPem(data: ByteArray) =
        """-----BEGIN CERTIFICATE-----
${Base64.Pem.encode(data)}
-----END CERTIFICATE-----"""

    fun convertDerCertificateToPemCertificate(der: ByteArray): String =
        wrapAsPem(der)

    fun convertX5cToPemCertificate(x5cElement: String): String =
        wrapAsPem(Base64.decode(x5cElement))

    fun convertDERorPEMtoByteArray(derOrPem: String): ByteArray {
        val str = derOrPem.lines().mapNotNull { it.takeIf { it.isNotBlank() }?.trim() }.joinToString("\n").trim()
        val der = if (str.lines()[0].contains("BEGIN CERTIFICATE")) {
            str.replace("BEGIN CERTIFICATE", "")
                .replace("END CERTIFICATE", "")
                .replace("\r", "")
                .replace("\n", "")
                .dropWhile { it == '-' }
                .dropLastWhile { it == '-' }
        } else str

        val matchesBase64 = der.matchesBase64()

        return when {
            matchesBase64 -> Base64.decode(der)
            else -> throw IllegalArgumentException("Invalid format (expected PEM, or DER in Base64): $derOrPem")
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importDERorPEM(derOrPem: String): Result<JWKKey> {
        val pem = if (!derOrPem.startsWith("-----BEGIN")) {
            val pem = convertX5cToPemCertificate(derOrPem)
            log.trace { "Converted x5c to PEM: $pem" }
            pem
        } else derOrPem
        return JWKKey.importPEM(pem)
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importFromDerCertificate(der: ByteArray): Result<JWKKey> =
        importPEM(convertDerCertificateToPemCertificate(der))

}
