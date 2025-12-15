package id.walt.ktorauthnz.flows

import io.klogging.logger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.intellij.lang.annotations.Language
import kotlin.time.Duration

private operator fun String.times(index: Int): String {
    val sb = StringBuilder()
    repeat(index) { sb.append(this) }
    return sb.toString()
}

fun Set<AuthFlow>.methods() = map { it.method }

@Serializable
data class AuthFlow(
    val method: String,
    val config: JsonObject? = null,

    @SerialName("continue")
    val continueWith: Set<AuthFlow>? = null,
    val success: Boolean = false,
    @Deprecated("replaced with success")
    val ok: Boolean? = false,
    /** set how long this auth flow result will be valid for */
    val expiration: String? = null
) {

    companion object {
        private val log = logger("AuthFlow")

        fun fromConfig(config: String): AuthFlow = Json.decodeFromString<AuthFlow>(config)
    }

    @Suppress("DEPRECATION") // check for legacy Deprecated "ok"
    fun isEndConditionSuccess() = success || (ok ?: false)

    @Transient
    val parsedDuration = expiration?.let { Duration.parse(it) }

    init {
        @Suppress("DEPRECATION")
        if (ok != null) {
            val msg = "Your AuthFlow configuration contains deprecated end-condition \"ok\" - use \"success\" instead."
            runBlocking { log.warn { msg } }
            println(msg)
        }

        check(isEndConditionSuccess() || continueWith != null) { "No end condition in auth flow with method $method" }
        check(isEndConditionSuccess() xor (continueWith != null)) { "Multiple end conditions in auth flow with method $method: OK and ${continueWith!!.methods()}" }

        if (continueWith != null) {
            check(continueWith.isNotEmpty()) { "Next flow list (`continueWith`) is empty at method $method" }
            check(
                continueWith.methods().toSet().size == continueWith.size
            ) { "Duplicated method in same flow in next flow list (`continueWith`) at method $method" }
        }
    }

    override fun toString(): String = toString(0)

    fun toString(index: Int): String {
        val prefix = " " * index * 2

        //val config = if (config != null) "\n${prefix}config=$config" else ""
        val end = when {
            isEndConditionSuccess() -> "|$prefix-> Flow end (success)"
            continueWith != null -> "${prefix}continue on success ->\n${continueWith.joinToString("\n") { it.toString(index + 1) }}"
            else -> "|$prefix?"
        }
        return "|${prefix}Method: $method\n$end"
    }
}

@Language("json")
private val flowConfigExample = """
{
  "mid": {
    "method": "userpass",
    "continue": [{
      "method": "totp",
      "success": true
    }, {
      "method": "emailpass",
      "continue": [{
        "method": "totp",
        "success": true
      }]
    }]
  },
  
  "high": {
    "method": "vc",
    "config": {
      "request_credentials": [
        "HighAssuranceCredential"
      ]
    },
    "success": true
  },
  
  "external": {
    "method": "ldap",
    "config": {
      "server_url": "ldap://entra.microsoft.com:3893",
      "user_dn_format": "cn=%s,ou=superheros,dc=glauth,dc=com"
    },
    "success": true
  },
  
  "special-idp": {
    "method": "oidc",
    "config": {
      "provider_configuration": "https://provider.example.com/.well-known/openid-configuration",
      "client_id": "app123",
      "client_secret": "secret123"
    },
    "continue": [{
      "method": "vc",
      "config": {
        "request_credentials": [
          "HighAssuranceCredential"
        ]
      },
      "success": true
    }]
  }
}
""".trimIndent()
