package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.FileNotFound
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
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


enum class KeyFileFormat {
    JWK, PEM, ENCRYPTED_PEM
}

class KeyConvertCmd : CliktCommand(
    name = "convert",
    help = "Convert key files between PEM and JWK formats.",
    printHelpOnEmptyArgs = true
) {

    val input by option("-i", "--input")
        .help("The input file path. Accepted formats are: JWK and PEM")
        .file()
        .required()

    val output by option("-o", "--output")
        .help("The output file path. Accepted formats are: JWK and PEM. If not provided the input filename will be used with a different extension.")
        .file()
    // .required()

    val passphrase by option("-p", "--passphrase")
        .help("Passphrase to open an encrypted PEM")
    // .prompt(text = "Please, inform the PEM passphrase", hideInput = true)

    override fun run() {

        checkIfInputFileExists(input)

        // Identify input key type
        val inputKeyType = getKeyType(input)
        // Decide which is the output file format
        val outputKeyType = getOutputKeyType(inputKeyType)

        // Read the source key from the input file
        val key = runBlocking { getKey(input) }

        // Set output file
        // e.g. if --input=myKey.jwt and --output is not provided, the output file will be myKey.pem
        val outputFile =
            output ?: File(input.parent, "${input.nameWithoutExtension}.${outputKeyType.toString().lowercase()}")

        val outputContent = getOutputContent(key, outputKeyType)

        outputFile.writeText(outputContent)

        echo("Done. $input file converted to $outputFile")
    }

    private fun checkIfInputFileExists(input: File): Unit { // Result<Boolean> {
        if (!input.exists()) {
            throw FileNotFound(input.path)
        }
    }

    private fun getKeyType(input: File): KeyFileFormat {

        operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)

        val firstLine = input.readLines()[0]
        return when (firstLine) {
            // For more PEM's content type, see:
            // https://github.com/openssl/openssl/blob/master/include/openssl/pem.h
            in Regex("""^\{.*""") -> KeyFileFormat.JWK
            in Regex("""^-+BEGIN PUBLIC KEY-+""") -> KeyFileFormat.PEM
            in Regex("""^-+BEGIN PRIVATE KEY-+""") -> KeyFileFormat.PEM
            in Regex("""^-+BEGIN ENCRYPTED PRIVATE KEY-+""") -> KeyFileFormat.ENCRYPTED_PEM
            else -> throw InvalidFileFormat(input.path, "Unknown file format")
        }
    }

    private suspend fun getKey(input: File): LocalKey {

        val errorMsg = "It's neither JWK nor a PEM."

        val inputContent = input.readText()
        val keyType = getKeyType(input)
        try {
            return when (keyType) {
                KeyFileFormat.JWK -> LocalKey.importJWK(inputContent).getOrThrow()
                KeyFileFormat.PEM -> LocalKey.importPEM(inputContent).getOrThrow()
                KeyFileFormat.ENCRYPTED_PEM -> LocalKey.importPEM(decrypt(input).getOrThrow()).getOrThrow()
                else -> throw InvalidFileFormat(input.path, errorMsg)
            }
        } catch (e: Exception) {
            throw InvalidFileFormat(input.path, errorMsg)
        }
    }

    private fun getOutputKeyType(inputKeyType: KeyFileFormat): KeyFileFormat {
        // If the provided file is of type JWK, convert it to PEM.
        // If PEM, convert it to JWK.
        return when (inputKeyType) {
            KeyFileFormat.JWK -> KeyFileFormat.PEM
            KeyFileFormat.PEM -> KeyFileFormat.JWK
            KeyFileFormat.ENCRYPTED_PEM -> KeyFileFormat.JWK
        }
    }

    private fun getOutputContent(inputKey: Key, outputKeyType: KeyFileFormat): String {
        return when (outputKeyType) {
            KeyFileFormat.JWK -> runBlocking { inputKey.exportJWK() }
            KeyFileFormat.PEM -> runBlocking { inputKey.exportPEM() }
            KeyFileFormat.ENCRYPTED_PEM -> runBlocking { inputKey.exportPEM() }
        }
    }

    private fun decrypt(input: File): Result<String> {

        lateinit var k: PrivateKey

        try {
            val decipherKey: String


            if (passphrase == null) {
                decipherKey = terminal.prompt("Key encrypted. Please, inform the passphrase to decipher it")!!
                if (decipherKey == null) { // TODO: Can happen?
                    return Result.failure(IllegalArgumentException("Passphrase is required for encrypted PEM file."))
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
        Security.addProvider(BouncyCastleProvider())

        try {
            val pemParser = PEMParser(FileReader(input))
            val o = pemParser.readObject()
            val pki: PrivateKeyInfo

            if (o is PKCS8EncryptedPrivateKeyInfo) {
                val builder = JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC")
                val idp = builder.build(passphrase.toCharArray())

                pki = o.decryptPrivateKeyInfo(idp)
            } else if (o is PEMEncryptedKeyPair) {
                val pkp = o.decryptKeyPair(BcPEMDecryptorProvider(passphrase.toCharArray()))

                pki = pkp.privateKeyInfo
            } else {
                throw PKCSException("Invalid encrypted private key class: " + o.javaClass.name)
            }

            val converter = JcaPEMKeyConverter().setProvider("BC")

            val encodedKey = Base64.getEncoder().encodeToString(converter.getPrivateKey(pki).encoded)
            val pem = """
                -----BEGIN PRIVATE KEY-----
                $encodedKey
                -----END PRIVATE KEY-----
            """.trimIndent()

            return pem
        } catch (e: Exception) {
            throw Exception("Failed to load key from $input: $e")
        }
    }
}