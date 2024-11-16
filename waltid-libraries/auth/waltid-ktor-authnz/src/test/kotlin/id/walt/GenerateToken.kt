package id.walt

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** read from stdin until an blank line is encountered */
fun readUntilEmptyLine(): String =
    generateSequence { readln() }
        .takeWhile { it.isNotBlank() }
        .joinToString("\n")

suspend fun main() {
    println("Enter key (end with empty line to continue):")
    val keyStr = readUntilEmptyLine().trim()

    // check if it's walt.id serialized key, JWK key, or PEM key, and import accordingly
    val key = when {
        keyStr.startsWith("{") -> {
            val keyJson = Json.parseToJsonElement(keyStr).jsonObject
            when {
                keyJson.containsKey("type") -> KeyManager.resolveSerializedKey(keyStr)
                else -> JWKKey.importJWK(keyStr).getOrThrow()
            }
        }

        keyStr.startsWith("-") -> JWKKey.importPEM(keyStr).getOrThrow()
        else -> error("Unknown key format! Enter waltid-serialized key, JWK or PEM.")
    }

    println("Imported key: $key (${key.getThumbprint()})")
    println("Serialized: " + KeySerialization.serializeKey(key))

    println("Enter claims:")
    val claims = HashMap<String, String>()

    // add to claim map until empty line is entered
    while (true) {
        print("Enter claim (or empty line to continue) (format: key=value): ")
        val line = readln()
        if (line.isBlank()) break

        val name = line.substringBefore("=")
        val value = line.substringAfter("=")

        claims[name] = value
        println("Current claims: ${claims.entries.joinToString { "${it.key}=${it.value}" }}")
    }

    // convert map to payload string
    val payload = claims.toJsonObject().toString()
    println("Payload: $payload")

    // sign payload
    val signed = key.signJws(payload.toByteArray())
    println("Signed: $signed")
}
