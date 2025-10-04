package id.walt.cli.util

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.policies.PolicyManager
import id.walt.policies.Verifier
import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyResult
import id.walt.w3c.issuance.Issuer.mergingJwtIssue
import id.walt.w3c.vc.vcs.W3CVC
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
                    issuerId = issuerDid,
                    subjectDid = subjectDid,
                    mappings = JsonObject(emptyMap()),
                    additionalJwtHeader = emptyMap(),
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
            @Suppress("NAME_SHADOWING")
            val policies = policies.ifEmpty { listOf("signature") }

            val requests = ArrayList<PolicyRequest>()

            policies.forEach { policy ->
                val verificationPolicy = PolicyManager.getPolicy(policy)
                requests += PolicyRequest(policy = verificationPolicy, args = args[policy])
            }

            try {
                return Verifier.verifyCredential(jws, requests) // jws.decodeJws().payload.toString(), requests
            } catch (e: IllegalStateException) {
                println("Something went wrong.")
                return emptyList()
            }
        }
    }
}
