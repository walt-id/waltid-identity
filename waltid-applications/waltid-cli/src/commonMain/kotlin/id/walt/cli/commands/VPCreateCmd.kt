package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.credentials.PresentationBuilder
import id.walt.did.dids.DidService
import id.walt.did.utils.randomUUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class VPCreateCmd : CliktCommand(
    name = "create",
    help = "Create a new Verifiable Presentation.",
    printHelpOnEmptyArgs = true,
) {

    val print: PrettyPrinter = PrettyPrinter(this)

    private val holderDid: String by option("-hd", "--holder-did")
        .help("The DID of the verifiable credential's holder.")
        .required()

    private val holderSigningKeyFile: File by option("-hk", "--holder-key")
        .help("The file path of the holder's signing key (required).")
        .file(true, true, false, mustBeReadable = true, canBeSymlink = false)
        .required()

    private val verifierDid: String by option("-vd", "--verifier-did")
        .help("The DID of the verifier for whom the Verifiable Presentation is created.")
        .required()

    private val nonce: String by option("-n", "--nonce")
        .help("asdfasdfasdf")
        .default(randomUUID())

    private val inputVcFileList: List<File> by option("-vc", "--vc-file")
        .file(true, true, false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the verifiable credential. Can be specified multiple times to include more than one vc in the vp (required - at least once).")
        .multiple(required = true)

    private val vpOutputFilePath by option("-o", "--vp-output")
        .path()
        .help("File path to save the created vp (required).")
        .required()


    override fun run() {
        runBlocking {
            DidService.minimalInit()
            val vcList = inputVcFileList.map { vcFile ->
                Json.decodeFromString<JsonElement>(vcFile.readText())
            }
            val holderSigningKey = KeyUtil(this@VPCreateCmd).getKey(holderSigningKeyFile)
            val vpToken = PresentationBuilder().apply {
                did = holderDid
                nonce = this@VPCreateCmd.nonce
                audience = verifierDid
                addCredentials(vcList)
            }.buildAndSign(holderSigningKey)

            if (vpOutputFilePath.exists()
                && YesNoPrompt(
                    "The file \"${vpOutputFilePath.absolutePathString()}\" already exists, do you want to overwrite it?",
                    terminal
                ).ask() == false
            ) {
                print.plain("Will not overwrite output file.")
                return@runBlocking
            }
            vpOutputFilePath.writeText(vpToken)
            print.greenb("Done. ", linebreak = false)
            print.plain("VP saved at file \"${vpOutputFilePath.absolutePathString()}\".")
        }
    }
}
