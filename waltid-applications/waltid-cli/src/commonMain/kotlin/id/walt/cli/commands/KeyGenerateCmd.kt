package id.walt.cli.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.io.File
import id.walt.cli.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking

class KeyGenerateCmd : CliktCommand(
    name = "generate"
) {

    override fun help(context: Context) = """Generates a new cryptographic key.
        
        Example usage:
        ---------------
        waltid key generate
        waltid key generate -t secp256k1
        waltid key generate -t RSA
        waltid key generate -t RSA -o myRsaKey.json
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
    }

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val acceptedKeyTypes = KeyType.entries.joinToString(" | ")

    private val keyType by option("-t", "--keyType")
        .enum<KeyType>()
        .help("Key type to use. Possible values are: [${acceptedKeyTypes}]. Default value is ${TextColors.brightBlue(KeyType.secp256r1.name)}.")
        .default(KeyType.secp256r1)

    private val optOutputFilePath by option("-o", "--output")
        .file()
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

            val outputFile = optOutputFilePath ?: File("${key.getKeyId()}.json")

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

