package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec
import kotlinx.coroutines.runBlocking
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class KeyGenerateCmd : CliktCommand(
    name = "generate"
) {

    override fun help(context: Context) = """Generates a new cryptographic key.
        
        Example usage:
        ---------------
        waltid key generate
        waltid key generate -t secp256k1
        waltid key generate -t P-384
        waltid key generate -t RSA --rsa-bits 3072
        waltid key generate -t RSA -o myRsaKey.json

        Private key material is written only with --output and displayed only with --show-private.
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

    private val acceptedKeyTypes = CliKeyAlgorithm.entries.joinToString(" | ") { it.optionName }

    private val keyType by option("-t", "--keyType")
        .convert { CliKeyAlgorithm.parse(it) }
        .help("Key type to use. Possible values are: [${acceptedKeyTypes}]. Default value is ${TextColors.brightBlue(CliKeyAlgorithm.P256.optionName)}.")
        .default(CliKeyAlgorithm.P256)

    private val rsaBits by option("--rsa-bits")
        .convert { value -> value.toIntOrNull()?.takeIf { it in setOf(2048, 3072, 4096) } ?: fail("must be 2048, 3072, or 4096") }
        .default(2048)
        .help("RSA modulus size. Accepted values: 2048, 3072, 4096. Default: 2048.")

    private val optOutputFilePath by option("-o", "--output")
        .path()
        .help("File path to save the private JWK. Without this option, no private key file is written.")

    private val showPrivate by option("--show-private")
        .flag(default = false)
        .help("Display private JWK material on stdout. WARNING: this exposes the private key.")

    private val commonOptions by CommonOptions()

    override fun run() {
        print.dim("Generating key of type ${keyType.optionName}...")
        runBlocking {
            val key = KeyUtil.generate(keyType.spec(rsaBits))
            val thumbprint = KeyUtil.thumbprint(key)

            print.dim("Key thumbprint is: $thumbprint")

            val publicJwk = KeyUtil.exportJwk(key, private = false)

            print.green("Generated public key metadata (JWK):")
            print.box(publicJwk)

            if (showPrivate) {
                print.red("WARNING: displaying private key material.")
                print.box(KeyUtil.exportJwk(key, private = true))
            }

            val outputFile = optOutputFilePath ?: return@runBlocking print.plain(
                "Private key not written. Use --output to save it or --show-private to display it."
            )
            if (outputFile.exists()
                && YesNoPrompt(
                    "The file \"${outputFile.absolutePathString()}\" already exists, do you want to overwrite it?",
                    terminal
                ).ask() == false
            ) {
                print.plain("Will not overwrite output file.")
                return@runBlocking
            }

            outputFile.writeText(KeyUtil.exportJwk(key, private = true))
            print.greenb("Done. ", linebreak = false)
            print.plain("Key saved at file \"${outputFile.absolutePathString()}\".")
        }
    }
}

enum class CliKeyAlgorithm(val optionName: String) {
    P256("P-256"),
    P384("P-384"),
    P521("P-521"),
    ED25519("Ed25519"),
    SECP256K1("secp256k1"),
    RSA("RSA");

    fun spec(rsaBits: Int = 2048): KeySpec = when (this) {
        P256 -> KeySpec.Ec(EcCurve.P256)
        P384 -> KeySpec.Ec(EcCurve.P384)
        P521 -> KeySpec.Ec(EcCurve.P521)
        ED25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
        SECP256K1 -> KeySpec.Ec(EcCurve.SECP256K1)
        RSA -> KeySpec.Rsa(rsaBits)
    }

    companion object {
        fun parse(value: String): CliKeyAlgorithm = when (value.lowercase()) {
            "p-256", "p256", "secp256r1" -> P256
            "p-384", "p384", "secp384r1" -> P384
            "p-521", "p521", "secp521r1" -> P521
            "ed25519" -> ED25519
            "secp256k1" -> SECP256K1
            "rsa" -> RSA
            else -> throw IllegalArgumentException("invalid choice: $value (choose from ${entries.joinToString { it.optionName }})")
        }
    }
}
