package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.*
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class VCSignCmd : CliktCommand(
    name = "sign",
    help = "Signs a Verifiable Credential.",
    printHelpOnEmptyArgs = true
) {
    val print: PrettyPrinter = PrettyPrinter(this)

    // -k, —key
    // -i, —issuerDid<str>
    // -s, —subjectDid=<str>
    // -vc, —verifiableCredential=<filepath>


    private val keyFile by option("-k", "--key")
        // .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .help("A core-crypto key representation to sign the credential")
        .file()
        .required()

    private val issuerDid by option("-i", "--issuer")
        .help("The verifiable credential's issuer DID")

    private val subjectDid by option("-s", "--subject")
        .help("The verifiable credential's subject DID")
        .required()

    private val vc: File by argument(help = "the verifiable credential file").file()

    override fun run() {

        val key = runBlocking { KeyUtil().getKey(keyFile) }
        val issuerDid = issuerDid ?: runBlocking { DidUtil.createDid(DidMethod.KEY, key) }
        val payload = vc.readText()

        val signedVC = runBlocking {
            VCUtil().sign(key, issuerDid, subjectDid, payload)
        }

        val signedVCFilePath = saveSignedVC(signedVC)

        signedVCFilePath.let {
            print.green("Signed VC saved at ${signedVCFilePath}")
        }

        print.green("Signed VC not saved because the file ${signedVCFilePath} already exists.")
        print.greenb("Signed VC JWS:")
        print.box(signedVC)
    }

    private fun saveSignedVC(signedVC: String): String? {

        val vcFileNamePrefix = vc.nameWithoutExtension
        val vcFileNameExtension = vc.extension
        val vcFilePath = vc.parentFile.path
        val signedVCFileName = "${vcFileNamePrefix}.signed.${vcFileNameExtension}"
        val signedVCFilePath = Path(vcFilePath, signedVCFileName)
        val signedVCFile = File(signedVCFilePath.absolutePathString())

        if (signedVCFile.exists()
            && YesNoPrompt(
                "The file \"${signedVCFile.path}\" already exists, do you want to overwrite it?",
                terminal
            ).ask() == false
        ) {
            print.plain("Will not overwrite output file.")
            return null
        }

        signedVCFile.writeText(signedVC)

        return signedVCFile.path

    }
}
