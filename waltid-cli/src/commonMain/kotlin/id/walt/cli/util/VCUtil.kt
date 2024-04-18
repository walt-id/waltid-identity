package id.walt.cli.util

import id.walt.credentials.issuance.Issuer.mergingJwtIssue
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.credentials.verification.PolicyManager
import id.walt.credentials.verification.Verifier
import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyResult
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class VCUtil {

    companion object {

        init {
            runBlocking {
                // TODO: What exactly is needed?
                // DidService.apply {
                //     registerResolver(LocalResolver())
                //     updateResolversForMethods()
                //     registerRegistrar(LocalRegistrar())
                //     updateRegistrarsForMethods()
                // }
                DidService.minimalInit()
            }
        }

        suspend fun sign(key: JWKKey, issuerDid: String, subjectDid: String, payload: String): String {

            val vcAsMap = Json.decodeFromString<Map<String, JsonElement>>(payload)
            val vc = W3CVC(vcAsMap)
            val jws =
                vc.mergingJwtIssue(
                    issuerKey = key,
                    issuerDid = issuerDid,
                    subjectDid = subjectDid,
                    mappings = JsonObject(emptyMap<String, JsonElement>()),
                    additionalJwtHeader = emptyMap<String, String>(),
                    additionalJwtOptions = emptyMap<String, JsonObject>()
                )
            // .w3cVc.signJws(issuerKey = key, issuerDid = issuerDid, subjectDid = subjectDid)

            return jws
        }

        suspend fun verify(
            jws: String,
            policies: List<String>,
            args: Map<String, JsonElement> = emptyMap()
        ): List<PolicyResult> {

            val policies = policies.ifEmpty { listOf("signature") }

            var requests: List<PolicyRequest> = mutableListOf()

            policies.forEach { policy ->
                val verificationPolicy = PolicyManager.getPolicy(policy)
                requests += PolicyRequest(policy = verificationPolicy, args = args[policy])
            }

            try {
                return Verifier.verifyCredential(jws, requests) // jws.decodeJws().payload.toString(), requests
            } catch (e: IllegalStateException) {
                println("Something went wrong.")
                return emptyList<PolicyResult>()
            }
        }
    }
}