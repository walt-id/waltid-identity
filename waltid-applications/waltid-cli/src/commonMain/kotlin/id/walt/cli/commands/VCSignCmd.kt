package id.walt.cli.commands

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.*
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class VCSignCmd : CliktCommand(
    name = "sign",
    help = """Sign a W3C Verifiable Credential (VC).
        
        Example usage:
        --------------
        waltid vc sign -k ./myKey.json -s did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 ./myVC.json
        waltid vc sign -k ./myKey.json \
                       -s did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 \
                       -i did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
                       ./myVC.json
    """,
    printHelpOnEmptyArgs = true,
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val keyFile by option("-k", "--key")
        .help("Key to be used to sign the credential, i.e., the issuer's signing key (required).")
        .file(mustExist = true, canBeDir = false, canBeSymlink = false, mustBeReadable = true)
        .required()

    private val issuerDid by option("-i", "--issuer")
        .help("The DID of the verifiable credential's issuer. If not specified, a default will be generated based on the did:key method.")

    private val subjectDid by option("-s", "--subject")
        .help("The DID of the verifiable credential's subject, i.e., the to-be holder of the credential (required).")
        .required()

    private val vc: File by argument(help = "The file path to the Verifiable Credential that will be signed (required).")
        .file(mustExist = true, canBeDir = false, canBeSymlink = false, mustBeReadable = true)

    private val overwrite by option("--overwrite", "-o")
        .help("Flag to overwrite the signed output file if it exists.")
        .flag(default = false)

    override fun run() {

        val key = runBlocking { KeyUtil().getKey(keyFile) }
        val issuerDid = issuerDid ?: runBlocking { DidUtil.createDid(DidMethod.KEY, key).did }

        validateIssuerKey(issuerDid, key)

        val payload = vc.readText()

        val signedVC = runBlocking {
            VCUtil.sign(key, issuerDid, subjectDid, payload)
        }

        val savedSignedVCFilePath = saveSignedVC(signedVC)

        savedSignedVCFilePath?.let {
            print.green("Done. ", false)
            print.plain("Signed VC saved at $savedSignedVCFilePath")
            return
        }

        print.plain("")
        print.red("Fail. ", false)
        print.plain("Signed VC not saved because the file ${getSignedVCFilePath()} already exists. But you can get it below.")
        print.plain("")
        print.green("Signed VC JWS:")
        print.dim("--------------")
        print.dim(signedVC)
        print.dim("--------------")
    }

    private fun validateIssuerKey(issuerDid: String, key: JWKKey) = runBlocking {

        // Check if it's a valid DID
        val didKey = try {
            DidUtil.resolveToKey(issuerDid)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            throw BadParameterValue("DID can not be resolved: $issuerDid")
        }

        // Check the key belongs to the issuer DID
        if (key.getPublicKey().getThumbprint().isEmpty()) {
            throw BadParameterValue("Missing thumbprint from key ${key.getKeyId()}")
        }
        if (key.getPublicKey().getThumbprint() != didKey?.getPublicKey()?.getThumbprint()) {
            throw BadParameterValue("Key ${key.getKeyId()} not associated with issuer DID $issuerDid")
        }

    }

    private fun saveSignedVC(signedVC: String): String? {

        val signedVCFilePath = getSignedVCFilePath()
        val signedVCFile = File(signedVCFilePath)

        if (signedVCFile.exists()) {
            if (!overwrite) {
                if (YesNoPrompt(
                        "The file \"${signedVCFile.path}\" already exists, do you want to overwrite it?",
                        terminal
                    ).ask() == false
                ) {
                    print.dim("Ok. I will not overwrite the output file.")
                    return null
                }
            } else {
                print.dim("""The file "${signedVCFile.path}" already exists and I will overwrite it.""")
            }
        }

        signedVCFile.writeText(signedVC)
        return signedVCFile.path
    }


    private fun getSignedVCFilePath(): String {
        val vcFileNamePrefix = vc.nameWithoutExtension
        val vcFileNameExtension = vc.extension
        val vcFilePath = vc.parentFile.path
        val signedVCFileName = "${vcFileNamePrefix}.signed.${vcFileNameExtension}"
        val signedVCFilePath = Path(vcFilePath, signedVCFileName).absolutePathString()
        return signedVCFilePath
    }
}
