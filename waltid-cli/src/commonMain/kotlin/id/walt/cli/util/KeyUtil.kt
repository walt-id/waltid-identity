package id.walt.cli.util

import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.mordant.rendering.TextStyles
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.ParseException

class KeyUtil {
    suspend fun getKey(keyFile: File): LocalKey {
        if (keyFile != null) {
            try {
                return LocalKey.importJWK(keyFile.readText()).getOrThrow()
            } catch (e: ParseException) {
                throw InvalidFileFormat(keyFile.name, e.let { e.message!! })
            }
        } else {
            return generateDefaultKey()
        }
    }

    private suspend fun generateDefaultKey(): LocalKey {
        println(TextStyles.dim("Key not provided. Let's generate a new one..."))
        val key = runBlocking { LocalKey.generate(KeyType.Ed25519) }
        println(TextStyles.dim("Key generated with thumbprint ${key.getThumbprint()}"))

        return key
    }
}