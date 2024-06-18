package id.walt.webwallet.seeker

import kotlinx.serialization.json.JsonObject


interface Seeker<T> {
    fun get(data: JsonObject): T
}