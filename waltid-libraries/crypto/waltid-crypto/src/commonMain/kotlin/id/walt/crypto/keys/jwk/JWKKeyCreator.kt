package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class JWKKeyCreator {
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

    fun convertDerCertificateToPemCertificate(der: ByteArray): String =
"""-----BEGIN CERTIFICATE-----
${Base64.Pem.encode(der)}
-----END CERTIFICATE-----"""

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun importFromDerCertificate(der: ByteArray): Result<JWKKey> =
        importPEM(convertDerCertificateToPemCertificate(der))

}
