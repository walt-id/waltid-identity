package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.PrettyPrinter
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class KeyGenerateCmd : CliktCommand(
    name = "generate",
    help = "Generates a new cryptographic key.",
    // printHelpOnEmptyArgs = true
) {

    val print: PrettyPrinter = PrettyPrinter(this)

    private val acceptedKeyTypes = KeyType.entries.joinToString(" | ")

    private val keyType by option("-t", "--keyType")
        .enum<KeyType>()
        .help("Key type to use. Possible values are: [${acceptedKeyTypes}]. Default value is " + KeyType.Ed25519.name)
        .default(KeyType.Ed25519)

    private val optOutputFilePath by option("-o", "--output")
        .path()
        .help("File path to save the generated key. Default value is <keyId>.json")

    private val commonOptions by CommonOptions()

    override fun run() {
        print.dim("Generating key of type ${keyType.name}...")
        runBlocking {
            val key = JWKKey.generate(keyType)

            print.dim("Key thumbprint is: ${key.getThumbprint()}")

            val jwk = key.exportJWKPretty()

            print.green("Generated Key (JWK):")
            print.box(jwk)

            val outputFile = optOutputFilePath ?: Path("${key.getKeyId()}.json")

            if (outputFile.exists()
                && YesNoPrompt(
                    "The file \"${outputFile.absolutePathString()}\" already exists, do you want to overwrite it?",
                    terminal
                ).ask() == false
            ) {
                print.plain("Will not overwrite output file.")
                return@runBlocking
            }

            outputFile.writeText(jwk)
            print.greenb("Done. ", linebreak = false)
            print.plain("Key saved at file \"${outputFile.absolutePathString()}\".")
        }
    }
}

