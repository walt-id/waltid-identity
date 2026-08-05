package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.cli.util.getNormalizedPath
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.keys.decodePublicKeyPem
import id.walt.crypto2.keys.encodePem
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.pkcs.RSAPrivateKey
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X962Parameters
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.io.File

enum class KeyFileFormat {
    JWK,
    SPKI_PEM,
    PKCS8_PEM,
}

enum class KeyOutputFormat {
    JWK,
    SPKI,
    PKCS8,
}

class KeyConvertCmd : CliktCommand(name = "convert") {
    override fun help(context: Context) = """Convert keys between JWK, SPKI PEM, and PKCS8 PEM.

        Example usage:
        ---------------
        waltid key convert -i myPrivateKey.jwk -o myPrivateKey.pem
        waltid key convert -i myPrivateKey.jwk --output-format SPKI -o myPublicKey.pem
        waltid key convert -i myPublicKey.pem

        Private JWK and PKCS8 output requires --output or --show-private.
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
        context { localization = WaltIdCmdHelpOptionMessage }
    }

    override val printHelpOnEmptyArgs = true

    val print = PrettyPrinter(this)

    private val input by option("-i", "--input")
        .help("Input file. Accepted formats: JWK, strict PUBLIC KEY PEM (SPKI), and strict PRIVATE KEY PEM (PKCS8).")
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)
        .required()

    private val output by option("-o", "--output")
        .help("Output file. The default uses the input name with .jwk or .pem.")
        .file()

    private val outputFormat by option("-f", "--output-format")
        .convert { value ->
            KeyOutputFormat.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: fail("must be JWK, SPKI, or PKCS8")
        }
        .help("Explicit output format: JWK, SPKI (public), or PKCS8 (private).")

    private val keyType by option("--key-type")
        .convert { value -> CliKeyAlgorithm.parse(value) }
        .help("Expected key type. Normally detected automatically; rejects input when it does not match.")

    private val passphrase by option("-p", "--passphrase")
        .help("Retained for compatibility. Encrypted PEM is rejected by the strict crypto2 PEM API.")

    private val showPrivate by option("--show-private")
        .flag(default = false)
        .help("Display private JWK or PKCS8 material on stdout. WARNING: this exposes the private key.")

    private val commonOptions by CommonOptions()

    override fun run() {
        if (passphrase != null) {
            throw UsageError("Encrypted PEM is not supported by the strict crypto2 PEM API; decrypt it to PKCS8 PRIVATE KEY PEM first.")
        }

        val content = input.readText()
        val sourceFormat = detectFormat(content)
        val (key, material) = try {
            val decodedMaterial = when (sourceFormat) {
                KeyFileFormat.JWK -> null
                KeyFileFormat.SPKI_PEM -> content.decodePublicKeyPem()
                KeyFileFormat.PKCS8_PEM -> content.decodePrivateKeyPem()
            }
            val decodedKey = runBlocking {
                if (decodedMaterial == null) KeyUtil.importJwk(content)
                else KeyUtil.restore(decodedMaterial, detectSpec(decodedMaterial))
            }
            decodedKey to decodedMaterial
        } catch (cause: Exception) {
            val details = if (commonOptions.verbose) cause.message ?: cause::class.simpleName else "Use --verbose for details."
            throw InvalidFileFormat(input.path, "Invalid key input. $details")
        }
        keyType?.let { expected ->
            require(key.spec == expected.spec((key.spec as? KeySpec.Rsa)?.bits ?: 2048)) {
                "Input key type ${key.spec} does not match --key-type ${expected.optionName}"
            }
        }

        val target = outputFormat ?: when (sourceFormat) {
            KeyFileFormat.JWK -> if (materialIsPrivate(key)) KeyOutputFormat.PKCS8 else KeyOutputFormat.SPKI
            KeyFileFormat.SPKI_PEM, KeyFileFormat.PKCS8_PEM -> KeyOutputFormat.JWK
        }
        val privateOutput = target == KeyOutputFormat.PKCS8 || target == KeyOutputFormat.JWK && materialIsPrivate(key)
        if (privateOutput && output == null && !showPrivate) {
            throw UsageError("Private $target output requires --output or explicit --show-private")
        }
        val outputFile = output ?: if (privateOutput) null
        else File(input.parentFile, "${input.nameWithoutExtension}.${target.extension}")
        val converted = try {
            runBlocking { export(key, target) }
        } catch (cause: Exception) {
            val details = if (commonOptions.verbose) cause.message ?: cause::class.simpleName else "Use --verbose for details."
            throw UsageError("Could not convert key. $details")
        }

        if (privateOutput) {
            print.green("Converted public key metadata (JWK):")
            print.box(runBlocking { KeyUtil.exportJwk(key, private = false) })
            if (showPrivate) {
                print.red("WARNING: displaying private key material.")
                print.box(converted)
            }
        } else {
            print.green("Converted Key ($target):")
            print.box(converted)
        }
        if (outputFile?.exists() == true && YesNoPrompt(
                "The file \"${getNormalizedPath(outputFile.absolutePath)}\" already exists, do you want to overwrite it?",
                terminal,
            ).ask() == false
        ) {
            print.plain("Will not overwrite output file.")
            return
        }
        if (outputFile != null) {
            outputFile.writeText(converted)
            print.greenb("Done. ", false)
            print.plain("Converted \"${getNormalizedPath(input.absolutePath)}\" to \"${getNormalizedPath(outputFile.absolutePath)}\".")
        } else {
            print.plain("No key file written.")
        }
    }

    private fun detectFormat(content: String): KeyFileFormat = when {
        content.trimStart().startsWith('{') -> KeyFileFormat.JWK
        content.startsWith("-----BEGIN PUBLIC KEY-----") -> KeyFileFormat.SPKI_PEM
        content.startsWith("-----BEGIN PRIVATE KEY-----") -> KeyFileFormat.PKCS8_PEM
        content.startsWith("-----BEGIN ENCRYPTED PRIVATE KEY-----") -> throw InvalidFileFormat(
            input.path,
            "Encrypted PEM is not supported; decrypt it to PKCS8 PRIVATE KEY PEM first.",
        )
        else -> throw InvalidFileFormat(
            input.path,
            "Expected JWK, SPKI PUBLIC KEY PEM, or PKCS8 PRIVATE KEY PEM.",
        )
    }

    private fun detectSpec(material: EncodedKey): KeySpec {
        val (algorithm, parameter, rsaBits) = when (material) {
            is EncodedKey.SpkiDer -> SubjectPublicKeyInfo.getInstance(material.data.toByteArray()).let { info ->
                Triple(
                    info.algorithm.algorithm,
                    info.algorithm.parameters,
                    if (info.algorithm.algorithm == PKCSObjectIdentifiers.rsaEncryption)
                        RSAPublicKey.getInstance(info.parsePublicKey()).modulus.bitLength()
                    else null,
                )
            }
            is EncodedKey.Pkcs8Der -> PrivateKeyInfo.getInstance(material.data.toByteArray()).let { info ->
                Triple(
                    info.privateKeyAlgorithm.algorithm,
                    info.privateKeyAlgorithm.parameters,
                    if (info.privateKeyAlgorithm.algorithm == PKCSObjectIdentifiers.rsaEncryption)
                        RSAPrivateKey.getInstance(info.parsePrivateKey()).modulus.bitLength()
                    else null,
                )
            }
            is EncodedKey.Jwk -> error("JWK specifications are detected by the crypto2 JWK parser")
        }
        return when (algorithm) {
            PKCSObjectIdentifiers.rsaEncryption -> KeySpec.Rsa(requireNotNull(rsaBits))
            EdECObjectIdentifiers.id_Ed25519 -> KeySpec.Edwards(EdwardsCurve.ED25519)
            X9ObjectIdentifiers.id_ecPublicKey -> KeySpec.Ec(
                curve(ASN1ObjectIdentifier.getInstance(X962Parameters.getInstance(parameter).parameters))
            )
            else -> throw IllegalArgumentException("Unsupported DER key algorithm OID: $algorithm")
        }
    }

    private fun curve(identifier: ASN1ObjectIdentifier): EcCurve = when (identifier) {
        SECObjectIdentifiers.secp256r1 -> EcCurve.P256
        SECObjectIdentifiers.secp384r1 -> EcCurve.P384
        SECObjectIdentifiers.secp521r1 -> EcCurve.P521
        SECObjectIdentifiers.secp256k1 -> EcCurve.SECP256K1
        else -> throw IllegalArgumentException("Unsupported EC curve OID: $identifier")
    }

    private fun materialIsPrivate(key: Key): Boolean = key.capabilities.privateKeyExporter != null

    private suspend fun export(key: Key, format: KeyOutputFormat): String = when (format) {
        KeyOutputFormat.JWK -> KeyUtil.exportJwk(key, private = materialIsPrivate(key))
        KeyOutputFormat.SPKI -> (requireNotNull(key.capabilities.publicKeyExporter)
            .exportPublicKey(KeyEncodingFormat.SPKI_DER) as EncodedKey.SpkiDer).encodePem()
        KeyOutputFormat.PKCS8 -> (requireNotNull(key.capabilities.privateKeyExporter) {
            "PKCS8 output requires private key material"
        }.exportPrivateKey(KeyEncodingFormat.PKCS8_DER) as EncodedKey.Pkcs8Der).encodePem()
    }

    private val KeyOutputFormat.extension: String
        get() = if (this == KeyOutputFormat.JWK) "jwk" else "pem"
}
