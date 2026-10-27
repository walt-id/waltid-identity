package id.walt.openid4vci.responses.challenge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttestationChallengeResponse(
    @SerialName("attestation_challenge") val attestationChallenge: String,
) {
    fun toHttp(): AttestationChallengeResponseHttp = AttestationChallengeResponseHttp(
        payload = buildJsonObject { put("attestation_challenge", attestationChallenge) },
    )
}

data class AttestationChallengeResponseHttp(
    val payload: JsonObject,
    val status: Int = 200,
    val headers: Map<String, String> = mapOf("Cache-Control" to "no-store"),
)
