package id.walt.policies

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyResult
import id.walt.policies.models.PresentationResultEntry
import id.walt.policies.models.PresentationVerificationResponse
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVC
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.measureTime

@OptIn(ExperimentalJsExport::class)
@JsExport
object Verifier {

    private val log = KotlinLogging.logger { }

    private fun JsonObject.getW3CType() =
        (this["type"] ?: this["vc"]?.jsonObject?.get("type") ?: this["vp"]?.jsonObject?.get("type")
        ?: throw IllegalArgumentException("No `type` supplied: $this")).let {
            when (it) {
                is JsonArray -> (it.lastOrNull()
                    ?: throw IllegalArgumentException("Empty `type` array! Please provide an type in the list.")).jsonPrimitive.content

                is JsonPrimitive -> it.content
                else -> throw IllegalArgumentException("Invalid type of `type`-attribute: ${it::class.simpleName}")

            }
        }

    private fun JsonObject.getSdjwtVcType() =
        (this["vct"] ?: this["vc"]?.jsonObject?.get("vct")
        ?: throw IllegalArgumentException("No `vct` supplied: $this")).let {
            when (it) {
                is JsonPrimitive -> it.content
                else -> throw IllegalArgumentException("Invalid type of `type`-attribute: ${it::class.simpleName}")
            }
        }

    private fun JsonObject.getAnyType() = runCatching { getW3CType() }
        .recover { getSdjwtVcType() }
        .getOrElse { throw IllegalArgumentException("Cannot determine any type for: $this") }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun PolicyRequest.runPolicyRequest(dataToVerify: JsonElement, context: Map<String, Any>): Result<Any> {
        return when (policy) {
            is JwtVerificationPolicy -> {
                check(dataToVerify is JsonPrimitive) { "Tried to apply JwtVerificationPolicy to non-jwt data: $policy" }
                policy.verify(dataToVerify.content, args, context)
            }

            is CredentialDataValidatorPolicy -> {
                check(dataToVerify is JsonObject) { "Tried to apply CredentialDataValidatorPolicy to non-credential data: $policy" }

                val credentialData = when {
                    dataToVerify["vc"] != null -> dataToVerify["vc"]!!.jsonObject
                    else -> dataToVerify
                }
                policy.verify(credentialData, args, context)
            }

            is CredentialWrapperValidatorPolicy -> {
                check(dataToVerify is JsonObject) { "Tried to apply CredentialWrapperValidatorPolicy to non-credential data: $policy" }
                policy.verify(dataToVerify, args, context)
            }

            else -> throw IllegalArgumentException("Unsupported policy type: ${policy::class.simpleName}")
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verifyCredential(
        jwt: String, policies: List<PolicyRequest>, context: Map<String, Any> = emptyMap(),
    ): List<PolicyResult> {
        val results = ArrayList<PolicyResult>()
        val resultMutex = Mutex()

        runPolicyRequests(jwt, policies, context, onSuccess = { policyResult ->
            resultMutex.withLock {
                results.add(policyResult)
            }
        }, onError = { policyResult, exception ->
            resultMutex.withLock {
                results.add(policyResult)
            }
        })

        return results
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun runPolicyRequests(
        jwt: String,
        policyRequests: List<PolicyRequest>,
        context: Map<String, Any> = emptyMap(),
        onSuccess: suspend (PolicyResult) -> Unit,
        onError: suspend (PolicyResult, Throwable) -> Unit,
    ) {
        coroutineScope {
            policyRequests.forEach { policyRequest ->
                launch {
                    runCatching {
                        val dataForPolicy: JsonElement = when (policyRequest.policy) {
                            is JwtVerificationPolicy -> JsonPrimitive(jwt)

                            is CredentialDataValidatorPolicy, is CredentialWrapperValidatorPolicy -> SDJwt.parse(jwt).fullPayload

                            else -> throw IllegalArgumentException("Unsupported policy type: ${policyRequest.policy::class.simpleName}")
                        }
                        val runResult = policyRequest.runPolicyRequest(dataForPolicy, context)
                        val policyResult = PolicyResult(policyRequest, runResult)
                        onSuccess(policyResult)
                    }.onFailure {
                        onError(PolicyResult(policyRequest, Result.failure(it)), it)
                    }
                }
            }
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verifyPresentation(
        format: VCFormat,
        vpToken: String,
        vpPolicies: List<PolicyRequest>,
        globalVcPolicies: List<PolicyRequest>,
        specificCredentialPolicies: Map<String, List<PolicyRequest>>,
        presentationContext: Map<String, Any> = emptyMap(),
    ): PresentationVerificationResponse {
        log.trace { "Verifying presentation with format $format and serialized vp_token $vpToken" }

        return when (format) {

            VCFormat.mso_mdoc -> TODO("mdoc presentations are not yet supported")

            VCFormat.sd_jwt_vc -> verifySDJwtVCPresentation(
                vpToken = vpToken,
                vpPolicies = vpPolicies,
                globalVcPolicies = globalVcPolicies,
                specificCredentialPolicies = specificCredentialPolicies,
                presentationContext = presentationContext
            )
            else -> verifyW3CPresentation(
                format = format,
                vpToken = vpToken,
                vpPolicies = vpPolicies,
                globalVcPolicies = globalVcPolicies,
                specificCredentialPolicies = specificCredentialPolicies,
                presentationContext = presentationContext
            )
        }
    }

    /**
     * "W3C" in this case refers to the Verifiable *Presentation* (which in itself is a special
     * kind of credential). The VP can contain SD-JWT VC etc., but itself it is still W3C.
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verifyW3CPresentation(
        format: VCFormat,
        vpToken: String,
        vpPolicies: List<PolicyRequest>,
        globalVcPolicies: List<PolicyRequest>,
        specificCredentialPolicies: Map<String, List<PolicyRequest>>,
        presentationContext: Map<String, Any> = emptyMap(),
    ): PresentationVerificationResponse {
        val providedJws = vpToken.decodeJws() // usually VP
        val payload = providedJws.payload
        val vpType = when (payload.contains("vp")) {
            true -> payload.getW3CType()
            else -> "" // else is IdToken
        }

        val verifiableCredentialJwts = when (payload.contains("vp")) {
            true -> (payload["vp"]?.jsonObject?.get("verifiableCredential") ?: payload["verifiableCredential"]
            ?: TODO("Provided data does not have `verifiableCredential` array.")).jsonArray.map { it.jsonPrimitive.content }

            else -> emptyList()
        }

        val results = ArrayList<PresentationResultEntry>()

        val resultMutex = Mutex()
        var policiesRun = 0

        val time = measureTime {
            coroutineScope {
                fun addResultEntryFor(type: String): Int {
                    results.add(PresentationResultEntry(type))
                    return results.size - 1
                }

                suspend fun runPolicyRequests(idx: Int, jwt: String, policies: List<PolicyRequest>) =
                    runPolicyRequests(
                        jwt = jwt,
                        policyRequests = policies,
                        context = presentationContext,
                        onSuccess = { policyResult ->
                            resultMutex.withLock {
                                policiesRun++
                                results[idx].policyResults.add(policyResult)
                            }
                        },
                        onError = { policyResult, exception ->
                            resultMutex.withLock {
                                policiesRun++
                                results[idx].policyResults.add(policyResult)
                            }
                        }
                    )

                /* VP Policies */
                when (payload.contains("vp")) {
                    true -> {
                        val vpIdx = addResultEntryFor(vpType)
                        runPolicyRequests(
                            idx = vpIdx,
                            jwt = vpToken,
                            policies = vpPolicies
                        )
                    }

                    else -> {
                        val vpIdx = 0
                        results.add(PresentationResultEntry(vpToken))
                        runPolicyRequests(
                            idx = vpIdx,
                            jwt = vpToken,
                            policies = vpPolicies
                        )
                    }
                }

                // VCs
                verifiableCredentialJwts.forEach { credentialJwt ->
                    val credentialType = credentialJwt.substringBefore("~").decodeJws().payload.getAnyType()

                    val vcIdx = addResultEntryFor(credentialType)

                    /* Global VC Policies */
                    runPolicyRequests(
                        idx = vcIdx,
                        jwt = credentialJwt,
                        policies = globalVcPolicies
                    )

                    /* Specific Credential Policies */
                    specificCredentialPolicies[credentialType]?.let { specificPolicyRequests ->
                        runPolicyRequests(
                            idx = vcIdx,
                            jwt = credentialJwt,
                            policies = specificPolicyRequests
                        )
                    }
                }
            }
        }

        return PresentationVerificationResponse(
            results = results,
            time = time,
            policiesRun = policiesRun
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun verifySDJwtVCPresentation(
        vpToken: String,
        vpPolicies: List<PolicyRequest>,
        globalVcPolicies: List<PolicyRequest>,
        specificCredentialPolicies: Map<String, List<PolicyRequest>>,
        presentationContext: Map<String, Any> = emptyMap(),
    ): PresentationVerificationResponse {
        log.trace { "Verifying SD-JWT VC Presentation, vp_token: $vpToken" }
        val sdJwtVC = SDJwtVC.parse(vpToken)
        val vpType = sdJwtVC.type ?: sdJwtVC.vct ?: ""
        log.trace { "SD-JWT VC Presentation vpType: $vpType" }

        val results = ArrayList<PresentationResultEntry>()

        val resultMutex = Mutex()
        var policiesRun = 0

        val time = measureTime {
            coroutineScope {
                suspend fun runPolicyRequests(idx: Int, jwt: String, policies: List<PolicyRequest>) =
                    runPolicyRequests(
                        jwt = jwt,
                        policyRequests = policies,
                        context = presentationContext,
                        onSuccess = { policyResult ->
                            resultMutex.withLock {
                                policiesRun++
                                results[idx].policyResults.add(policyResult)
                            }
                        },
                        onError = { policyResult, exception ->
                            resultMutex.withLock {
                                policiesRun++
                                results[idx].policyResults.add(policyResult)
                            }
                        })

                /* VP Policies */
                results.add(PresentationResultEntry(vpToken))
                runPolicyRequests(
                    idx = 0,
                    jwt = vpToken,
                    policies = vpPolicies
                )

                // VCs
                if (globalVcPolicies.size > 0 || specificCredentialPolicies.containsKey(vpType)) {
                    results.add(PresentationResultEntry(vpType))

                    /* Global VC Policies */
                    runPolicyRequests(
                        idx = 1,
                        jwt = vpToken,
                        policies = globalVcPolicies
                    )

                    /* Specific Credential Policies */
                    specificCredentialPolicies[vpType]?.let { specificPolicyRequests ->
                        runPolicyRequests(
                            idx = 1,
                            jwt = vpToken,
                            policies = specificPolicyRequests
                        )
                    }
                }
            }
        }

        return PresentationVerificationResponse(
            results = results,
            time = time,
            policiesRun = policiesRun
        )
    }


    private val EMPTY_MAP = emptyMap<String, Any>()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Suppress("UNCHECKED_CAST" /* as? */)
    suspend fun verifyJws(jwt: String): Result<JsonObject> =
        JwtSignaturePolicy().verify(jwt, null, EMPTY_MAP) as? Result<JsonObject>
            ?: Result.failure(IllegalArgumentException("Could not get JSONObject from VC verification"))

}
