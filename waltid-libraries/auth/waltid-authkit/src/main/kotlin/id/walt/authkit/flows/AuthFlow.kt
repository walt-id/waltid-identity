package id.walt.authkit.flows

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.intellij.lang.annotations.Language

private operator fun String.times(index: Int): String {
    val sb = StringBuilder()
    repeat(index) { sb.append(this) }
    return sb.toString()
}

@Serializable
data class AuthFlow(
    val method: String,
    val config: JsonObject? = null,

    @SerialName("continue")
    val continueWith: AuthFlow? = null,
    val ok: Boolean = false,
) {

    init {
        check(ok || continueWith != null) { "No end condition in auth flow with method $method" }
        check(ok xor (continueWith != null)) { "Multiple end conditions in auth flow with method $method" }
    }

    override fun toString(): String = toString(0)

    fun toString(index: Int): String {
        val prefix = " " * index * 2

        val config = if (config != null) "\n${prefix}config=$config" else ""
        val end = when {
            ok -> "$prefix-> Flow end (success)"
            continueWith != null -> "continue on success ->\n${continueWith.toString(index + 1)}"
            else -> "$prefix?"
        }
        return "${prefix}Method: $method$config\n$end"

    }

}

@Language("json")
val flowConfig = """
{
  "mid": {
    "method": "userpass",
    "continue": {
      "method": "totp",
      "ok": true
    }
  },
  
  "high": {
    "method": "vc",
    "config": {
      "request_credentials": [
        "HighAssuranceCredential"
      ]
    },
    "ok": true
  },
  
  "external": {
    "method": "ldap",
    "config": {
      "server_url": "ldap://entra.microsoft.com:3893",
      "user_dn_format": "cn=%s,ou=superheros,dc=glauth,dc=com"
    },
    "ok": true
  },
  
  "special-idp": {
    "method": "oidc",
    "config": {
      "provider_configuration": "https://provider.example.com/.well-known/openid-configuration",
      "client_id": "app123",
      "client_secret": "secret123"
    },
    "continue": {
      "method": "vc",
      "config": {
        "request_credentials": [
          "HighAssuranceCredential"
        ]
      },
      "ok": true
    }
  }
}
""".trimIndent()

fun main() {
    val flows = Json.decodeFromString<Map<String, AuthFlow>>(flowConfig)

    flows.forEach { (id, flow) ->
        println("== Flow: $id ==")

        println(flow)
        println()
    }
}
