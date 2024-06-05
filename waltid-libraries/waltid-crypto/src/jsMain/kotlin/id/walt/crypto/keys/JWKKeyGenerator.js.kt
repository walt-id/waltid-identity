package id.walt.crypto.keys

import JWK
import KeyLike
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKeyCreator
import id.walt.crypto.utils.JwsUtils.jwsAlg
import id.walt.crypto.utils.PromiseUtils.await
import jose
import love.forte.plugin.suspendtrans.annotation.JsPromise
import kotlin.js.json

@OptIn(ExperimentalJsExport::class)
@JsExport
object JsJWKKeyCreator : JWKKeyCreator {

    @JsPromise
    @JsExport.Ignore
    override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey {
        val alg = type.jwsAlg()

        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val key = await(jose.generateKeyPair<KeyLike>(alg, json("extractable" to true) as jose.GenerateKeyPairOptions)).privateKey

        return JWKKey(key).apply { init() }
    }

    @JsPromise
    @JsExport.Ignore
    override suspend fun importRawPublicKey(type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?): Key {
        val key: KeyLike = await(jose.importSPKI(rawPublicKey.decodeToString(), type.jwsAlg()))
        return JWKKey(key).apply { init() }
    }

    @JsPromise
    @JsExport.Ignore
    override suspend fun importJWK(jwk: String): Result<JWKKey> =
        runCatching {
            var jsonJWK = JSON.parse<JWK>(jwk)
            while (jsonJWK::class == String::class) {
                jsonJWK = JSON.parse(jsonJWK as String)
            }
            JWKKey(await(jose.importJWK(jsonJWK)), jsonJWK).apply { init() }
        }


    /**
     * Only supported on Node right now (algorithms are not passed to WebCrypto)
     */
    @JsPromise
    @JsExport.Ignore
    override suspend fun importPEM(pem: String): Result<JWKKey> =
        runCatching {
            val lines = pem.lines()
            fun String.getPemTitle() = this.trim().dropWhile { it == '-' }.dropLastWhile { it == '-' }.trim()
            fun String.isPemTitle(prefix: String, suffix: String) = this.startsWith(prefix) && this.endsWith(suffix)

            val hasPrivateKey = lines.any { it.getPemTitle().isPemTitle("BEGIN", "PRIVATE KEY") }
            val hasPublicKey = lines.any { it.getPemTitle().isPemTitle("BEGIN", "PUBLIC KEY") }

            val importedPemKey: KeyLike = await(
                when {
                    hasPrivateKey -> jose.importPKCS8(lines.dropWhile { !it.getPemTitle().isPemTitle("BEGIN", "PRIVATE KEY") }
                        .dropLastWhile { !it.getPemTitle().isPemTitle("END", "PRIVATE KEY") }.joinToString("\n"), "")

                    hasPublicKey -> jose.importSPKI(lines.dropWhile { !it.getPemTitle().isPemTitle("BEGIN", "PUBLIC KEY") }
                        .dropLastWhile { !it.getPemTitle().isPemTitle("END", "PUBLIC KEY") }.joinToString("\n"), "")

                    else -> throw IllegalArgumentException(
                        "Unable to determine if public or private PEM-encoded key. " +
                                "Make sure the title line includes 'BEGIN PUBLIC KEY' or 'BEGIN PRIVATE KEY'."
                    )
                }
            )

            JWKKey(importedPemKey).apply { init() }
        }
}
