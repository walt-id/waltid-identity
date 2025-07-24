package id.walt.cli.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.models.Credential
import id.walt.cli.presexch.MatchPresentationDefinitionCredentialsUseCase
import id.walt.cli.presexch.PresentationDefinitionFilterParser
import id.walt.cli.presexch.PresentationSubmissionBuilder
import id.walt.cli.presexch.strategies.DescriptorPresentationDefinitionMatchStrategy
import id.walt.cli.presexch.strategies.FilterPresentationDefinitionMatchStrategy
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.w3c.PresentationBuilder
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.dif.PresentationDefinition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class VPCreateCmd : CliktCommand(
    name = "create"
) {
    override fun help(context: Context) = """Create a W3C Verifiable Presentation (VP).
        
        Example usage:
        ----------------
        waltid vp create -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -hk ./holder-key.json \
        -vd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -vc ./someVcFile.json \
        -pd ./presDef.json \
        -vp ./outputVp.jwt \
        -ps ./outputPresSub.json
        waltid vp create -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -hk ./holder-key.json \
        -vd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -vc ./firstVcFile.json \
        -vc ./secondVcFile.json \
        -pd ./presDef.json \
        -vp ./outputVp.jwt \
        -ps ./outputPresSub.json
        waltid vp create -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -hk ./holder-key.json \
        -vd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -n some-random-value-goes-here  \
        -vc ./firstVcFile.json \
        -vc ./secondVcFile.json \
        -pd ./presDef.json \
        -vp ./outputVp.jwt \
        -ps ./outputPresSub.json
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

    private val matchStrategies = listOf(
        FilterPresentationDefinitionMatchStrategy(PresentationDefinitionFilterParser()),
        DescriptorPresentationDefinitionMatchStrategy(),
    )

    private val holderDid: String by option("-hd", "--holder-did")
        .help("The DID of the verifiable credential's holder (required).")
        .required()

    private val holderSigningKeyFile: File by option("-hk", "--holder-key")
        .help("The file path of the holder's (private) signing key in JWK format (required).")
        .file(true, true, false, mustBeReadable = true, canBeSymlink = false)
        .required()

    private val verifierDid: String by option("-vd", "--verifier-did")
        .help("The DID of the verifier for whom the Verifiable Presentation is created (required).")
        .required()

    private val nonce: String by option("-n", "--nonce")
        .help("Unique value used in the context of the OID4VP protocol to mitigate replay attacks. Random value will be generated if not specified.")
        .default(randomUUIDString())

    private val inputVcFileList: List<File> by option("-vc", "--vc-file")
        .file(true, true, false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the verifiable credential. Can be specified multiple times to include more than one vc in the vp (required - at least one vc file must be provided).")
        .multiple(required = true)

    private val presentationDefinitionPath by option("-pd", "--presentation-definition")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("The file path of the presentation definition based on which the VP token will be created (required).")
        .required()

    private val vpOutputFilePath by option("-vp", "--vp-output")
        .path()
        .help("File path to save the created vp (required).")
        .required()

    private val presentationSubmissionOutputFilePath by option("-ps", "--presentation-submission-output")
        .path()
        .help("File path to save the created vp (required).")
        .required()


    override fun run() {
        initCmd()

        if (vpOutputFilePath.exists() && YesNoPrompt(
                "The file \"${vpOutputFilePath.absolutePathString()}\" already exists, do you want to overwrite it?",
                terminal
            ).ask() == false
        ) {
            print.plain("Will not overwrite VP output file.")
            return
        }

        if (presentationSubmissionOutputFilePath.exists() && YesNoPrompt(
                "The file \"${presentationSubmissionOutputFilePath.absolutePathString()}\" already exists, do you want to overwrite it?",
                terminal
            ).ask() == false
        ) {
            print.plain("Will not overwrite presentation submission output file.")
            return
        }

        val cmdParams = parseParameters()

        validateParameters(cmdParams)

        val qualifiedVcList = getQualifiedCredentials(cmdParams.vcList, cmdParams.presentationDefinition)
        if (qualifiedVcList.isEmpty()) {
            print.plain("Input credentials do not satisfy requirements of presentation definition.")
            return
        }

        val vpToken = createVpToken(cmdParams, qualifiedVcList)

        val presentationSubmission = PresentationSubmissionBuilder(
            cmdParams.presentationDefinition,
            qualifiedVcList,
        ).buildToString()

        vpOutputFilePath.writeText(vpToken)
        presentationSubmissionOutputFilePath.writeText(presentationSubmission)

        print.greenb("Done.")
        print.plain("VP saved at file \"${vpOutputFilePath.absolutePathString()}\".")
        print.plain("Presentation Submission saved at file \"${presentationSubmissionOutputFilePath.absolutePathString()}\".")
    }

    private fun initCmd() = runBlocking {
        DidService.minimalInit()
    }

    private fun parseParameters(): VpCreateParameters {
        //read all vc files, parse them and add them to a list
        val vcList = inputVcFileList.map { vcFile ->
            vcFile.readText().let { encodedVc ->
                Credential.parseFromJwsString(encodedVc)
            }
        }
        //parse the presentation definition
        val presentationDefinition = PresentationDefinition.fromJSON(
            Json.decodeFromString<JsonObject>(
                presentationDefinitionPath.readText()
            )
        )
        //read the holder's signing key
        val holderSigningKey = runBlocking {
            KeyUtil(this@VPCreateCmd).getKey(holderSigningKeyFile)
        }
        return VpCreateParameters(
            holderSigningKey,
            holderDid,
            verifierDid,
            nonce,
            presentationDefinition,
            vcList,
        )
    }

    private fun validateParameters(params: VpCreateParameters) = runBlocking {
        if (!params.holderSigningKey.hasPrivateKey) throw IllegalArgumentException("Input holder signing key is not a private key")
        DidService.resolveToKey(params.holderDid).onFailure { exception ->
            throw IllegalArgumentException("Failed to resolve holder DID: $exception")
        }.onSuccess { resolvedKey ->
            if (params.holderSigningKey.getPublicKey().getThumbprint() != resolvedKey.getThumbprint())
                throw IllegalArgumentException("Resolved holder public key does not match public key corresponding to input signing (private) key")
        }
        DidService.resolveToKey(params.verifierDid).onFailure { exception ->
            throw IllegalArgumentException("Failed to resolve verifier DID: $exception")
        }
    }

    private fun getQualifiedCredentials(
        vcList: List<Credential>,
        presentationDefinition: PresentationDefinition,
    ): List<Credential> =
        MatchPresentationDefinitionCredentialsUseCase(
            vcList,
            matchStrategies,
        ).match(presentationDefinition)

    private fun createVpToken(
        params: VpCreateParameters,
        qualifiedVcList: List<Credential>,
    ): String = runBlocking {
        PresentationBuilder().apply {
            did = params.holderDid
            nonce = this@VPCreateCmd.nonce
            audience = verifierDid
            addCredentials(qualifiedVcList.map { it.serializedCredential.toJsonElement() })
        }.buildAndSign(params.holderSigningKey)
    }

    private data class VpCreateParameters(
        val holderSigningKey: Key,
        val holderDid: String,
        val verifierDid: String,
        val nonce: String,
        val presentationDefinition: PresentationDefinition,
        val vcList: List<Credential>,
    )

}


