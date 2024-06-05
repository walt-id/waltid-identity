package id.walt.credentials.utils

import id.walt.credentials.vc.vcs.W3CVC
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object W3CVcUtils {

    fun W3CVC.overwrite(map: Map<String, JsonElement>): W3CVC = W3CVC(
        this.toJsonObject().toMutableMap().apply {
            map.forEach { (k, v) ->
                this[k] = v
            }
        })

    fun W3CVC.update(key: String, map: Map<String, JsonElement>): W3CVC = W3CVC(
        this.toMutableMap().apply {
            this[key] = JsonObject(this[key]!!.jsonObject.toMutableMap().apply {
                map.forEach { (k, v) ->
                    this[k] = v
                }
            })
        })

}
