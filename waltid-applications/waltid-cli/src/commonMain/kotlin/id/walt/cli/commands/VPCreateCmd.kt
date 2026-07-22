package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.CliDcql
import id.walt.cli.util.CredentialHolderBinding
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import id.walt.w3c.PresentationBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.random.Random
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class VPCreateCmd : CliktCommand(name = "create") {
    override fun help(context: Context) = """Create Final OpenID4VP jwt_vc_json presentations selected by DCQL.

        Example usage:
        ----------------
        waltid vp create -hd <holder-did> -hk holder.jwk -vd <verifier-id> -n <nonce> \
          -vc credential.jwt -dq query.json -vp vp-token.json

        Credential signatures, holder binding, trusted authorities, cardinality, and credential sets are enforced.
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
        context { localization = WaltIdCmdHelpOptionMessage }
    }

    override val printHelpOnEmptyArgs = true

    val print = PrettyPrinter(this)

    private val holderDid by option("-hd", "--holder-did")
        .help("DID of the holder signing each presentation.")
        .required()

    private val holderSigningKeyFile: File by option("-hk", "--holder-key")
        .help("Holder private signing key in JWK format.")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .required()

    private val verifierDid by option("-vd", "--verifier-did")
        .help("Verifier client ID used as the audience.")
        .required()

    private val nonce by option("-n", "--nonce")
        .help("OpenID4VP nonce. A random value is generated when omitted.")
        .default(randomNonce())

    private val inputVcFileList: List<File> by option("-vc", "--vc-file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("Credential file. Repeat for multiple credentials.")
        .multiple(required = true)

    private val dcqlQueryPath by option("-dq", "--dcql-query")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("Final OpenID4VP DCQL query file.")
        .required()

    private val vpOutputFilePath by option("-vp", "--vp-output")
        .path()
        .help("Output file for the Final vp_token JSON object.")
        .required()

    override fun run() {
        if (vpOutputFilePath.exists() && YesNoPrompt(
                "The file \"${vpOutputFilePath.absolutePathString()}\" already exists, do you want to overwrite it?",
                terminal,
            ).ask() == false
        ) {
            print.plain("Will not overwrite VP output file.")
            return
        }

        val output = try {
            runBlocking {
            val holderKey = KeyUtil().getKey(holderSigningKeyFile)
            requireNotNull(holderKey.capabilities.signer) { "Input holder signing key is not a private signing key" }
            require(KeyUtil.didContainsKey(holderDid, holderKey)) {
                "Resolved holder DID does not contain the public key corresponding to the input signing key"
            }

            val query = DcqlParser.parse(dcqlQueryPath.readText()).getOrThrow().also(CliDcql::validate)
            val credentials = inputVcFileList.mapIndexed { index, file ->
                VCUtil.parse(file.readText()).toDcqlCredential(index.toString())
            }
            val matches = CliDcql.match(query, credentials)
            require(matches.isNotEmpty()) { "Input credentials do not satisfy the DCQL query" }

                JsonObject(matches.mapValues { (_, queryMatches) ->
                JsonArray(queryMatches.map { match ->
                    val credential = (match.credential as? RawDcqlCredential)?.originalCredential as? DigitalCredential
                        ?: error("DCQL match did not retain its parsed credential")
                    val signature = VCUtil.verify(credential, VCUtil.policies(listOf("signature"), emptyMap())).single()
                    require(signature.result.isSuccess) {
                        "Credential signature verification failed: ${signature.result.exceptionOrNull()?.message}"
                    }
                    if (match.originalQuery.requireCryptographicHolderBinding) {
                        CredentialHolderBinding.requireBoundToPresenter(credential, holderDid, holderKey)
                    }
                    require(credential.format == "jwt_vc_json") {
                        "vp create currently supports jwt_vc_json only; ${credential.format} requires format-specific holder binding/session data"
                    }
                    require(
                        credential !is SelectivelyDisclosableVerifiableCredential || credential.disclosures.isNullOrEmpty()
                    ) {
                        "Selectively disclosable credentials require a format-specific key-binding presentation"
                    }
                    val token = PresentationBuilder().apply {
                        did = holderDid
                        this.nonce = this@VPCreateCmd.nonce
                        audience = verifierDid
                        addCredential(JsonPrimitive(requireNotNull(credential.signed) { "DCQL matched an unsigned credential" }))
                    }.buildAndSign(holderKey, holderKey.preferredJwsAlgorithm())
                    JsonPrimitive(token)
                })
                })
            }
        } catch (cause: Exception) {
            throw UsageError(cause.message ?: cause::class.simpleName ?: "Could not create presentation")
        }

        vpOutputFilePath.writeText(Json { prettyPrint = true }.encodeToString(output))
        print.greenb("Done.")
        print.plain("VP token saved at file \"${vpOutputFilePath.absolutePathString()}\".")
    }

    private fun DigitalCredential.toDcqlCredential(id: String): RawDcqlCredential {
        val selectivelyDisclosable = this as? SelectivelyDisclosableVerifiableCredential
        return RawDcqlCredential(
            id = id,
            format = format,
            data = credentialData,
            disclosures = selectivelyDisclosable?.disclosures?.map { DcqlDisclosure(it.name, it.value) },
            originalCredential = this,
        )
    }

    private fun randomNonce(): String = buildString {
        append(Random.nextLong().toULong().toString(16))
        append(Random.nextLong().toULong().toString(16))
    }
}
