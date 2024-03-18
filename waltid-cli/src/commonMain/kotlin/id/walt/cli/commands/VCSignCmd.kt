package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.DidMethod
import id.walt.cli.util.DidUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking
import java.io.File

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

        val key = runBlocking { KeySerialization.deserializeKey(keyFile.readText()).getOrThrow() as LocalKey }
        val issuerDid = issuerDid ?: runBlocking { DidUtil.createDid(DidMethod.KEY, key) }
        val payload = vc.readText()

        val signedVC = VCUtil().sign(key, issuerDid, subjectDid, payload)

        print("VC signed!")
    }
}
