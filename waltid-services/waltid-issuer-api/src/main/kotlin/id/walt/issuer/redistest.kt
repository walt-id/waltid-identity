package id.walt.issuer

/*
import id.walt.issuer.issuance2.NewExamples
import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisStringCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID

fun main() {
    val client = RedisClient.create("redis://localhost")
    val connection = client.connect()
    val sync: RedisStringCommands<String, String> = connection.sync()

    val sessionId = UUID.generateUUID()

    sync.setex("session:$sessionId", 100, Json.encodeToString(NewExamples.newIssuanceRequestExample))

}
*/
