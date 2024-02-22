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

class KeyGenerateCmd : CliktCommand(
    name = "generate",
    help = "Generates a new cryptographic key.",
    // printHelpOnEmptyArgs = true
) {

    // enum class KeyType {
    //     ED25519, SECP256K1, SECP256R1, RSA
    // }

    private val acceptedKeyTypes = KeyType.entries.joinToString(" | ")

    val keyType by option("-t", "--keyType")
        .enum<KeyType>(ignoreCase = true)
        .help("Key type to use. Possible values are: [${acceptedKeyTypes}}]. Default value is " + KeyType.Ed25519.name)
        //.choice(KeyType.Ed25519.name, KeyType.RSA.name, KeyType.secp256r1.name, KeyType.secp256k1.name) // Case sensitive. Not good.
        // .prompt("Please, inform the type of the key you want to generate. Check --help for available options.")
        .default(KeyType.Ed25519)

    val optOutputFilePath by option("-o", "--output")
        .path()
        .help("File path to save the generated key. Default value is <keyId>.json")

    override fun run() {
        echo("Generating key of type " + keyType.name)
        runBlocking {

            var key: LocalKey? = null

            key = LocalKey.generate(keyType)

            val jwk = key.exportJWK()

            val outputFilePath = optOutputFilePath ?: Path("./${key.getKeyId()}.json")
            val outputFile = outputFilePath.toFile()

            echo("--- Generated Key (JWK) ---")
            echo(jwk)
            echo("---------------------------")

            outputFile.writeText(jwk)
            echo("Key saved at file ${outputFile}")
        }
    }
}

