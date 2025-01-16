package id.walt.webwallet.service

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class JwsDecoder {
    private val json = Json { ignoreUnknownKeys = true }

    fun payload(data: String) = runCatching {
        data.decodeJws().payload
    }.fold(onSuccess = { it }, onFailure = { json.decodeFromString(data) }).let {
        JsonUtils.tryGetData(it, "vc")?.jsonObject ?: it
    }
}
