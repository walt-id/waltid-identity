package id.walt.policies

import id.walt.credentials.utils.VCFormat
import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyResult
import id.walt.policies.models.PresentationResultEntry
import id.walt.policies.models.PresentationVerificationResponse
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVC
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

    private val log = KotlinLogging.logger {  }

    private fun JsonObject.getW3CType() = (this["type"] ?: this["vc"]?.jsonObject?.get("type") ?: this["vp"]?.jsonObject?.get("type")
    ?: throw IllegalArgumentException("No `type` supplied: $this")).let {
        when (it) {
            is JsonArray -> (it.lastOrNull()
                ?: throw IllegalArgumentException("Empty `type` array! Please provide an type in the list.")).jsonPrimitive.content

            is JsonPrimitive -> it.content
            else -> throw IllegalArgumentException("Invalid type of `type`-attribute: ${it::class.simpleName}")

        }
    }

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
        return when(format) {
            VCFormat.sd_jwt_vc -> verifySDJwtVCPresentation(vpToken, vpPolicies, globalVcPolicies, specificCredentialPolicies, presentationContext)
            VCFormat.mso_mdoc -> TODO()
            else -> verifyW3CPresentation(format, vpToken, vpPolicies, globalVcPolicies, specificCredentialPolicies, presentationContext)
        }
    }

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
                    runPolicyRequests(jwt, policies, presentationContext, onSuccess = { policyResult ->
                        resultMutex.withLock {
                            policiesRun++
                            results[idx].policyResults.add(policyResult)
                        }
                    }, onError = { policyResult, exception ->
                        resultMutex.withLock {
                            policiesRun++
                            results[idx].policyResults.add(policyResult)
                        }
                    })

                /* VP Policies */
                when (payload.contains("vp")) {
                    true -> {
                        val vpIdx = addResultEntryFor(vpType)
                        runPolicyRequests(vpIdx, vpToken, vpPolicies)
                    }

                    else -> {
                        val vpIdx = 0
                        results.add(PresentationResultEntry(vpToken))
                        runPolicyRequests(vpIdx, vpToken, vpPolicies)
                    }
                }

                // VCs
                verifiableCredentialJwts.forEach { credentialJwt ->
                    val credentialType = credentialJwt.decodeJws().payload.getW3CType()
                    val vcIdx = addResultEntryFor(credentialType)

                    /* Global VC Policies */
                    runPolicyRequests(vcIdx, credentialJwt, globalVcPolicies)

                    /* Specific Credential Policies */
                    specificCredentialPolicies[credentialType]?.let { specificPolicyRequests ->
                        runPolicyRequests(vcIdx, credentialJwt, specificPolicyRequests)
                    }
                }
            }
        }

        return PresentationVerificationResponse(results, time, policiesRun)
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
        val sdJwtVC = SDJwtVC.parse(vpToken)
        val payload = sdJwtVC.fullPayload
        val vpType =  sdJwtVC.type ?: sdJwtVC.vct ?: ""

        val results = ArrayList<PresentationResultEntry>()

        val resultMutex = Mutex()
        var policiesRun = 0

        val time = measureTime {
            coroutineScope {
                suspend fun runPolicyRequests(idx: Int, jwt: String, policies: List<PolicyRequest>) =
                    runPolicyRequests(jwt, policies, presentationContext, onSuccess = { policyResult ->
                        resultMutex.withLock {
                            policiesRun++
                            results[idx].policyResults.add(policyResult)
                        }
                    }, onError = { policyResult, exception ->
                        resultMutex.withLock {
                            policiesRun++
                            results[idx].policyResults.add(policyResult)
                        }
                    })

                /* VP Policies */
                results.add(PresentationResultEntry(vpToken))
                runPolicyRequests(0, vpToken, vpPolicies)

                // VCs
                if(globalVcPolicies.size > 0 || specificCredentialPolicies.containsKey(vpType)) {
                    results.add(PresentationResultEntry(vpType))

                    /* Global VC Policies */
                    runPolicyRequests(1, vpToken, globalVcPolicies)

                    /* Specific Credential Policies */
                    specificCredentialPolicies[vpType]?.let { specificPolicyRequests ->
                        runPolicyRequests(1, vpToken, specificPolicyRequests)
                    }
                }
            }
        }

        return PresentationVerificationResponse(results, time, policiesRun)
    }

    private val EMPTY_MAP = emptyMap<String, Any>()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @Suppress("UNCHECKED_CAST" /* as? */)
    suspend fun verifyJws(jwt: String): Result<JsonObject> = JwtSignaturePolicy().verify(jwt, null, EMPTY_MAP) as? Result<JsonObject>
        ?: Result.failure(IllegalArgumentException("Could not get JSONObject from VC verification"))


}
