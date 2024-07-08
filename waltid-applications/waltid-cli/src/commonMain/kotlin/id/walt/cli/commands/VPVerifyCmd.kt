package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import id.walt.cli.util.PrettyPrinter
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.path.readText

class VPVerifyCmd : CliktCommand(
    name = "verify",
    printHelpOnEmptyArgs = true,
) {

    val print: PrettyPrinter = PrettyPrinter(this)

    private val holderDid: String by option("-hd", "--holder-did")
        .help("The DID of the verifiable credential's holder (required).")
        .required()

    private val verifierDid: String by option("-vd", "--verifier-did")
        .help("The DID of the verifier for whom the Verifiable Presentation has been created (required).")
        .required()

    private val presentationDefinitionPath by option("-p", "--presentation-definition")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the presentation definition based on which the VP token has been created (required).")
        .required()

    private val presentationSubmissionPath by option("-ps", "--presentation-submission")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the presentation submission (required).")
        .required()

    private val vpPath by option("-vp", "--verifiable-presentation")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the verifiable presentation (required).")
        .required()

    override fun run() {
        initCmd()
    }

    private fun initCmd() = runBlocking {
        DidService.minimalInit()

        val cmdParams = parseParameters()
    }

    private fun parseParameters(): VpVerifyParameters {
        //parse the presentation definition
        val presentationDefinition = PresentationDefinition.fromJSON(
            Json.decodeFromString<JsonObject>(
                presentationDefinitionPath.readText()
            )
        )
        //parse the presentation submission
        val presentationSubmission = PresentationSubmission.fromJSON(
            Json.decodeFromString<JsonObject>(
                presentationSubmissionPath.readText()
            )
        )
        //read the verifiable presentation
        return VpVerifyParameters(
            holderDid,
            verifierDid,
            presentationDefinition,
            presentationSubmission,
            vpPath.readText(),
        )
    }

    private data class VpVerifyParameters(
        val holderDid: String,
        val verifierDid: String,
        val presentationDefinition: PresentationDefinition,
        val presentationSubmission: PresentationSubmission,
        val vp: String,
    )

}