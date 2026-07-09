package id.walt.policies2.vc

import id.walt.policies2.vc.policies.JsonSchemaPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

internal actual suspend fun createRemoteJsonSchemaPolicy(schemaUrl: Url): JsonSchemaPolicy =
    JsonSchemaPolicy(schema = fetchRemoteSchema(schemaUrl))

private suspend fun fetchRemoteSchema(schemaUrl: Url): JsonObject {
    val client = rawGithubTestClient()
    try {
        return Json.decodeFromString<JsonObject>(client.get(schemaUrl).bodyAsText())
    } finally {
        client.close()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun rawGithubTestClient(): HttpClient = HttpClient(Darwin) {
    engine {
        handleChallenge { _, _, challenge, completionHandler ->
            val protectionSpace = challenge.protectionSpace
            val serverTrust = protectionSpace.serverTrust

            if (
                protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust &&
                protectionSpace.host == "raw.githubusercontent.com" &&
                serverTrust != null
            ) {
                // Test-only workaround for simulator-side Darwin trust failures seen as NSURLErrorDomain -1202.
                completionHandler(
                    NSURLSessionAuthChallengeUseCredential,
                    NSURLCredential.credentialForTrust(serverTrust),
                )
            } else {
                completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            }
        }
    }
}
