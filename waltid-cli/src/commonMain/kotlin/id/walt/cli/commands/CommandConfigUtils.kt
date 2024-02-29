package id.walt.cli.commands

import kotlin.reflect.KClass

object CommandConfigUtils {
    operator fun Map<String, String>.get(key: KClass<*>): String = this[key.simpleName]!!
    operator fun MutableMap<String, String>.set(key: KClass<*>, value: String) {
        this[key.simpleName!!] = value
    }
}
