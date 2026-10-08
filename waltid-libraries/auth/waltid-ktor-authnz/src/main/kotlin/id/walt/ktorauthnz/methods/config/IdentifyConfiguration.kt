package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.Identify
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Settings of the `identify` step, which finds the account first and then offers the ways it can log in.
 *
 * Which flows an identified user is offered, in order:
 * 1. [domains]: for an email address of one of these domains, its flows - e.g. everyone at `example.net` logs in
 *    with the company's identity provider, with or without an account here
 * 2. the account's own flows ([id.walt.ktorauthnz.methods.storeddata.IdentifyStoredData])
 * 3. [default], for accounts that have no flows of their own
 * 4. [unknown], for identifiers without an account; without it, they are answered 404
 */
@Serializable
data class IdentifyConfiguration(
    /** Settings per method id (e.g. `oidc`, `ldap`, `vc`), for steps of the offered flows that carry none. */
    val methods: Map<String, JsonObject> = emptyMap(),
    val domains: Map<String, Set<AuthFlow>> = emptyMap(),
    val default: Set<AuthFlow>? = null,
    val unknown: Set<AuthFlow>? = null,
) : AuthMethodConfiguration {
    override fun authMethod() = Identify
}
