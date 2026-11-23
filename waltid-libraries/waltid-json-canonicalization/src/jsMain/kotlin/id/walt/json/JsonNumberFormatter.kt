package id.walt.json

internal actual fun jsonNumberFormatter(): (Double) -> String = { it.asDynamic().toString() as String }
