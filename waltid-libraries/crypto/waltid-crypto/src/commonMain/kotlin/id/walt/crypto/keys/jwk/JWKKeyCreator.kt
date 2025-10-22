package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
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
        private val log = KotlinLogging.logger {  }
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
