package id.walt.policies2.vc

import id.walt.policies2.vc.policies.JsonSchemaPolicy
import io.ktor.http.Url

internal actual suspend fun createRemoteJsonSchemaPolicy(schemaUrl: Url): JsonSchemaPolicy =
    JsonSchemaPolicy(schemaUrl = schemaUrl)
