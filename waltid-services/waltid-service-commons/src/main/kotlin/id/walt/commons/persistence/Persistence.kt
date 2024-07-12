package id.walt.commons.persistence

import kotlin.time.Duration


abstract class Persistence<V>(
    val discriminator: String,
    val defaultExpiration: Duration,
) {

    abstract operator fun get(id: String): V
    abstract operator fun set(id: String, value: V)
    fun put(id: String, value: V) = set(id, value)
    abstract fun remove(id: String)
    fun mutate(id: String, mutation: (V) -> V) {
        set(id, mutation.invoke(get(id)))
    }

}


