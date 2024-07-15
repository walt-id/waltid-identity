package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import id.walt.cli.util.JsonUtils.toJsonPrimitive
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.credentials.verification.*
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyResult
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
    help = """Apply a wide range of verification policies on a W3C Verifiable Presentation (VP).
        
        Example usage:
        ----------------
        waltid vp verify -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -pd ./presDef.json \
        -ps ./presSub.json \
        -vp ./vpPath.jwt
        waltid vp verify -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -pd ./presDef.json \
        -ps ./presSub.json \
        -vp ./vpPath.jwt \
        -vpp maximum-credentials \
        -vppa=max=2 \
        -vpp minimum-credentials \
        -vppa=min=1
        waltid vp verify -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -pd ./presDef.json \
        -ps ./presSub.json \
        -vp ./vpPath.jwt \
        -vpp maximum-credentials \
        -vppa=max=2 \
        -vpp minimum-credentials \
        -vppa=min=1 \
        -vcp allowed-issuer \
        -vcpa=issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV
    """,
    printHelpOnEmptyArgs = true,
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val holderDid: String by option(
        "-hd", "--holder-did"
    ).help("The DID of the holder that created (signed) the verifiable presentation. If specified, the VP token signature will be validated against this value.")
        .default("")

    private val presentationDefinitionPath by option("-pd", "--presentation-definition").path(
        mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false
    ).help("The file path of the presentation definition (required).")
        .required()

    private val presentationSubmissionPath by option("-ps", "--presentation-submission").path(
        mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false
    ).help("The file path of the presentation submission (required).").required()

    private val vpPath by option("-vp", "--verifiable-presentation").path(
        mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false
    ).help("The file path of the verifiable presentation (required).").required()

    private val vpPolicies: List<String> by option(
        "-vpp", "--vp-policy"
    ).help("Specify one, or more policies to be applied while validating the VP JWT (signature policy is always applied).")
        .choice(
            *listOf(
                "signature",
                "expired",
                "not-before",
                "holder-binding",
                "maximum-credentials",
                "minimum-credentials",
            ).toTypedArray()
        ).multiple()

    private val vpPolicyArguments: Map<String, String> by option("-vppa", "--vp-policy-arg").associate().help {
        """Argument required by some VP policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |not-before| - |
            |maximum-credentials|max=5|
            |minimum-credentials|min=2|
        """.trimMargin()
    }

    private val globalVcPolicies: List<String> by option(
        "-vcp", "--vc-policy"
    ).help("Specify one, or more policies to be applied to all credentials contained in the VP JWT (signature policy is always applied).")
        .choice(
            *listOf(
                "signature",
                "expired",
                "not-before",
                "allowed-issuer",
                "webhook",
            ).toTypedArray()
        ).multiple()

    private val globalVcPolicyArguments: Map<String, String> by option("-vcpa", "--vc-policy-arg").associate().help {
        """Argument required by some VC policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |not-before| - |
            |allowed-issuer|issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV|
            |webhook|url=https://example.com|
        """.trimMargin()
    }

    override fun run() {
        initCmd()

        val cmdParams = parseParameters()

        val verificationResponse = runBlocking {
            verify(cmdParams)
        }

        val results = verificationResponse.results.flatMap { it.policyResults }
        println(results)

        print.box("VP Verification Result")
        print.dim("Overall: ", false)
        if (verificationResponse.overallSuccess()) print.green("Success!") else print.red("Fail!")

        results.forEach {
            if (it.isSuccess()) {
                handleSuccess(it)
            } else {
                handleFailure(it)
            }
        }
    }

    private fun initCmd() = runBlocking {
        DidService.minimalInit()
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
            presentationDefinition,
            presentationSubmission,
            vpPath.readText(),
            parseVpPolicyRequests(),
            parseGlobalVcPolicyRequests(),
        )
    }

    private fun parseVpPolicyRequests(): List<PolicyRequest> {
        val vpPolicyRequests = mutableListOf(
            PolicyRequest(PolicyManager.getPolicy("signature")),
        )
        if (holderDid.isNotEmpty()) vpPolicyRequests += PolicyRequest(
            PolicyManager.getPolicy("allowed-issuer"), holderDid.toJsonPrimitive()
        )
        vpPolicies.forEach { policyName ->
            vpPolicyRequests += when (policyName) {
                "expired", "not-before", "holder-binding" -> {
                    PolicyRequest(PolicyManager.getPolicy(policyName))
                }

                "maximum-credentials" -> {
                    if ("max" !in vpPolicyArguments || vpPolicyArguments["max"]!!.isEmpty()) {
                        throw MissingOption(this.option("-vppa for the 'maximum-credentials' policy (-vppa=max=5"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy(policyName), vpPolicyArguments["max"].toJsonPrimitive()
                    )
                }

                "minimum-credentials" -> {
                    if ("min" !in vpPolicyArguments || vpPolicyArguments["min"]!!.isEmpty()) {
                        throw MissingOption(this.option("-vppa for the 'minimum-credentials' policy (-vppa=min=2"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy(policyName), vpPolicyArguments["min"].toJsonPrimitive()
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
                "expired", "not-before" -> {
                    PolicyRequest(PolicyManager.getPolicy(policyName))
                }

                "allowed-issuer" -> {
                    if ("issuer" !in globalVcPolicyArguments || globalVcPolicyArguments["issuer"]!!.isEmpty()) {
                        throw MissingOption(this.option("--vcpa for the 'allowed-issuer' policy (--vcpa=issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy(policyName),
                        globalVcPolicyArguments["issuer"].toJsonElement(),
                    )
                }

                "webhook" -> {
                    if ("url" !in globalVcPolicyArguments || globalVcPolicyArguments["url"]!!.isEmpty()) {
                        throw MissingOption(this.option("--vcpa for the 'webhook' policy (--vcpa=url=https://example.com"))
                    }
                    PolicyRequest(
                        PolicyManager.getPolicy(policyName),
                        globalVcPolicyArguments["url"].toJsonElement(),
                    )
                }

                else -> throw IllegalArgumentException("Unknown, or inapplicable vc policy $policyName")
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
                        "presentationDefinition" to params.presentationDefinition,
                        "presentationSubmission" to params.presentationSubmission,
                    )
                )
            }
        } catch (e: IllegalStateException) {
            println("Something went wrong.")
            return PresentationVerificationResponse(
                results = ArrayList(),
                time = Duration.ZERO,
                policiesRun = 0,
            )
        }
    }

    private data class VpVerifyParameters(
        val holderDid: String,
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
                        print.italic("""-> ${err.message}""", linebreak = true)
                    } else {
                        print.italic(
                            """"${err.objectPath}" (in ${err.schemaPath}) -> ${err.message}""", linebreak = true
                        )
                    }
                }
            }

            is ExpirationDatePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC expired since ${exception.date}", linebreak = true)
            }

            is NotBeforePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC not valid until ${exception.date}", linebreak = true)
            }

            else -> {
                print.dim("${it.request.policy.name}: ", false)
                exception?.message?.let {
                    print.red("Fail! ", false)
                    print.italic(it, linebreak = true)
                } ?: print.red("Fail! ")
            }
        }
    }

    private fun handleSuccess(it: PolicyResult) {
        print.dim("${it.request.policy.name}: ", false)
        print.green("Success!", linebreak = true)
    }
}