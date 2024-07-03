import kotlinx.serialization.json.JsonObject

interface KeyRepresentation {
    fun jwk(): JsonObject
    fun thumbprint(): String
    fun pem(): String

    fun kid(): String?

    fun externalRepresentation(): ByteArray
}

interface Signing {
    fun signJws(plainText: ByteArray, headers: Map<String, String>): String

    fun signRaw(plainText: ByteArray): ByteArray
}

interface Verification {
    fun verifyJws(jws: String): Result<JsonObject>

    fun verifyRaw(signature: ByteArray, signedData: ByteArray): Result<ByteArray>
}