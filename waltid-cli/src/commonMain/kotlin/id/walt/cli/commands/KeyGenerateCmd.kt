package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText
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
        echo(TextStyles.dim("Generating key of type ${keyType.name}..."))
        runBlocking {
            val key = LocalKey.generate(keyType)

            echo(TextStyles.dim("Key thumbprint is: ${key.getThumbprint()}"))

            val jwk = key.exportJWKPretty()

            echo(TextColors.green("Generated Key (JWK):"))
            terminal.println(Markdown("""
                |```json
                |$jwk
                |```
            """.trimMargin()))

            val outputFile = optOutputFilePath ?: Path("${key.getKeyId()}.json")

            if (outputFile.exists()
                && YesNoPrompt(
                    "The file \"${outputFile.absolutePathString()}\" already exists, do you want to overwrite it?",
                    terminal
                ).ask() == false
            ) {
                echo("Will not overwrite output file.")
                return@runBlocking
            }

            outputFile.writeText(jwk)
            echo("${TextColors.brightGreen("Done.")} Key saved at file \"${outputFile.absolutePathString()}\".")
        }
    }
}

