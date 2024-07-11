package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import id.walt.cli.util.JsonUtils.toJsonPrimitive
import id.walt.cli.util.PrettyPrinter
import id.walt.credentials.verification.*
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyResult
import id.walt.credentials.verification.models.PresentationResultEntry
import id.walt.credentials.verification.models.PresentationVerificationResponse
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.path.readText
import kotlin.time.Duration

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

    private val presentationDefinitionPath by option("-pd", "--presentation-definition")
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

    private val vpPolicies: List<String> by option("-vpp", "--vp-policies")
        .help("list of vp policies - signature is always applied")
        .choice(
            listOf(
                "signature",
                "expired",
                "not-before",
                "maximum-credentials",
                "minimum-credentials",
            ).joinToString()
        )
        .multiple()

    private val vpPolicyArguments: Map<String, String> by option("-vppa", "--vp-policy-arg")
        .associate()
        .help {
            """Argument required by some policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |not-before| - |
            |maximum-credentials|max=5|
            |minimum-credentials|min=2|
        """.trimMargin()
        }

    private val globalVcPolicies: List<String> by option("-vcp", "--vc-policies")
        .help("list of vc policies - signature policy  is always applied")
        .choice(
            listOf(
                "signature",
                "expired",
                "not-before",
            ).joinToString()
        )
        .multiple()

    private val globalVcPolicyArguments: Map<String, String> by option("-vcpa", "--vc-policy-arg")
        .associate()
        .help {
            """Argument required by some policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |not-before| - |
        """.trimMargin()
        }

    override fun run() {
        initCmd()
    }

    private fun initCmd() = runBlocking {
        DidService.minimalInit()

        val cmdParams = parseParameters()

        val verificationResponse = runBlocking {
            verify(cmdParams)
        }

        val results = verificationResponse.results.flatMap { it.policyResults }

        print.box("VP Verification Result")
        results.forEach {
            if (it.isSuccess()) { // Not enough to be a successful verification. Sometimes, the verification succeeds because the policy is not even applied.
                handleSuccess(it)
            } else {
                handleFailure(it)
            }
        }
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

        return VpVerifyParameters(
            holderDid,
            verifierDid,
            presentationDefinition,
            presentationSubmission,
            vpPath.readText(),
            parseVpPolicyRequests(),
            parseGlobalVcPolicyRequests(),
        )
    }

    private fun parseVpPolicyRequests(): List<PolicyRequest> {
        val vpPolicyRequests = mutableListOf<PolicyRequest>(
            PolicyRequest(PolicyManager.getPolicy("signature")),
            PolicyRequest(PolicyManager.getPolicy("presentation-definition")),
        )
        vpPolicies.forEach { policyName ->
            vpPolicyRequests += when (policyName) {
                "expired" -> {
                    PolicyRequest(PolicyManager.getPolicy("expired"))
                }

                "not-before" -> {
                    PolicyRequest(PolicyManager.getPolicy("not-before"))
                }

                "maximum-credentials" -> {
                    if ("max" !in vpPolicyArguments || vpPolicyArguments["max"]!!.isEmpty()) {
                        throw MissingOption(this.option("-vppa for the 'maximum-credentials' policy (-vppa=max=5"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy("maximum-credentials"),
                        vpPolicyArguments["max"].toJsonPrimitive()
                    )
                }

                "minimum-credentials" -> {
                    if ("min" !in vpPolicyArguments || vpPolicyArguments["min"]!!.isEmpty()) {
                        throw MissingOption(this.option("-vppa for the 'minimum-credentials' policy (-vppa=min=2"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy("minimum-credentials"),
                        vpPolicyArguments["min"].toJsonPrimitive()
                    )
                }

                else -> throw IllegalArgumentException("Unknown, or inapplicable vp policy $policyName")
            }
        }
        return vpPolicyRequests
    }

    private fun parseGlobalVcPolicyRequests(): List<PolicyRequest> {
        val globalVcPolicyRequests = mutableListOf<PolicyRequest>(
            PolicyRequest(PolicyManager.getPolicy("signature"))
        )
        globalVcPolicies.forEach { policyName ->
            globalVcPolicyRequests += when (policyName) {
                "expired" -> {
                    PolicyRequest(PolicyManager.getPolicy("expired"))
                }

                "not-before" -> {
                    PolicyRequest(PolicyManager.getPolicy("not-before"))
                }

                else -> throw IllegalArgumentException("Unknown, or inapplicable global vc policy $policyName")
            }
        }
        return globalVcPolicyRequests
    }

    private fun verify(params: VpVerifyParameters): PresentationVerificationResponse {
        try {
            return runBlocking {
                Verifier.verifyPresentation(
                    vpTokenJwt = params.vp,
                    vpPolicies = params.vpPolicyRequests,
                    globalVcPolicies = params.globalVcPolicyRequests,
                    specificCredentialPolicies = emptyMap(),
                    mapOf(
                        "presentationDefinition" to params.presentationDefinition.toJsonElement(),
                        "presentationSubmission" to params.presentationSubmission.toJsonElement(),
                    )
                )
            }
        } catch (e: IllegalStateException) {
            println("Something went wrong.")
            return PresentationVerificationResponse(
                results = ArrayList<PresentationResultEntry>(),
                time = Duration.ZERO,
                policiesRun = 0,
            )
        }
    }

    private data class VpVerifyParameters(
        val holderDid: String,
        val verifierDid: String,
        val presentationDefinition: PresentationDefinition,
        val presentationSubmission: PresentationSubmission,
        val vp: String,
        val vpPolicyRequests: List<PolicyRequest>,
        val globalVcPolicyRequests: List<PolicyRequest>,
    )

    private fun handleFailure(it: PolicyResult) {
        when (val exception = it.result.exceptionOrNull()) {

            is JsonSchemaVerificationException -> {
                exception.validationErrors.forEach { err ->
                    print.dim("${it.request.policy.name}: ", false)
                    print.red("Fail! ", false)
                    if (err.objectPath.isEmpty()) {
                        print.italic("""-> ${err.message}""")
                    } else {
                        print.italic(""""${err.objectPath}" (in ${err.schemaPath}) -> ${err.message}""")
                    }
                }
            }

            is ExpirationDatePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC expired since ${exception.date}")
            }

            is NotBeforePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC not valid until ${exception.date}")
            }

            else -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                exception?.message?.let { print.italic(it) }
            }
        }
    }

    private fun handleSuccess(it: PolicyResult) {
        print.dim("${it.request.policy.name}: ", false)
        print.green("Success! ")
    }
}