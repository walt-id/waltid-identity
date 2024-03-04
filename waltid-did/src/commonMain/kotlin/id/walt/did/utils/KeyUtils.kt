package id.walt.did.utils

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.crypto.utils.MultiCodecUtils
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
object KeyUtils {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun fromPublicKeyMultiBase(identifier: String): Result<Key> {
        val publicKeyRaw = MultiBaseUtils.convertMultiBase58BtcToRawKey(identifier)
        //TODO: externalize import call
        return when (val code = MultiCodecUtils.getMultiCodecKeyCode(identifier)) {
            MultiCodecUtils.JwkJcsPubMultiCodecKeyCode -> LocalKey.importJWK(publicKeyRaw.decodeToString())
            else -> Result.success(
                LocalKey.importRawPublicKey(
                    MultiCodecUtils.getKeyTypeFromKeyCode(code), publicKeyRaw, LocalKeyMetadata()
                )
            )
        }
    }

    fun getKeyTypeForVerificationMaterialType(type: String) = KeyType.entries.firstOrNull {
        val regex = Regex("(${it.name.lowercase()})(.*)")
        regex.containsMatchIn(type.lowercase())
    } ?: throw IllegalArgumentException("Verification material type not supported: $type")
}