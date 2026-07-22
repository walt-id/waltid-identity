package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import id.walt.cli.util.CliDcql
import id.walt.cli.util.CredentialHolderBinding
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlParser
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.policies2.vp.policies.AudienceCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.ExpCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.JwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.NbfCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.NonceCheckJwtVcJsonVPPolicy
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VerificationSessionContext
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import kotlin.io.path.readText

class VPVerifyCmd : CliktCommand(name = "verify") {
    override fun help(context: Context) = """Verify Final OpenID4VP DCQL jwt_vc_json presentations.

        Example usage:
        ----------------
        waltid vp verify -hd <holder-did> -vd <verifier-id> -n <nonce> \
          -dq query.json -vp vp-token.json

        Envelope and credential signatures are mandatory. Requested policies are additive.
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
        context { localization = WaltIdCmdHelpOptionMessage }
    }

    override val printHelpOnEmptyArgs = true

    val print = PrettyPrinter(this)

    private val holderDid by option("-hd", "--holder-did")
        .help("Expected holder DID. When omitted, the signed iss claim is used.")
        .default("")

    private val verifierDid by option("-vd", "--verifier-did")
        .help("Expected verifier client ID/audience.")
        .required()

    private val nonce by option("-n", "--nonce")
        .help("Expected OpenID4VP nonce.")
        .required()

    private val dcqlQueryPath by option("-dq", "--dcql-query")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("Original Final OpenID4VP DCQL query.")
        .required()

    private val vpPath by option("-vp", "--verifiable-presentation")
        .path(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
        .help("Final vp_token JSON object to verify.")
        .required()

    private val vpPolicies by option("-vpp", "--vp-policy")
        .choice("signature", "expired", "not-before")
        .multiple()
        .help("Envelope policies. Signature, audience, and nonce are always checked; time checks run by default.")

    private val globalVcPolicies by option("-vcp", "--vc-policy")
        .choice(
            "signature",
            "expired",
            "expiration",
            "not-before",
            "revoked-status-list",
            "schema",
            "allowed-issuer",
            "webhook",
        )
        .multiple()

    private val rawVcPolicyArguments by option("-vcpa", "--vc-policy-arg")
        .convert { value ->
            value.substringBefore('=', missingDelimiterValue = "").takeIf(String::isNotBlank)
                ?.let { it to value.substringAfter('=') }
                ?: fail("must use name=value")
        }
        .multiple()

    override fun run() {
        val result = runCatching { runBlocking { verify() } }
        result.exceptionOrNull()?.let { cause ->
            print.dim("Overall: ", false)
            print.red("Fail! ", false)
            print.italic(cause.message ?: cause::class.simpleName ?: "Verification error")
        }
        if (result.getOrDefault(false).not()) throw ProgramResult(1)
    }

    private suspend fun verify(): Boolean {
        val query = DcqlParser.parse(dcqlQueryPath.readText()).getOrThrow().also(CliDcql::validate)
        val presentations = parseVpToken(vpPath.readText(), query)
        val vcArguments = rawVcPolicyArguments.groupBy({ it.first }, { it.second }).toMutableMap()
        if ("schema" in globalVcPolicies) {
            val schemaPath = vcArguments["schema"]?.singleOrNull()
                ?: throw IllegalArgumentException("Policy schema requires exactly one --vc-policy-arg=schema=/path/to/schema.json")
            vcArguments["schema"] = listOf(File(schemaPath).also {
                require(it.isFile) { "Schema file does not exist: $schemaPath" }
            }.readText())
        }
        val vcPolicyList = VCUtil.policies(globalVcPolicies, vcArguments)
        val timePolicyNames = vpPolicies.ifEmpty { listOf("expired", "not-before") }
        val policyList = buildList<JwtVcJsonVPPolicy> {
            add(AudienceCheckJwtVcJsonVPPolicy())
            add(NonceCheckJwtVcJsonVPPolicy())
            if ("expired" in timePolicyNames) add(ExpCheckJwtVcJsonVPPolicy())
            if ("not-before" in timePolicyNames) add(NbfCheckJwtVcJsonVPPolicy())
        }
        var overall = true
        val matchedQueryIds = mutableSetOf<String>()

        print.box("VP Verification Result")
        presentations.forEach { (queryId, tokens) ->
            val queryCredential = query.credentials.single { it.id == queryId }
            val queryCredentials = mutableListOf<RawDcqlCredential>()
            tokens.forEachIndexed { presentationIndex, token ->
                val presentation = JwtVcJsonPresentation.parse(token).getOrThrow()
                val signerDid = requireNotNull(presentation.issuer) { "VP JWT is missing iss" }
                require(signerDid.startsWith("did:")) { "CLI VP verification requires a DID holder issuer" }
                if (holderDid.isNotEmpty()) require(holderDid == signerDid) {
                    "Expected holder DID $holderDid, got $signerDid"
                }
                val decoded = CompactJws.decodeUnverified(token)
                require(decoded.algorithm in JwsAlgorithm.fullySpecified) {
                    "VP JWT uses a non-fully-specified algorithm: ${decoded.algorithm.identifier}"
                }
                val kid = decoded.protectedHeader["kid"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                val verificationKey = KeyUtil.resolveDidVerificationKey(signerDid, kid)
                val signatureResult = runCatching {
                    CompactJws.verify(token, verificationKey, setOf(decoded.algorithm))
                }
                printPolicy("$queryId: jwt_vc_json/envelope_signature", signatureResult.exceptionOrNull())
                overall = overall && signatureResult.isSuccess

                val context = VerificationSessionContext(
                    vpToken = token,
                    expectedNonce = nonce,
                    expectedAudience = verifierDid,
                    expectedOrigins = null,
                    responseUri = null,
                    responseMode = OpenID4VPResponseMode.DIRECT_POST,
                    isSigned = false,
                    isEncrypted = false,
                    jwkThumbprint = null,
                    isAnnexC = false,
                )
                VPPolicyRunner.verifySpecificPresentation(presentation, policyList, context).forEach { (id, result) ->
                    printPolicy("$queryId: $id", result.errors.firstOrNull()?.let { IllegalArgumentException(it.message) })
                    overall = overall && result.success
                }

                val credentials = presentation.credentials.orEmpty()
                require(credentials.isNotEmpty()) { "Presentation for $queryId contains no credentials" }
                credentials.forEachIndexed { credentialIndex, credential ->
                    if (queryCredential.requireCryptographicHolderBinding) {
                        val binding = runCatching {
                            CredentialHolderBinding.requireBoundToPresenter(credential, signerDid, verificationKey)
                        }
                        printPolicy("$queryId credential[$credentialIndex]: holder-binding", binding.exceptionOrNull())
                        overall = overall && binding.isSuccess
                    }
                    VCUtil.verify(credential, vcPolicyList).forEach { run ->
                        printPolicy("$queryId credential[$credentialIndex]: ${run.policy.id}", run.result.exceptionOrNull())
                        overall = overall && run.result.isSuccess
                    }
                    queryCredentials += credential.toDcqlCredential("$queryId-$presentationIndex-$credentialIndex")
                }
            }

            val cardinality = runCatching {
                CliDcql.requireExactResponseCardinality(queryCredential, tokens.size, queryCredentials.size)
            }
            printPolicy("$queryId: cardinality", cardinality.exceptionOrNull())
            overall = overall && cardinality.isSuccess
            val match = runCatching { CliDcql.match(DcqlQuery(listOf(queryCredential)), queryCredentials) }
            val dcqlSuccess = match.getOrNull()?.get(queryId)?.size == queryCredentials.size
            printPolicy("$queryId: dcql", if (dcqlSuccess) null else IllegalArgumentException("Presentation does not satisfy query"))
            overall = overall && dcqlSuccess
            if (dcqlSuccess && cardinality.isSuccess) matchedQueryIds += queryId
        }

        val fulfillment = runCatching { CliDcql.enforceCredentialSets(query, matchedQueryIds) }
        printPolicy("dcql fulfillment", fulfillment.exceptionOrNull())
        overall = overall && fulfillment.isSuccess
        print.dim("Overall: ", false)
        if (overall) print.green("Success!") else print.red("Fail!")
        return overall
    }

    private fun parseVpToken(raw: String, query: DcqlQuery): Map<String, List<String>> {
        val json = Json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("Final vp_token must be a JSON object")
        val queryIds = query.credentials.mapTo(mutableSetOf()) { it.id }
        require(json.keys.all(queryIds::contains)) { "vp_token contains an unknown DCQL query ID" }
        return json.mapValues { (queryId, value) ->
            val tokens = value as? JsonArray
                ?: throw IllegalArgumentException("vp_token entry $queryId must be an array")
            require(tokens.isNotEmpty()) { "vp_token entry $queryId must not be empty" }
            tokens.map { element ->
                (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    ?: throw IllegalArgumentException("vp_token presentations must be strings")
            }
        }
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

    private fun printPolicy(name: String, error: Throwable?) {
        print.dim("$name: ", false)
        if (error == null) print.green("Success!") else {
            print.red("Fail! ", false)
            print.italic(error.message ?: error::class.simpleName ?: "Verification error")
        }
    }
}
