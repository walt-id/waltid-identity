package id.walt.credentials.verification

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.credentials.verification.models.PolicyResult
import id.walt.credentials.verification.models.PresentationResultEntry
import id.walt.credentials.verification.models.PresentationVerificationResponse
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.time.measureTime

object PolicyRunner {

    private fun JsonObject.getW3CType() =
        (this["type"] ?: this["vc"]?.jsonObject?.get("type") ?: this["vp"]?.jsonObject?.get("type")
        ?: throw IllegalArgumentException("No `type` supplied: $this")).let {
            when (it) {
                is JsonArray -> (it.lastOrNull()
                    ?: throw IllegalArgumentException("Empty `type` array! Please provide an type in the list."))
                    .jsonPrimitive.content

                is JsonPrimitive -> it.content
                else -> throw IllegalArgumentException("Invalid type of `type`-attribute: ${it::class.simpleName}")

            }
        }


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


    suspend fun verifyPresentation(
        vpTokenJwt: String,
        vpPolicies: List<PolicyRequest>,
        globalVcPolicies: List<PolicyRequest>,
        specificCredentialPolicies: Map<String, List<PolicyRequest>>,
        presentationContext: Map<String, Any>
    ): PresentationVerificationResponse {
        val providedJws = vpTokenJwt.decodeJws() // usually VP
        val payload = providedJws.payload
        val vpType = payload.getW3CType()

        val verifiableCredentialJwts =
            (payload["vp"]?.jsonObject?.get("verifiableCredential") ?: payload["verifiableCredential"]
            ?: TODO("Provided data does not have `verifiableCredential` array."))
                .jsonArray
                .map { it.jsonPrimitive.content }


        val results = ArrayList<PresentationResultEntry>()

        val resultMutex = Mutex()
        var policiesRun = 0

        val time = measureTime {
            coroutineScope {
                suspend fun runPolicyRequests(
                    resultIdx: Int,
                    policyRequests: List<PolicyRequest>,
                    jwt: String,
                    context: Map<String, Any> = emptyMap()
                ) {
                    policyRequests.forEach { policyRequest ->
                        launch {
                            runCatching {
                                val dataForPolicy: JsonElement = when (policyRequest.policy) {
                                    is JwtVerificationPolicy -> JsonPrimitive(jwt)

                                    is CredentialDataValidatorPolicy, is CredentialWrapperValidatorPolicy -> jwt.decodeJws().payload

                                    else -> throw IllegalArgumentException("Unsupported policy type: ${policyRequest.policy::class.simpleName}")
                                }
                                val runResult = policyRequest.runPolicyRequest(dataForPolicy, context)
                                val policyResult = PolicyResult(policyRequest, runResult)
                                resultMutex.withLock {
                                    policiesRun++
                                    results[resultIdx].policyResults.add(policyResult)
                                }
                            }.onFailure {
                                println("Error executing policy: ${policyRequest.policy.name}")
                                it.printStackTrace()
                                resultMutex.withLock {
                                    results[resultIdx].policyResults.add(PolicyResult(policyRequest, Result.failure(it)))
                                }
                            }
                        }
                    }
                }

                fun addResultEntryFor(type: String): Int {
                    results.add(PresentationResultEntry(type))
                    return results.size - 1
                }

                /* VP Policies */
                val vpIdx = addResultEntryFor(vpType)
                runPolicyRequests(vpIdx, vpPolicies, vpTokenJwt, presentationContext)

                // VCs
                verifiableCredentialJwts.forEach { credentialJwt ->
                    val credentialType = credentialJwt.decodeJws().payload.getW3CType()
                    val vcIdx = addResultEntryFor(credentialType)

                    /* Global VC Policies */
                    runPolicyRequests(vcIdx, globalVcPolicies, credentialJwt)

                    /* Specific Credential Policies */
                    specificCredentialPolicies[credentialType]?.let { specificPolicyRequests ->
                        runPolicyRequests(vcIdx, specificPolicyRequests, credentialJwt)
                    }
                }

            }
        }

        return PresentationVerificationResponse(results, time, policiesRun)
    }
}

suspend fun main() {
    DidService.apply {
        registerResolver(LocalResolver())
        updateResolversForMethods()
    }

    val vpToken =
        "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDprZXk6ejZNa3BkQ3FUNWZIWlZBYmRRdjJTS1dGTWdyTHN2UmFZdVRmSnpDdkJ4TG4xWHBzI3o2TWtwZENxVDVmSFpWQWJkUXYyU0tXRk1nckxzdlJhWXVUZkp6Q3ZCeExuMVhwcyJ9.eyJzdWIiOiJkaWQ6a2V5Ono2TWtwZENxVDVmSFpWQWJkUXYyU0tXRk1nckxzdlJhWXVUZkp6Q3ZCeExuMVhwcyIsIm5iZiI6MTY5Njc2MTcxOSwiaWF0IjoxNjk2NzYxNzc5LCJqdGkiOiJ1cm46dXVpZDpmMjM2ODMxNy03MjhjLTRhMWQtYWMyNC1kMTI4OTI2N2M5N2MiLCJpc3MiOiJkaWQ6a2V5Ono2TWtwZENxVDVmSFpWQWJkUXYyU0tXRk1nckxzdlJhWXVUZkp6Q3ZCeExuMVhwcyIsIm5vbmNlIjoiIiwidnAiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlUHJlc2VudGF0aW9uIl0sImlkIjoidXJuOnV1aWQ6ZDFhZGUxMTMtMTU2ZC00MDk4LWI4NmItZTQyMmY0ZDQ3MTE3IiwiaG9sZGVyIjoiZGlkOmtleTp6Nk1rcGRDcVQ1ZkhaVkFiZFF2MlNLV0ZNZ3JMc3ZSYVl1VGZKekN2QnhMbjFYcHMiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpoYkdjaU9pSkZaRVJUUVNJc0ltdHBaQ0k2SW1ScFpEcHJaWGs2ZWpaTmEzQlNZMjlVUmpJMFMxZHJlRzlTYUdSWmNFTm9VSFpCTkVNM2FuQkdaelZ4ZDI4eldqSXlaM05pTVdoeUluMC5leUp6ZFdJaU9pSmthV1E2YTJWNU9ubzJUV3R3WkVOeFZEVm1TRnBXUVdKa1VYWXlVMHRYUmsxbmNreHpkbEpoV1hWVVprcDZRM1pDZUV4dU1WaHdjeU42TmsxcmNHUkRjVlExWmtoYVZrRmlaRkYyTWxOTFYwWk5aM0pNYzNaU1lWbDFWR1pLZWtOMlFuaE1iakZZY0hNaUxDSnBjM01pT2lKa2FXUTZhMlY1T25vMlRXdHdVbU52VkVZeU5FdFhhM2h2VW1oa1dYQkRhRkIyUVRSRE4ycHdSbWMxY1hkdk0xb3lNbWR6WWpGb2NpSXNJblpqSWpwN0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5d2RYSnNMbWx0YzJkc2IySmhiQzV2Y21jdmMzQmxZeTl2WWk5Mk0zQXdMMk52Ym5SbGVIUXVhbk52YmlKZExDSnBaQ0k2SW5WeWJqcDFkV2xrT2psaFlXTmtPREZtTFRjM1lUTXRORGRoWWkxaU1tSXlMVGswTlRFd01qazFOVFl5TXlJc0luUjVjR1VpT2xzaVZtVnlhV1pwWVdKc1pVTnlaV1JsYm5ScFlXd2lMQ0pQY0dWdVFtRmtaMlZEY21Wa1pXNTBhV0ZzSWwwc0ltNWhiV1VpT2lKS1JrWWdlQ0IyWXkxbFpIVWdVR3gxWjBabGMzUWdNeUJKYm5SbGNtOXdaWEpoWW1sc2FYUjVJaXdpYVhOemRXVnlJanA3SW5SNWNHVWlPbHNpVUhKdlptbHNaU0pkTENKcFpDSTZJbVJwWkRwclpYazZlalpOYTNCU1kyOVVSakkwUzFkcmVHOVNhR1JaY0VOb1VIWkJORU0zYW5CR1p6VnhkMjh6V2pJeVozTmlNV2h5SWl3aWJtRnRaU0k2SWtwdlluTWdabTl5SUhSb1pTQkdkWFIxY21VZ0tFcEdSaWtpTENKMWNtd2lPaUpvZEhSd2N6b3ZMM2QzZHk1cVptWXViM0puTHlJc0ltbHRZV2RsSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB4TFRJd01qSXZhVzFoWjJWekwwcEdSbDlNYjJkdlRHOWphM1Z3TG5CdVp5SjlMQ0pwYzNOMVlXNWpaVVJoZEdVaU9pSXlNREl6TFRFd0xUQTBWREl6T2pVM09qQTVMalExTURNeU1qVXdNbG9pTENKamNtVmtaVzUwYVdGc1UzVmlhbVZqZENJNmV5SjBlWEJsSWpwYklrRmphR2xsZG1WdFpXNTBVM1ZpYW1WamRDSmRMQ0pwWkNJNkltUnBaRHByWlhrNmVqWk5hM0JrUTNGVU5XWklXbFpCWW1SUmRqSlRTMWRHVFdkeVRITjJVbUZaZFZSbVNucERka0o0VEc0eFdIQnpJM28yVFd0d1pFTnhWRFZtU0ZwV1FXSmtVWFl5VTB0WFJrMW5ja3h6ZGxKaFdYVlVaa3A2UTNaQ2VFeHVNVmh3Y3lJc0ltRmphR2xsZG1WdFpXNTBJanA3SW1sa0lqb2lkWEp1T25WMWFXUTZZV015TlRSaVpEVXRPR1poWkMwMFltSXhMVGxrTWprdFpXWmtPVE00TlRNMk9USTJJaXdpZEhsd1pTSTZXeUpCWTJocFpYWmxiV1Z1ZENKZExDSnVZVzFsSWpvaVNrWkdJSGdnZG1NdFpXUjFJRkJzZFdkR1pYTjBJRE1nU1c1MFpYSnZjR1Z5WVdKcGJHbDBlU0lzSW1SbGMyTnlhWEIwYVc5dUlqb2lWR2hwY3lCM1lXeHNaWFFnYzNWd2NHOXlkSE1nZEdobElIVnpaU0J2WmlCWE0wTWdWbVZ5YVdacFlXSnNaU0JEY21Wa1pXNTBhV0ZzY3lCaGJtUWdhR0Z6SUdSbGJXOXVjM1J5WVhSbFpDQnBiblJsY205d1pYSmhZbWxzYVhSNUlHUjFjbWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhKbGNYVmxjM1FnZDI5eWEyWnNiM2NnWkhWeWFXNW5JRXBHUmlCNElGWkRMVVZFVlNCUWJIVm5SbVZ6ZENBekxpSXNJbU55YVhSbGNtbGhJanA3SW5SNWNHVWlPaUpEY21sMFpYSnBZU0lzSW01aGNuSmhkR2wyWlNJNklsZGhiR3hsZENCemIyeDFkR2x2Ym5NZ2NISnZkbWxrWlhKeklHVmhjbTVsWkNCMGFHbHpJR0poWkdkbElHSjVJR1JsYlc5dWMzUnlZWFJwYm1jZ2FXNTBaWEp2Y0dWeVlXSnBiR2wwZVNCa2RYSnBibWNnZEdobElIQnlaWE5sYm5SaGRHbHZiaUJ5WlhGMVpYTjBJSGR2Y210bWJHOTNMaUJVYUdseklHbHVZMngxWkdWeklITjFZMk5sYzNObWRXeHNlU0J5WldObGFYWnBibWNnWVNCd2NtVnpaVzUwWVhScGIyNGdjbVZ4ZFdWemRDd2dZV3hzYjNkcGJtY2dkR2hsSUdodmJHUmxjaUIwYnlCelpXeGxZM1FnWVhRZ2JHVmhjM1FnZEhkdklIUjVjR1Z6SUc5bUlIWmxjbWxtYVdGaWJHVWdZM0psWkdWdWRHbGhiSE1nZEc4Z1kzSmxZWFJsSUdFZ2RtVnlhV1pwWVdKc1pTQndjbVZ6Wlc1MFlYUnBiMjRzSUhKbGRIVnlibWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhSdklIUm9aU0J5WlhGMVpYTjBiM0lzSUdGdVpDQndZWE56YVc1bklIWmxjbWxtYVdOaGRHbHZiaUJ2WmlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUdGdVpDQjBhR1VnYVc1amJIVmtaV1FnWTNKbFpHVnVkR2xoYkhNdUluMHNJbWx0WVdkbElqcDdJbWxrSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB6TFRJd01qTXZhVzFoWjJWekwwcEdSaTFXUXkxRlJGVXRVRXhWUjBaRlUxUXpMV0poWkdkbExXbHRZV2RsTG5CdVp5SXNJblI1Y0dVaU9pSkpiV0ZuWlNKOWZYMTlmUS54Qkg1b0dwZm9xdFpXMTdhMEtlak1tUkUtNDhsMWt6bzExc2lrZUxkR0JoMHFMQ3E5d2pJeUZHeWxVMUxoM0FHaWN1VGRLdDB0bkJqRXhud29ZMmRCZyJdfX0.-29z2twNHmK3tIwS59R-WiHuOhBNJbUS5YXKPCbCaKhPa8QyD1Z8hZ-G6ECFY8K4ZSnoB5b7OCyvvIclYj8gAA"


    //language=json
    val vpPolicies = Json.parseToJsonElement(
        """
        [
          "signature",
          "expired",
          "not-before"
        ]
    """
    ).jsonArray.parsePolicyRequests()

    //language=json
    val vcPolicies = Json.parseToJsonElement(
        """
       [
          "signature",
          "expired",
          "not-before"
        ] 
    """
    ).jsonArray.parsePolicyRequests()

    //language=json
    val specificPolicies = Json.parseToJsonElement(
        """
       {
          "OpenBadgeCredential": [
              {
                "policy": "schema",
                "args": {
                    "type": "object",
                    "required": ["issuer"],
                    "properties": {
                        "issuer": {
                            "type": "object"
                        }
                    }
                }
            }
          ]
       } 
    """
    ).jsonObject.mapValues { it.value.jsonArray.parsePolicyRequests() }

    println("VP Policies: $vpPolicies")
    println("VC Policies: $vcPolicies")
    println("SP Policies: $specificPolicies")

    val r = PolicyRunner.verifyPresentation(
        vpTokenJwt = vpToken,
        vpPolicies = vpPolicies,
        globalVcPolicies = vcPolicies,
        specificCredentialPolicies = specificPolicies,
        mapOf(
            "presentationSubmission" to JsonObject(emptyMap()),
            "challenge" to "abc"
        )
    )

    println(
        Json { prettyPrint = true }.encodeToString(
            r.toJson()
        )
    )
}
