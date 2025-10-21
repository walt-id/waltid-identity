package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
@SerialName("signature")
class CredentialSignaturePolicy : VerificationPolicy2() {
    override val id = "signature"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonObject> {
        /*val issuer = credential.issuer

        val possibleKeys: List<Result<Key>> = when {
            issuer != null && DidUtils.isDidUrl(issuer)
                -> DidService.resolveToKeys(issuer).map { p ->
                p.toList().map { Result.success(it) }
            }.getOrElse { listOf(Result.failure(it)) }

            credential.signature is JwtBasedSignature && (credential.signature as JwtBasedSignature).jwtHeader?.containsKey("x5c") == true -> {
                val x5c = (credential.signature as JwtBasedSignature).jwtHeader!!["x5c"]!!.jsonArray
                log.trace { "Found x5c: $x5c" }

                 // TODO: Proper certificate chain parsing
                x5c.map { x5cElem ->
                    val pem = x5cToPemCertificate(x5cElem.jsonPrimitive.content)
                    log.trace { "Handling converted x5c element: $pem" }
                    JWKKey.importPEM(pem).mapCatching { importedPemKey ->
                        JWKKey(importedPemKey.exportJWK(), credential.issuer)
                    }
                }

            }

            issuer != null && !DidUtils.isDidUrl(issuer) -> throw IllegalArgumentException("Issuer is not a DID: \"$issuer\"")
            else -> throw IllegalArgumentException("Cannot determine any public key for issuer: \"$issuer\" - this is supposed to be some kind of DID, x5c certificate, etc.")
        }

        when {
            possibleKeys.isEmpty() -> return Result.failure(IllegalArgumentException("Failed to find a key to verify credential signature against, for credential: $credential"))
            possibleKeys.none { it.isSuccess } -> return Result.failure(IllegalArgumentException("All keys failed to parse, cannot verify credential signature, for credential: $credential, errors: ${possibleKeys.map { it.exceptionOrNull() }}"))
        }

        if (possibleKeys.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "Failed to find a key to verify credential signature against, for credential: $credential"
                )
            )
        }

        val keys = possibleKeys.filter { it.isSuccess }.map { it.getOrThrow() }*/

        // Use new generic getSignerKey method:
        val signerKey = credential.getSignerKey() ?: return Result.failure(
            IllegalArgumentException(
                "Failed to retrieve issuer key to verify credential signature against, for credential: $credential",
            )
        )

        val verificationResult = credential.verify(signerKey)

        if (verificationResult.isSuccess) {
            return Result.success(buildJsonObject {
                put("verification_result", JsonPrimitive(verificationResult.isSuccess))
                // Signed form that was verified
                put("signed_credential", JsonPrimitive(credential.signed))
                put("credential_signature", Json.encodeToJsonElement(credential.signature))
                put("verified_data", verificationResult.getOrNull() ?: JsonNull)
                put("successful_issuer_public_key", signerKey.exportJWKObject())
                put("successful_issuer_public_key_id", JsonPrimitive(signerKey.getKeyId()))

                /*
                if (failedVerificationResults.isNotEmpty()) {
                    val failedMap = failedVerificationResults.associate { (key, result) -> key.getKeyId() to result.message }
                    putJsonObject("previous_failed_verification_results") {
                        failedMap.forEach { (keyId, errorMessage) ->
                            put(keyId, JsonPrimitive(errorMessage))
                        }
                    }
                }*/
            })
        }

        /*
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
                }*/

        // All keys failed:
        return Result.failure(
            IllegalArgumentException(
                "Failed to verify credential signature, for credential: $credential",
                //failedVerificationResults.last().second
            )
        )
    }
}
