package id.walt.credentials.issuance

import id.walt.core.crypto.keys.Key
import id.walt.core.crypto.keys.KeySerialization
import id.walt.core.crypto.utils.JwsUtils.decodeJws
import id.walt.credentials.issuance.Issuer.mergingIssue
import id.walt.credentials.utils.W3CDataMergeUtils
import id.walt.credentials.utils.W3CDataMergeUtils.mergeWithMapping
import id.walt.credentials.utils.W3CVcUtils.overwrite
import id.walt.credentials.utils.W3CVcUtils.update
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.resolver.LocalResolver
import id.walt.did.utils.randomUUID
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Issuer {

    /**
     * @param id: id
     */
    @Serializable
    data class ExtraData(
        val idLocation: String = "id",


        )

    /**
     * Manually set data and issue credential
     */
    suspend fun W3CVC.baseIssue(
        key: Key,
        did: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>
    ): String {
        val overwritten = overwrite(dataOverwrites)
        var updated = overwritten
        dataUpdates.forEach { (k, v) -> updated = updated.update(k, v) }

        return signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions
        )
    }


    val dataFunctions = mapOf<String, suspend (call: W3CDataMergeUtils.FunctionCall?) -> JsonElement>(
        "subjectDid" to { JsonPrimitive("xxSubjectDidxx") },

        "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
        "timestamp-seconds" to { JsonPrimitive(Clock.System.now().epochSeconds) },

        "timestamp-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it!!.args!!)).toString()) },
        "timestamp-in-seconds" to { JsonPrimitive((Clock.System.now() + Duration.parse(it!!.args!!)).epochSeconds) },

        "timestamp-before" to { JsonPrimitive((Clock.System.now() - Duration.parse(it!!.args!!)).toString()) },
        "timestamp-before-seconds" to { JsonPrimitive((Clock.System.now() - Duration.parse(it!!.args!!)).epochSeconds) },

        "uuid" to { JsonPrimitive("urn:uuid:${randomUUID()}") },
        "webhook" to { JsonPrimitive(HttpClient().get(it!!.args!!).bodyAsText()) },
        "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it!!.args!!).bodyAsText()) },

        "last" to {
            it!!.history[it.args!!] ?: throw IllegalArgumentException("No such function in history: ${it.args}")
        }
    )

    /**
     * Merge data with mappings and issue
     */
    suspend fun W3CVC.mergingIssue(
        key: Key,
        did: String,
        subject: String,

        mappings: JsonObject,

//
//        dataOverwrites: Map<String, JsonElement>,
//        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>,

        completeJwtWithDefaultCredentialData: Boolean = true
    ): String {

        /*val jwtMappings = mappings.filterKeys { it.startsWith("jwt:") }.mapKeys { it.key.removePrefix("jwt:") }
        println("JWT MAPPINGS: $jwtMappings")

        val dataMappings = JsonObject(mappings.filterKeys { !it.startsWith("jwt") })*/

        val mapped = this.mergeWithMapping(mappings, dataFunctions)

        val vc = mapped.vc
        val jwtRes = mapped.results.mapKeys { it.key.removePrefix("jwt:") }.toMutableMap()

        fun completeJwtAttributes(attribute: String, completer: () -> JsonElement?) {
            if (attribute !in jwtRes) {
                val completed = completer.invoke()

                if (completed != null) {
                    jwtRes[attribute] = completed
                }
            }
        }

        if (completeJwtWithDefaultCredentialData) {
            completeJwtAttributes("jti") { vc["id"] }
            completeJwtAttributes("exp") {
                vc["expirationDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("iat") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("nbf") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) - 90.seconds }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
        }

        return vc.signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeader.toMutableMap().apply {
                put("typ", "JWT")
            },
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtRes)
            }
        )
    }
}


suspend fun main() {
    DidService.apply {
        registerResolver(LocalResolver())
        registerRegistrar(LocalRegistrar())
        updateRegistrarsForMethods()
        updateResolversForMethods()
    }
    val issuerKey =
        KeySerialization.deserializeKey("""{"type":"local","jwk":"{\"kty\":\"OKP\",\"d\":\"mi_10iiMhRzWpc8S97W5mW3nW_Llv6FJWQreODqV6os\",\"crv\":\"Ed25519\",\"kid\":\"-sPnHUacW7L3lWc4t33UjMektLlyufzosu_GzNgb7v4\",\"x\":\"RKrOFFf5mR_Tva7Vbi_OgE5PoUYCS6sODxaLgSxkQ8U\"}"}""")
            .getOrThrow()
    val issuerDid = "did:key:z6Mkj5Jq5UaRznynC7wviUnMEekGry4vsggRuZbAb2BiCc1J"

    //val subjectKey =KeySerialization.deserializeKey("""{"type":"local","jwk":"{\"kty\":\"OKP\",\"d\":\"nL_6G-cpUi5PgQHBCE1hScxOBNFwXyCpueuRgoyl--M\",\"crv\":\"Ed25519\",\"kid\":\"_4zODEzU66fXCza1TiNLIePaaMusNFcnw7hl59n77gA\",\"x\":\"bbbI8_OdO92i4wZLNKTfy5QJ27cfVA13oAUKe3LVxZ0\"}"}""")
    val subjectDid = "did:key:z6MkmqY96sGNppYEtB2wwfi1HBD3cm9NuWpgxpWyhD1zWts6"

    //language=JSON
    val mappings = Json.parseToJsonElement(
        """
        {
          "id": "<uuid>",
          "credentialSubject": {"id": "<subjectDid>"},
          
          "issuanceDate": "<timestamp>",
          "expirationDate": "<timestamp-in:14d>"
        }
    """.trimIndent()
    ).jsonObject

    //language=JSON
    val vcStr = """
        {
            "@context": ["ctx"],
            "type": ["type"],
            "credentialSubject": {"name": "Muster", "image": {"url": "URL"}}
        }
    """.trimIndent()

    val vc = W3CVC.fromJson(vcStr)

    val jwt = vc.mergingIssue(issuerKey, issuerDid, subjectDid, mappings, emptyMap(), emptyMap())

    println("JWT: $jwt")

    jwt.decodeJws().apply {
        println("Header:  $header")
        println("Payload: $payload")
    }
}
