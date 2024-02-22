package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
class KeyGenerateCmd : CliktCommand(
    name = "generate",
    help = "Generates a new cryptographic key.",
    // printHelpOnEmptyArgs = true
) {

    private val acceptedKeyTypes = KeyType.entries.joinToString(" | ")

    private val keyType by option("-t", "--keyType")
        .enum<KeyType>()
        .help("Key type to use. Possible values are: [${acceptedKeyTypes}}]. Default value is " + KeyType.Ed25519.name)
        .default(KeyType.Ed25519)

    private val optOutputFilePath by option("-o", "--output")
        .path()
        .help("File path to save the generated key. Default value is <keyId>.json")

    override fun run() {
        echo("Generating key of type " + keyType.name)
        runBlocking {
            val key = LocalKey.generate(keyType)

            val jwk = key.exportJWK()

            val outputFilePath = optOutputFilePath ?: Path("./${key.getKeyId()}.json")
            val outputFile = outputFilePath.toFile()

            echo("--- Generated Key (JWK) ---")
            echo(jwk)
            echo("---------------------------")

            outputFile.writeText(jwk)
            echo("Key saved at file: ${outputFile.absolutePath}")
        }
    }
}

