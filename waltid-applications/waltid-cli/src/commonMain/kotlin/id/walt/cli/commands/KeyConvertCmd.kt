package id.walt.cli.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.YesNoPrompt
import com.github.ajalt.mordant.terminal.prompt
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.cli.util.getNormalizedPath
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.bc.BcPEMDecryptorProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.PKCSException
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder
import java.io.File
import java.io.FileReader
import java.security.PrivateKey
import java.security.Security
import java.util.*
import kotlin.io.path.useLines


enum class KeyFileFormat {
    JWK, PEM, ENCRYPTED_PEM;

    companion object {
        fun getNames() = KeyFileFormat.entries.joinToString { it.name }
    }
}

class KeyConvertCmd : CliktCommand(
    name = "convert"
) {

    override fun help(context: Context) = """Convert key files between PEM and JWK formats.
        
        Example usage:
        ---------------
        waltid key convert -i myRsaKey.pem
        waltid key convert -i myEncryptedRsaKey.pem -p 123123 -o myRsaKey.jwk
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
    }

    override val printHelpOnEmptyArgs = true


    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val input by option("-i", "--input").help("The input file path. Accepted formats are: JWK and PEM")
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true).required()


    private val sourceKeyType by lazy { getKeyType(input) }
    private val targetKeyType by lazy { getOutputKeyType(sourceKeyType) }

    private val output by option(
        "-o",
        "--output"
    ).help("The output file path. Accepted formats are: JWK and PEM. If not provided, the input filename will be used with a different extension.")
        .file().defaultLazy {
            File(input.parent, "${input.nameWithoutExtension}.${targetKeyType.toString().lowercase()}")
        }

    private val passphrase by option("-p", "--passphrase").help("Passphrase to open an encrypted PEM.")
    // .prompt(text = "Please, inform the PEM passphrase", hideInput = true)

    private val commonOptions by CommonOptions()

    override fun run() {
        // Read the source key from the input file
        print.dim("Reading key \"${input.absolutePath}\"...")
        val inputKey = runBlocking { getKey(input) }

        print.dim("Converting key \"${input.absolutePath}\"...")
        val outputContent = runBlocking { convertKey(inputKey, targetKeyType) }

        print.green("Converted Key (${targetKeyType}):")
        print.box(outputContent)

        if (output.exists()
            && YesNoPrompt(
                "The file \"${getNormalizedPath(output.absolutePath)}\" already exists, do you want to overwrite it?",
                terminal
            ).ask() == false
        ) {
            print.plain("Will not overwrite output file.")
            return
        }

        output.writeText(outputContent)

        print.greenb("Done. ", false)
        print.plain("Converted \"${getNormalizedPath(input.absolutePath)}\" to \"${getNormalizedPath(output.absolutePath)}\".")
    }

    private operator fun Regex.contains(text: CharSequence?): Boolean = this.matches(text ?: "")
    private fun getKeyType(input: File): KeyFileFormat = input.toPath().useLines { lines ->
        when (lines.firstOrNull()) {
            null -> throw InvalidFileFormat(input.path, "No lines in file.")

            // other PEM content types: https://github.com/openssl/openssl/blob/master/include/openssl/pem.h
            in Regex("""^\{.*""") -> KeyFileFormat.JWK
            in Regex("""^-+BEGIN .*PUBLIC KEY-+""") -> KeyFileFormat.PEM
            in Regex("""^-+BEGIN .*OPENSSH PRIVATE KEY-+""") -> KeyFileFormat.PEM
            in Regex("""^-+BEGIN .*EC PARAMETERS-+""") -> KeyFileFormat.PEM
            in Regex("""^-+BEGIN .*ENCRYPTED PRIVATE KEY-+""") -> KeyFileFormat.ENCRYPTED_PEM
            in Regex("""^-+BEGIN .*PRIVATE KEY-+""") -> KeyFileFormat.PEM
            else -> throw InvalidFileFormat(input.path, "Unknown file format (expected ${KeyFileFormat.getNames()}).")
        }
    }

    private suspend fun getKey(input: File, keyType: KeyFileFormat = getKeyType(input)): JWKKey {
        val inputContent = input.readText()
        try {
            return run {
                when (keyType) {
                    KeyFileFormat.JWK -> JWKKey.importJWK(inputContent).getOrThrow()
                    KeyFileFormat.PEM -> JWKKey.importPEM(inputContent).getOrThrow()
                    KeyFileFormat.ENCRYPTED_PEM -> JWKKey.importPEM(decrypt(input).getOrThrow()).getOrThrow()
                }
            }
        } catch (e: Throwable) {
            val mainMsg = "Invalid file format."
            var complementaryMsg = "Use the --verbose flag to get more details."

            if (this.commonOptions.verbose) {
                complementaryMsg = e.localizedMessage ?: "No more details to show."
            }
            throw InvalidFileFormat(input.absolutePath, "$mainMsg $complementaryMsg")
        }

    }

    /**
     * If the provided file is of type JWK, convert it to PEM.
     * If PEM, convert it to JWK.
     */
    private fun getOutputKeyType(inputKeyType: KeyFileFormat): KeyFileFormat =
        if (inputKeyType == KeyFileFormat.JWK) KeyFileFormat.PEM else KeyFileFormat.JWK


    /**
    Convert provided source key to specified target key type
     */
    private suspend fun convertKey(inputKey: Key, targetKeyType: KeyFileFormat): String {
        try {
            return when (targetKeyType) {
                KeyFileFormat.JWK -> inputKey.exportJWK()
                KeyFileFormat.PEM -> inputKey.exportPEM()
                KeyFileFormat.ENCRYPTED_PEM -> inputKey.exportPEM()
            }
        } catch (e: Throwable) {
            val mainMsg = "Oops. Something went wrong when converting the key."
            var complementaryMsg = "Use the --verbose flag to get more details."

            if (this.commonOptions.verbose) {
                complementaryMsg = e.localizedMessage ?: "No more details to show."
            }

            throw UsageError("$mainMsg $complementaryMsg")
        }
    }

    private fun decrypt(input: File): Result<String> {

        lateinit var k: PrivateKey

        try {
            val decipherKey: String?


            if (passphrase == null) {
                decipherKey = terminal.prompt("Key encrypted. Please, inform the passphrase to decipher it")
                if (decipherKey == null) { // TODO: Can happen?
                    return Result.failure(BadParameterValue(passphrase!!))
                }
            } else {
                decipherKey = passphrase as String
            }
            val decryptedPEM = decryptKey(decipherKey) // as BCRSAPrivateCrtKey
            return Result.success(decryptedPEM)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // TODO: Extract
    fun decryptKey(passphrase: String): String {
        Security.addProvider(BouncyCastleProvider()) // TODO: do not add at every function call

        try {
            val pemParser = PEMParser(FileReader(input))
            val pemObject = runCatching {
                pemParser.readObject() ?: error("No object in PEM")
            }.getOrElse { throw IllegalArgumentException("Could not parse object from PEM", it) }
            val pki = when (pemObject) {
                is PKCS8EncryptedPrivateKeyInfo -> {
                    val decryptorProviderBuilder = JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC")
                    val inputDecryptorProvider = decryptorProviderBuilder.build(passphrase.toCharArray())

                    pemObject.decryptPrivateKeyInfo(inputDecryptorProvider)
                }

                is PEMEncryptedKeyPair -> {
                    val pkp = pemObject.decryptKeyPair(BcPEMDecryptorProvider(passphrase.toCharArray()))

                    pkp.privateKeyInfo
                }

                else -> throw PKCSException("Invalid encrypted private key class: " + pemObject::class.qualifiedName)
            }

            val converter = JcaPEMKeyConverter().setProvider("BC")

            val encodedKey = Base64.getEncoder().encodeToString(converter.getPrivateKey(pki).encoded)

            return """
                -----BEGIN PRIVATE KEY-----
                $encodedKey
                -----END PRIVATE KEY-----
            """.trimIndent()
        } catch (e: Exception) {
            throw Exception("Failed to load key from $input: $e")
        }
    }
}
