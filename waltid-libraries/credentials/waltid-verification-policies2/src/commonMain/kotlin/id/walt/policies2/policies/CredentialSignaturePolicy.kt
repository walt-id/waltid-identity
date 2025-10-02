package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@SerialName("signature")
class CredentialSignaturePolicy : VerificationPolicy2() {
    override val id = "signature"

    override suspend fun verify(credential: DigitalCredential): Result<JsonObject> {
        val issuer = credential.issuer

        val keys = when {
            issuer != null && DidUtils.isDidUrl(issuer)
                -> DidService.resolveToKeys(issuer).getOrThrow()

            credential.signature is JwtBasedSignature && (credential.signature as JwtBasedSignature).jwtHeader?.containsKey("x5c") == true -> {
                val x5c = (credential.signature as JwtBasedSignature).jwtHeader!!["x5c"]!!.jsonArray
                x5c.map { x5cElem ->
                    JWKKey.importPEM(x5cElem.jsonPrimitive.content).getOrThrow()
                        .let { JWKKey(it.exportJWK(), credential.issuer) }
                }
            }

            issuer != null && !DidUtils.isDidUrl(issuer) -> throw IllegalArgumentException("Issuer is not a DID: \"$issuer\"")
            else -> throw IllegalArgumentException("Cannot determine any public key for issuer: \"$issuer\" - this is supposed to be some kind of DID, x5c certificate, etc.")
        }

        val failedVerificationResults = ArrayList<Pair<Key, Throwable>>()

        keys.forEach { issuerPublicKeyEntry ->
            val verificationResult = credential.verify(issuerPublicKeyEntry)

            if (verificationResult.isSuccess) {
                return Result.success(buildJsonObject {
                    put("verification_result", JsonPrimitive(verificationResult.isSuccess))
                    // Signed form that was verified
                    put("signed_credential", JsonPrimitive(credential.signed))
                    put("credential_signature", Json.encodeToJsonElement(credential.signature))
                    put("verified_data", verificationResult.getOrNull() ?: JsonNull)
                    put("successful_issuer_public_key", issuerPublicKeyEntry.exportJWKObject())
                    put("successful_issuer_public_key_id", JsonPrimitive(issuerPublicKeyEntry.getKeyId()))

                    if (failedVerificationResults.isNotEmpty()) {
                        val failedMap = failedVerificationResults.associate { (key, result) -> key.getKeyId() to result.message }
                        putJsonObject("previous_failed_verification_results") {
                            failedMap.forEach { (keyId, errorMessage) ->
                                put(keyId, JsonPrimitive(errorMessage))
                            }
                        }
                    }
                })
            }

            failedVerificationResults += (issuerPublicKeyEntry to verificationResult.exceptionOrNull()!!)
        }

        // All keys failed:
        return Result.failure(
            IllegalArgumentException(
                "Failed to verify credential signature, for credential: $credential",
                failedVerificationResults.last().second
            )
        )
    }
}
