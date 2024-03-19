package id.walt.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.mordant.rendering.TextStyles
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.ParseException

class KeyUtil(private val cmd: CliktCommand? = null) {

    suspend fun getKey(keyFile: File?): LocalKey {
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
        val msg1 = TextStyles.dim("Key not provided. Let's generate a new one...")
        printit(msg1)
        val key = runBlocking { LocalKey.generate(KeyType.Ed25519) }
        val msg2 = TextStyles.dim("Key generated with thumbprint ${key.getThumbprint()}")
        printit(msg2)

        return key
    }

    private fun printit(msg: String) {
        if (cmd != null) cmd.echo(msg)
        else println(msg)
    }
}