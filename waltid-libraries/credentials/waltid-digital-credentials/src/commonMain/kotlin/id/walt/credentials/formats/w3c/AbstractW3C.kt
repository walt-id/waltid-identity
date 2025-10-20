package id.walt.credentials.formats

import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

@Serializable
sealed class AbstractW3C(
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

    companion object {
        private val log = KotlinLogging.logger { }
    }


    override suspend fun getIssuerKey(): Key? {
        val s = signature as JwtBasedSignature
        return s.getJwtBasedIssuer(credentialData)
    }
    /*
    override suspend fun getIssuerKey(): Key? {
        val issuer = issuer

        val possibleKeys: List<Result<Key>> = when {
            issuer != null && DidUtils.isDidUrl(issuer)
                -> DidService.resolveToKeys(issuer).map { p ->
                p.toList().map { Result.success(it) }
            }.getOrElse { listOf(Result.failure(it)) }

            signature is JwtBasedSignature && (signature as JwtBasedSignature).jwtHeader?.containsKey("x5c") == true -> {
                val x5c = (signature as JwtBasedSignature).jwtHeader!!["x5c"]!!.jsonArray
                log.trace { "Found x5c: $x5c" }

                // TODO: Proper certificate chain parsing
                *//*x5c.map { x5cElem ->
                    val pem = x5cToPemCertificate(x5cElem.jsonPrimitive.content)
                    log.trace { "Handling converted x5c element: $pem" }
                    JWKKey.importPEM(pem).mapCatching { importedPemKey ->
                        JWKKey(importedPemKey.exportJWK(), issuer)
                    }
                }*//*

                val pem = x5cToPemCertificate(x5c.first().jsonPrimitive.content)
                log.trace { "Handling converted x5c element: $pem" }
                listOf(JWKKey.importPEM(pem).mapCatching { importedPemKey ->
                    JWKKey(importedPemKey.exportJWK(), issuer)
                })

            }

            issuer != null && !DidUtils.isDidUrl(issuer) -> throw IllegalArgumentException("Issuer is not a DID: \"$issuer\"")
            else -> throw IllegalArgumentException("Cannot determine any public key for issuer: \"$issuer\" - this is supposed to be some kind of DID, x5c certificate, etc.")
        }

        return possibleKeys.map { it.getOrNull() }.firstOrNull()
    }*/

    init {
        selfCheck()
    }

    override suspend fun verify(publicKey: Key) =
        when (signature) {
            is JwtCredentialSignature, is SdJwtCredentialSignature -> {
                require(signed != null) { "Cannot verify unsigned credential" }
                publicKey.verifyJws(signed!!)
            }

            is CoseCredentialSignature -> TODO("Not implemented yet: verify W3C with COSE")
            is DataIntegrityProofCredentialSignature -> TODO("Not implemented yet: verify W3C with DIP")
            null -> throw IllegalArgumentException("Credential contains no signature, cannot verify")
        }
}

