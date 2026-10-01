package id.walt.openid4vci.proofs.attestation

import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.VerifiedCredentialBindingCandidate
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Library convention: an attested JWK may reference its holder's DID verification method in kid. */
internal suspend fun VerifiedKeyAttestation.resolveBindings(
    configuration: CredentialConfiguration,
): List<VerifiedCredentialBindingCandidate> = attestedKeys.mapIndexed { index, key ->
    // Read the signed JWK: restoring a crypto key need not preserve its original kid.
    val jwk = payload.getValue("attested_keys").jsonArray[index].jsonObject
    val kid = jwk[JwtHeaderParams.KEY_ID]?.let {
        (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
            ?: throw invalidCredentialProof("Attested JWK kid must be a string")
    }
    if (kid == null || !kid.startsWith("did:")) return@mapIndexed VerifiedCredentialBindingCandidate(key)
    if (!DidUtils.isDidUrl(kid)) throw invalidCredentialProof("Attested JWK kid must be a valid DID URL")

    val did = kid.substringBefore('#').substringBefore('?').substringBefore('/')
    val methodName = DidUtils.methodFromDid(did)
    if (!DidUtils.isDidUrl(did) || !methodName.matches(Regex("[a-z0-9]+"))) {
        throw invalidCredentialProof("Attested JWK kid must be a valid DID URL")
    }
    val method = CryptographicBindingMethod.Did(methodName)
    if (configuration.cryptographicBindingMethodsSupported?.contains(method) == false) {
        throw invalidCredentialProof("Attested key DID method is not supported by this credential configuration: ${method.value}")
    }
    val resolvedKeys = try {
        Crypto2DidService.resolveToKeys(did).getOrThrow()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw invalidCredentialProof("Could not resolve attested key DID", e)
    }
    val referencedKey = resolvedKeys.singleOrNull {
        val methodId = it.id.value
        (if (methodId.startsWith('#')) did + methodId else methodId) == kid
    } ?: throw invalidCredentialProof("Attested JWK kid must identify one DID verification method")
    if (Jwk.sha256Thumbprint(referencedKey.exportPublicJwk()) != Jwk.sha256Thumbprint(key.exportPublicJwk())) {
        throw invalidCredentialProof("Attested JWK does not match its DID verification method")
    }
    VerifiedCredentialBindingCandidate(key, kid, did)
}
